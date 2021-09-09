package com.moko.support.entity;

import java.io.Serializable;

public class EnergyInfo implements Serializable {
    public int type;// daily:0,monthly,1
    public String hour;
    public String date;
    public String recordDate;
    public String value;
    public int energy;
}
