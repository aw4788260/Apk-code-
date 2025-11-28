package com.example.secureapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "videos",
        foreignKeys = @ForeignKey(entity = ChapterEntity.class,
                                  parentColumns = "id",
                                  childColumns = "chapter_id",
                                  onDelete = ForeignKey.CASCADE))
public class VideoEntity {
    @PrimaryKey
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("sort_order")
    public int sortOrder;

    // اسم الحقل كما يأتي من الـ API لديك
    @SerializedName("youtube_video_id") 
    public String youtubeVideoId; 

    @ColumnInfo(name = "chapter_id")
    public int chapterId;
}
