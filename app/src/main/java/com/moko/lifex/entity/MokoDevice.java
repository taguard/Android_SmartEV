package com.moko.lifex.entity;


import java.io.Serializable;

public class MokoDevice implements Serializable {
    public int id;
    public String name;
    public String nickName;
    public String deviceId;
    public String uniqueId;
    public String type;
    public boolean on_off;
    public String company_name;
    public String production_date;
    public String product_model;
    public String firmware_version;
    public String topicPublish;
    public String topicSubscribe;
    public boolean isOnline;
    public boolean isOverload;
    public int overloadValue;
}
