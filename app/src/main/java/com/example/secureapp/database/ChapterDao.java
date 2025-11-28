package com.example.secureapp.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE subject_id = :subjectId ORDER BY sortOrder ASC")
    List<ChapterEntity> getChaptersForSubject(int subjectId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChapterEntity> chapters);
    
    @Query("DELETE FROM chapters")
    void deleteAll();
}
