package com.moko.support.entity;


import android.text.TextUtils;

import java.io.Serializable;

public class MQTTConfig implements Serializable {
    public String host = "";
    public String port = "1883";
    public boolean cleanSession = true;
    public int connectMode;
    public int qos = 1;
    public int keepAlive = 60;
    public String clientId = "";
    public String deviceId = "";
    public String username = "";
    public String password = "";
    public String caPath;
    public String clientKeyPath;
    public String clientCertPath;
    public String topicSubscribe;
    public String topicPublish;
    public String ntpUrl;
    public int timeZone;

    public boolean isError() {
        return TextUtils.isEmpty(host)
                || TextUtils.isEmpty(port)
                || keepAlive == 0;
    }
}
