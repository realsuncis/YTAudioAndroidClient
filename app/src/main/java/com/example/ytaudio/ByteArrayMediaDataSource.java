package com.example.ytaudio;
import android.annotation.TargetApi;
import android.media.MediaDataSource;
import android.os.Build;
import java.io.IOException;


@TargetApi(Build.VERSION_CODES.M)
public class ByteArrayMediaDataSource extends MediaDataSource {

    private final byte[] data;

    public ByteArrayMediaDataSource(byte []data) {
        this.data = data;
    }
    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if(position > data.length) return 0;
        if(position + size > data.length) size = data.length - (int)position;
        System.arraycopy(data, (int)position, buffer, offset, size);
        return size;
    }

    @Override
    public long getSize() throws IOException {
        return data.length;
    }

    @Override
    public void close() throws IOException {
        // Nothing to do here
    }
}
