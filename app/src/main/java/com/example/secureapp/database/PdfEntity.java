package com.example.secureapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "pdfs",
        foreignKeys = @ForeignKey(entity = ChapterEntity.class,
                                  parentColumns = "id",
                                  childColumns = "chapter_id",
                                  onDelete = ForeignKey.CASCADE))
public class PdfEntity {
    @PrimaryKey
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("sort_order")
    public int sortOrder;

    @SerializedName("file_path") 
    public String filePath; 

    @ColumnInfo(name = "chapter_id")
    public int chapterId;
}
