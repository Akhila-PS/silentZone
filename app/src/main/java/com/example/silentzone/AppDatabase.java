package com.example.silentzone;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {SilentZone.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SilentZoneDao silentZoneDao();
}
