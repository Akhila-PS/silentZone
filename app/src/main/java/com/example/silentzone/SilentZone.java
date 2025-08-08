package com.example.silentzone;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class SilentZone {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public double latitude;
    public double longitude;
    public String name;

    public SilentZone(double latitude, double longitude, String name) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
    }
}
