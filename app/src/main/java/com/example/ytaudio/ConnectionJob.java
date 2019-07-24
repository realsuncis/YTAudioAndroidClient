package com.example.ytaudio;

public class ConnectionJob
{
    CONNECTION_OPCODE opcode;
    Object[] data;

    ConnectionJob(CONNECTION_OPCODE opcode, Object[] data)
    {
        this.opcode = opcode;
        this.data = data;
    }
}

enum CONNECTION_OPCODE {START_STREAM, SEEK};
