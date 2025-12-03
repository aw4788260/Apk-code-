package com.example.secureapp.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {SubjectEntity.class, ChapterEntity.class, VideoEntity.class, ExamEntity.class, PdfEntity.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SubjectDao subjectDao();
    public abstract ChapterDao chapterDao();
    public abstract VideoDao videoDao();
    public abstract ExamDao examDao();
    public abstract PdfDao pdfDao(); // ✅ جديد
}
