package com.moko.support.entity;

import java.io.Serializable;

public class EnergyCurrentInfo implements Serializable {
    public String timestamp;
    public int all_energy;
    public int thirty_day_energy;
    public int today_energy;
    public int current_hour_energy;
    public int EC;
}
