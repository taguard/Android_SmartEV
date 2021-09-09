package com.moko.support.event;

public class DeviceModifyNameEvent {

    private String deviceId;
    private String nickName;

    public DeviceModifyNameEvent(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
}
