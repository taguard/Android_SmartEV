package com.moko.support.event;

import com.moko.support.entity.DeviceResponse;

public class SocketResponseEvent {

    private DeviceResponse response;

    public SocketResponseEvent(DeviceResponse response) {
        this.response = response;
    }

    public DeviceResponse getResponse() {
        return response;
    }
}
