package com.example.secureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast; // ✅ لتنبيه المستخدم
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;
import android.util.Log;
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.ChapterEntity;
import com.example.secureapp.database.ExamEntity;
import com.google.firebase.crashlytics.FirebaseCrashlytics; // ✅ للاستخدام
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

        // استقبال البيانات
        subjectId = getIntent().getIntExtra("SUBJECT_ID", -1);
        subjectName = getIntent().getStringExtra("SUBJECT_NAME");
        
        // ✅ تسجيل البيانات المستقبلة
        FirebaseCrashlytics.getInstance().log("CHAPTER_LOAD: Subject ID received: " + subjectId);

        if (subjectId == -1) {
             Toast.makeText(this, "خطأ في تحميل المادة. ID غير صالح.", Toast.LENGTH_LONG).show();
             Log.e(TAG, "Error: SUBJECT_ID is -1, closing activity.");
             finish();
             return;
        }

        TextView titleView = findViewById(R.id.subject_name_header);
        titleView.setText(subjectName);
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnLectures = findViewById(R.id.btn_tab_lectures);
        btnExams = findViewById(R.id.btn_tab_exams);
        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        // تحميل الفصول بشكل افتراضي
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
            
            // ✅ لوج لنتائج قاعدة البيانات
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("CHAPTER_LOAD: Found " + (chapters != null ? chapters.size() : 0) + " chapters.");
            
            if (chapters == null || chapters.isEmpty()) {
                 Toast.makeText(this, "لا توجد فصول متاحة لهذه المادة.", Toast.LENGTH_SHORT).show();
                 recyclerView.setAdapter(null);
            } else {
                // ✅ تعديل: تمرير subjectName (اسم المادة) للمحول
                ChaptersAdapter adapter = new ChaptersAdapter(chapters, subjectName);
                recyclerView.setAdapter(adapter);
            }
        }
    }



    private void loadExams() {
        if (subjectId != -1) {
            List<ExamEntity> exams = db.examDao().getExamsForSubject(subjectId);
            
            // ✅ لوج لنتائج قاعدة البيانات
            FirebaseCrashlytics.getInstance().log("EXAM_LOAD: Found " + (exams != null ? exams.size() : 0) + " exams for display.");

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
        int activeColor = getColor(R.color.teal_200);
        int inactiveColor = Color.parseColor("#555555");

        btnLectures.setBackgroundColor(isLecturesActive ? activeColor : inactiveColor);
        btnLectures.setTextColor(isLecturesActive ? Color.BLACK : Color.WHITE);

        btnExams.setBackgroundColor(!isLecturesActive ? activeColor : inactiveColor);
        btnExams.setTextColor(!isLecturesActive ? Color.BLACK : Color.WHITE);
    }
}
