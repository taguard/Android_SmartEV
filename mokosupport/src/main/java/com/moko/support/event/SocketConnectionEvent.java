package com.moko.support.event;

public class SocketConnectionEvent {

    private int status;

    public SocketConnectionEvent(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
