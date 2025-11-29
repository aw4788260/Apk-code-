package com.example.secureapp;

import android.os.Bundle;
import android.view.WindowManager;
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
    private int chapterId;
    private String chapterName;
    private String subjectName = "General";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_videos);

        chapterId = getIntent().getIntExtra("CHAPTER_ID", -1);
        chapterName = getIntent().getStringExtra("CHAPTER_NAME");
        
        if (getIntent().hasExtra("SUBJECT_NAME")) {
            subjectName = getIntent().getStringExtra("SUBJECT_NAME");
        }

        TextView titleView = findViewById(R.id.subject_name_header);
        if(titleView != null) titleView.setText(chapterName);
        
        // ❌ تم حذف كود زر الرجوع (btn_back) من هنا

        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        if (chapterId != -1) {
            List<VideoEntity> videos = db.videoDao().getVideosForChapter(chapterId);
            VideosAdapter adapter = new VideosAdapter(this, videos, subjectName, chapterName);
            recyclerView.setAdapter(adapter);
        }
    }
}
