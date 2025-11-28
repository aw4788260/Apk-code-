package com.example.secureapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.ChapterEntity;
import java.util.List;

public class ChaptersActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // أضف هذا السطر بعد super.onCreate وقبل setContentView
getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_chapters);

        int subjectId = getIntent().getIntExtra("SUBJECT_ID", -1);
        String subjectName = getIntent().getStringExtra("SUBJECT_NAME");

        TextView titleView = findViewById(R.id.subject_name_header);
        titleView.setText(subjectName);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        if (subjectId != -1) {
            List<ChapterEntity> chapters = db.chapterDao().getChaptersForSubject(subjectId);
            ChaptersAdapter adapter = new ChaptersAdapter(chapters);
            recyclerView.setAdapter(adapter);
        }
    }
}
