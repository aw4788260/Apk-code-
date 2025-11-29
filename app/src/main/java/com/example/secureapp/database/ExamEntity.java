package com.example.secureapp.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.ColumnInfo;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "exams",
        foreignKeys = @ForeignKey(entity = SubjectEntity.class,
                                  parentColumns = "id",
                                  childColumns = "subject_id",
                                  onDelete = ForeignKey.CASCADE))
public class ExamEntity {
    @PrimaryKey
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("duration_minutes")
    public int durationMinutes;

    @SerializedName("sort_order")
    public int sortOrder;

    @ColumnInfo(name = "subject_id")
    public int subjectId;

    // ✅✅ [جديد] حقول لتحديد حالة الامتحان والتوجيه
    @SerializedName("is_completed")
    public boolean isCompleted;

    @SerializedName("first_attempt_id")
    public Integer firstAttemptId; // نستخدم Integer لأنه قد يكون null
}
