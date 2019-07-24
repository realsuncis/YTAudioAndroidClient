package com.example.ytaudio;

import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MainActivity extends AppCompatActivity {

    Thread client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        client = new YTAudioClient();
        client.start();
    }

    public void onPlayButtonPressed(View view) //Pass over to thread
    {
        ((YTAudioClient)client).onPlayButtonPressed(view);
    }

}

class YTAudioClient extends Thread
{
    final static String ipAddress = "192.168.42.60";
    final static int port = 8080;
    final static String key = "123456789";
    static Thread connectionThread;

    @Override
    public void run()
    {
        try
        {

            InetAddress ip = InetAddress.getByName(ipAddress);

            Socket s = new Socket(ip, port);

            DataInputStream dis = new DataInputStream(s.getInputStream());
            DataOutputStream dos = new DataOutputStream(s.getOutputStream());

            //Send key to validate connection
            dos.writeUTF(key);
            System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            byte response = dis.readByte();
            if (response == 5)
            {
                dis.close();
                dos.close();
                this.interrupt();
            }

            connectionThread = new ConnectionHandler(dos, dis);
            connectionThread.start();



        }catch(Exception e){
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void onPlayButtonPressed(View view)
    {
        if(connectionThread != null)
        ((ConnectionHandler) connectionThread).addJob(new ConnectionJob(CONNECTION_OPCODE.START_STREAM, new Object[]{"dQw4w9WgXcQ"})); //TODO change to user input
    }

}

class ConnectionHandler extends Thread
{
    private volatile BlockingQueue<ConnectionJob> jobs = new ArrayBlockingQueue<ConnectionJob>(10);
    DataOutputStream dos;
    DataInputStream dis;

    ConnectionHandler(DataOutputStream dos, DataInputStream dis)
    {
        this.dos = dos;
        this.dis = dis;
    }

    @Override
    public void run() {
        Thread audioPlayer = new StreamAudioPlayer(this);
        audioPlayer.start();
        while (true) {
            try {
                ConnectionJob job;
                job = jobs.take();

                if(job.opcode == CONNECTION_OPCODE.START_STREAM) {
                    dos.writeByte(2); //START stream
                    dos.writeUTF((String)job.data[0]);
                    byte startResponse = dis.readByte();
                    if (startResponse == 4) //SUCCESS
                    {
                        int duration = dis.readInt();
                        System.out.println("Audio duration:" + duration);
                        ((StreamAudioPlayer)audioPlayer).initiateAudioPlayback((String)job.data[0], duration);
                    }
                    else //FAIL
                    {
                        System.out.println("Failed getting duration");
                        continue;
                    }
                }
                else if (job.opcode == CONNECTION_OPCODE.SEEK)
                {
                    dos.writeByte(3);
                    dos.writeUTF((String)job.data[0]);
                    dos.writeInt((int)job.data[1]);
                    dos.writeInt((int)job.data[2]);
                    byte response = dis.readByte();
                    if(response == 4) //SUCCESS
                    {
                        int fileCount = dis.readInt();
                        System.out.println("File count:" + fileCount);
                        for(int i = 0; i < fileCount; i++)
                        {
                            while(dis.available() < 4) Thread.sleep(1);
                            int byteCount = dis.readInt();
                            System.out.println("File" + i + " size: " + byteCount);
                            byte[] buffer = new byte[byteCount];
                            dis.read(buffer);
                            ((StreamAudioPlayer)audioPlayer).setAudioData((int)job.data[1] + i, buffer);
                        }

                    }
                }
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    synchronized void addJob(ConnectionJob job)
    {
        jobs.offer(job);
    }
}

class StreamAudioPlayer extends Thread
{
    private BlockingQueue<AudioJob> jobs = new ArrayBlockingQueue<AudioJob>(10);
    private Thread connectionThread;
    private MediaPlayer player;
    private volatile int currentTime = 0;
    private volatile ByteArrayMediaDataSource[] dataSources;
    boolean[] isSeekingArray;
    private String link;
    private int duration;

    StreamAudioPlayer(Thread connectionThread)
    {
        this.connectionThread = connectionThread;
        player = new MediaPlayer();
    }

    @Override
    public void run()
    {
        while(true)
        {
            AudioJob job;
            try {
                job = jobs.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
            if(job.opcode == AUDIO_OPCODE.PLAY_AUDIO)
            {
                AudioLoop: while(currentTime < duration)
                {
                    int currentTimeCached = currentTime;
                    if(!isDataAvailable(currentTime, 5))
                    {
                        if(!isSeekingInRange(currentTime, currentTime+10)) //Prevent data from being reseeked
                        {
                            seekData(currentTime, 10);
                        }

                    }

                    while(dataSources[currentTime] == null || player.isPlaying()) //Wait for data to seek if there is none
                    {
                        //Since the time has changed outside of this thread, player needs to recheck if audio data has been seeked at currentTime
                        if(currentTimeCached != currentTime) continue AudioLoop;
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    System.out.println(currentTimeCached);
                    player.reset();
                    player.setDataSource(dataSources[currentTime]);
                    try {
                        player.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                        continue;
                    }
                    player.start();
                    currentTime++;
                }
            }

        }
    }


    public void initiateAudioPlayback(String link, int duration)
    {
        this.link = link;
        this.duration = duration;
        dataSources = new ByteArrayMediaDataSource[duration];
        isSeekingArray = new boolean[duration];
        currentTime = 0;
        jobs.add(new AudioJob(AUDIO_OPCODE.PLAY_AUDIO, new Object[]{}));
    }

    private boolean isDataAvailable(int timeStamp, int seconds)
    {
        for(int i = timeStamp; i < timeStamp+seconds; i++)
        {
            if(i>=duration) return true;
            if (dataSources[i] == null) return false;
        }

        return true;
    }

    private boolean isSeekingInRange(int secondsFloor, int secondsCeiling)
    {
        for(int i = secondsFloor; i < secondsCeiling; i++)
        {
            if(i>=duration) return false;
            if(isSeekingArray[i] == true) return true;
        }

        return false;
    }

    private void seekData(int currentTime, int seconds)
    {
        for(int i = currentTime; i < currentTime+seconds; i++)
        {
            if(i>=duration) break;
            isSeekingArray[i] = true;
        }
        ((ConnectionHandler)connectionThread).addJob(new ConnectionJob(CONNECTION_OPCODE.SEEK, new Object[]{link, currentTime, seconds}));
    }

    public void setAudioData(int timeStamp, byte[] data)
    {
        dataSources[timeStamp] = new ByteArrayMediaDataSource(data);
    }

}
