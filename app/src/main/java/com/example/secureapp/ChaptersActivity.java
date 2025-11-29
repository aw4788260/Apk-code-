package com.example.secureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import android.util.Log;
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.ChapterEntity;
import com.example.secureapp.database.ExamEntity;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.List;

public class ChaptersActivity extends AppCompatActivity {
    private static final String TAG = "ChaptersActivity";
    private RecyclerView recyclerView;
    private AppDatabase db;
    private int subjectId;
    private String subjectName;
    private Button btnLectures, btnExams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_chapters);

        subjectId = getIntent().getIntExtra("SUBJECT_ID", -1);
        subjectName = getIntent().getStringExtra("SUBJECT_NAME");
        
        FirebaseCrashlytics.getInstance().log("CHAPTER_LOAD: Subject ID received: " + subjectId);

        if (subjectId == -1) {
             Toast.makeText(this, "خطأ في تحميل المادة. ID غير صالح.", Toast.LENGTH_LONG).show();
             finish();
             return;
        }

        TextView titleView = findViewById(R.id.subject_name_header);
        titleView.setText(subjectName);
        
        // ❌ تم حذف كود زر الرجوع (btn_back) من هنا

        btnLectures = findViewById(R.id.btn_tab_lectures);
        btnExams = findViewById(R.id.btn_tab_exams);
        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

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
            if (chapters == null || chapters.isEmpty()) {
                 Toast.makeText(this, "لا توجد فصول متاحة لهذه المادة.", Toast.LENGTH_SHORT).show();
                 recyclerView.setAdapter(null);
            } else {
                ChaptersAdapter adapter = new ChaptersAdapter(chapters, subjectName);
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private void loadExams() {
        if (subjectId != -1) {
            List<ExamEntity> exams = db.examDao().getExamsForSubject(subjectId);
            if (exams == null || exams.isEmpty()) {
                 Toast.makeText(this, "لا توجد امتحانات متاحة لهذه المادة.", Toast.LENGTH_SHORT).show();
                 recyclerView.setAdapter(null);
            } else {
                ExamsAdapter adapter = new ExamsAdapter(this, exams);
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private void updateTabsUI(boolean isLecturesActive) {
        // تغيير الخلفية بناءً على الاختيار
        if (isLecturesActive) {
            btnLectures.setBackgroundResource(R.drawable.tab_active);
            btnLectures.setTextColor(Color.BLACK);
            
            btnExams.setBackgroundResource(R.drawable.tab_inactive);
            btnExams.setTextColor(Color.WHITE);
        } else {
            btnLectures.setBackgroundResource(R.drawable.tab_inactive);
            btnLectures.setTextColor(Color.WHITE);
            
            btnExams.setBackgroundResource(R.drawable.tab_active);
            btnExams.setTextColor(Color.BLACK);
        }
    }
}
