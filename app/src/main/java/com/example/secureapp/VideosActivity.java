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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videos);

        int chapterId = getIntent().getIntExtra("CHAPTER_ID", -1);
        String chapterName = getIntent().getStringExtra("CHAPTER_NAME");

        TextView titleView = findViewById(R.id.subject_name_header); // تأكد من وجود نفس الـ ID في XML
        if(titleView != null) titleView.setText(chapterName);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.chapters_recycler); // استخدمنا نفس الـ ID في XML لتوفير الوقت
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        if (chapterId != -1) {
            List<VideoEntity> videos = db.videoDao().getVideosForChapter(chapterId);
            VideosAdapter adapter = new VideosAdapter(this, videos, chapterName);
            recyclerView.setAdapter(adapter);
        }
    }
}
