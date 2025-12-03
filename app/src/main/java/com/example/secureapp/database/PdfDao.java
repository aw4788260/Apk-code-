package com.example.secureapp.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PdfDao {
    @Query("SELECT * FROM pdfs WHERE chapter_id = :chapterId ORDER BY sortOrder ASC")
    List<PdfEntity> getPdfsForChapter(int chapterId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PdfEntity> pdfs);

    @Query("DELETE FROM pdfs")
    void deleteAll();
}
