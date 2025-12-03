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
import com.example.secureapp.database.PdfEntity; // ✅ استيراد كيان الـ PDF

import java.util.ArrayList;
import java.util.Collections;
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
        
        // حماية من لقطة الشاشة
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_videos);

        chapterId = getIntent().getIntExtra("CHAPTER_ID", -1);
        chapterName = getIntent().getStringExtra("CHAPTER_NAME");
        
        if (getIntent().hasExtra("SUBJECT_NAME")) {
            subjectName = getIntent().getStringExtra("SUBJECT_NAME");
        }

        TextView titleView = findViewById(R.id.subject_name_header);
        if(titleView != null) titleView.setText(chapterName);

        recyclerView = findViewById(R.id.chapters_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        if (chapterId != -1) {
            // 1. إنشاء قائمة موحدة للمحتوى
            List<ContentItem> contentList = new ArrayList<>();

            // 2. جلب الفيديوهات وإضافتها للقائمة
            List<VideoEntity> videos = db.videoDao().getVideosForChapter(chapterId);
            for (VideoEntity v : videos) {
                contentList.add(new ContentItem(
                    ContentItem.TYPE_VIDEO, 
                    v.id, 
                    v.title, 
                    v.sortOrder, 
                    v.youtubeVideoId
                ));
            }

            // 3. جلب ملفات PDF وإضافتها للقائمة ✅
            List<PdfEntity> pdfs = db.pdfDao().getPdfsForChapter(chapterId);
            for (PdfEntity p : pdfs) {
                contentList.add(new ContentItem(
                    ContentItem.TYPE_PDF, 
                    p.id, 
                    p.title, 
                    p.sortOrder, 
                    null // الـ PDF لا يحتاج extraData حالياً
                ));
            }

            // 4. ترتيب القائمة المدمجة حسب sortOrder لضمان الترتيب الصحيح
            Collections.sort(contentList, (o1, o2) -> Integer.compare(o1.sortOrder, o2.sortOrder));

            // 5. استخدام ContentAdapter الجديد الذي يدعم النوعين
            ContentAdapter adapter = new ContentAdapter(this, contentList, subjectName, chapterName);
            recyclerView.setAdapter(adapter);
        }
    }
}
