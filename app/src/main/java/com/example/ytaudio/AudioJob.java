package com.example.ytaudio;

public class AudioJob
{
    AUDIO_OPCODE opcode;
    Object[] data;

    AudioJob(AUDIO_OPCODE opcode, Object[] data)
    {
        this.opcode = opcode;
        this.data = data;
    }
}

enum AUDIO_OPCODE {PLAY_AUDIO};
