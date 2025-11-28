package com.example.secureapp.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ExamDao {
    @Query("SELECT * FROM exams WHERE subject_id = :subjectId ORDER BY sortOrder ASC")
    List<ExamEntity> getExamsForSubject(int subjectId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ExamEntity> exams);

    @Query("DELETE FROM exams")
    void deleteAll();
}
