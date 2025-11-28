package com.example.secureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager; // ✅ تم إضافة هذا السطر
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.ChapterEntity;
import com.example.secureapp.database.ExamEntity;
import java.util.List;

public class ChaptersActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private AppDatabase db;
    private int subjectId;
    private String subjectName;
    private Button btnLectures, btnExams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ✅ حماية الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_chapters);

        subjectId = getIntent().getIntExtra("SUBJECT_ID", -1);
        subjectName = getIntent().getStringExtra("SUBJECT_NAME");

        TextView titleView = findViewById(R.id.subject_name_header);
        titleView.setText(subjectName);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnLectures = findViewById(R.id.btn_tab_lectures);
        btnExams = findViewById(R.id.btn_tab_exams);
        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        // الافتراضي
        loadChapters();

        btnLectures.setOnClickListener(v -> {
            updateTabsUI(true);
            loadChapters();
        });

        btnExams.setOnClickListener(v -> {
            updateTabsUI(false);
            loadExams();
        });
    }

    private void loadChapters() {
        if (subjectId != -1) {
            List<ChapterEntity> chapters = db.chapterDao().getChaptersForSubject(subjectId);
            ChaptersAdapter adapter = new ChaptersAdapter(chapters);
            recyclerView.setAdapter(adapter);
        }
    }

    private void loadExams() {
        if (subjectId != -1) {
            List<ExamEntity> exams = db.examDao().getExamsForSubject(subjectId);
            ExamsAdapter adapter = new ExamsAdapter(this, exams);
            recyclerView.setAdapter(adapter);
        }
    }

    private void updateTabsUI(boolean isLecturesActive) {
        int activeColor = getColor(R.color.teal_200);
        int inactiveColor = Color.parseColor("#555555");

        btnLectures.setBackgroundColor(isLecturesActive ? activeColor : inactiveColor);
        btnLectures.setTextColor(isLecturesActive ? Color.BLACK : Color.WHITE);

        btnExams.setBackgroundColor(!isLecturesActive ? activeColor : inactiveColor);
        btnExams.setTextColor(!isLecturesActive ? Color.BLACK : Color.WHITE);
    }
}
