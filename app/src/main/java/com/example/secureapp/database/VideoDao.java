package com.example.secureapp.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface VideoDao {
    @Query("SELECT * FROM videos WHERE chapter_id = :chapterId ORDER BY sortOrder ASC")
    List<VideoEntity> getVideosForChapter(int chapterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<VideoEntity> videos);
    
    @Query("DELETE FROM videos")
    void deleteAll();
}
