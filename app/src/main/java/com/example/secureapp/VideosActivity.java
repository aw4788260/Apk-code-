package com.example.secureapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.VideoEntity;
import java.util.List;

public class VideosActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AppDatabase db;
    
    // متغيرات لتخزين البيانات القادمة وتمريرها للمحول
    private int chapterId;
    private String chapterName;
    private String subjectName = "General"; // قيمة افتراضية لتجنب المشاكل

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videos);

        // استقبال البيانات
        chapterId = getIntent().getIntExtra("CHAPTER_ID", -1);
        chapterName = getIntent().getStringExtra("CHAPTER_NAME");
        
        // (اختياري) استقبال اسم المادة إذا كنت تمرره، وإلا سيبقى "General"
        if (getIntent().hasExtra("SUBJECT_NAME")) {
            subjectName = getIntent().getStringExtra("SUBJECT_NAME");
        }

        TextView titleView = findViewById(R.id.subject_name_header);
        if(titleView != null) titleView.setText(chapterName);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        if (chapterId != -1) {
            List<VideoEntity> videos = db.videoDao().getVideosForChapter(chapterId);
            
            // ✅✅ [التصحيح هنا] تمرير subjectName كمعامل ثالث
            VideosAdapter adapter = new VideosAdapter(this, videos, subjectName, chapterName);
            
            recyclerView.setAdapter(adapter);
        }
    }
}
