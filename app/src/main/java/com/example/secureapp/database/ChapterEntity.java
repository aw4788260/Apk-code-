package com.example.secureapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@Entity(tableName = "chapters",
        foreignKeys = @ForeignKey(entity = SubjectEntity.class,
                                  parentColumns = "id",
                                  childColumns = "subject_id",
                                  onDelete = ForeignKey.CASCADE))
public class ChapterEntity {
    
    @PrimaryKey
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("sort_order")
    public int sortOrder;

    @ColumnInfo(name = "subject_id")
    public int subjectId;

    // ✅ لاستقبال الفيديوهات من السيرفر
    @Ignore
    @SerializedName("videos")
    public List<VideoEntity> videosList;

        @Ignore
    @SerializedName("pdfs") // ✅ الاسم مطابق لما يرسله السيرفر
    public List<PdfEntity> pdfsList;
}
