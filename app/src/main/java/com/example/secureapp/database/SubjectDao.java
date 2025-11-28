package com.example.secureapp.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC")
    List<SubjectEntity> getAllSubjects();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SubjectEntity> subjects);

    @Query("DELETE FROM subjects")
    void deleteAll();
}
