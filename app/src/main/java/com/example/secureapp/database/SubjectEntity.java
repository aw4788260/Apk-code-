package com.example.secureapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@Entity(tableName = "subjects")
public class SubjectEntity {
    
    @PrimaryKey
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("sort_order")
    public int sortOrder;

    // ✅ لاستقبال قائمة الفصول من السيرفر (لن يتم حفظها في جدول المواد، بل في جدول الفصول)
    @Ignore
    @SerializedName("chapters")
    public List<ChapterEntity> chaptersList;

    // ✅ لاستقبال قائمة الامتحانات من السيرفر (لن يتم حفظها في جدول المواد، بل في جدول الامتحانات)
    @Ignore
    @SerializedName("exams")
    public List<ExamEntity> examsList;
}
