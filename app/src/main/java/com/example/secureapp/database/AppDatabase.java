package com.example.secureapp.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {SubjectEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SubjectDao subjectDao();
}
