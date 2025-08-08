package com.example.silentzone;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SilentZoneDao {
    @Insert
    void insert(SilentZone zone);

    @Query("SELECT * FROM SilentZone")
    List<SilentZone> getAllZones();

    @Query("DELETE FROM SilentZone WHERE name = :name")
    void deleteByName(String name);

    @Query("DELETE FROM SilentZone")
    void deleteAll();
}
