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

    // ✅ هذا الحقل يستقبل الفصول من السيرفر ولا يُحفظ في جدول المواد
    @Ignore
    @SerializedName("chapters")
    public List<ChapterEntity> chaptersList;
}
