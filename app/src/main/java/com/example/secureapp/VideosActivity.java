package com.example.secureapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.VideoEntity;
import com.example.secureapp.database.PdfEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideosActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private TextView emptyView;
    private AppDatabase db;
    private int chapterId;
    private String chapterName;
    private String subjectName = "General";

    private Button tabVideos, tabFiles;
    private boolean isVideoTab = true;

    private List<ContentItem> allItems = new ArrayList<>();
    private ContentAdapter adapter;

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

        tabVideos = findViewById(R.id.tab_videos);
        tabFiles = findViewById(R.id.tab_files);
        recyclerView = findViewById(R.id.chapters_recycler);
        emptyView = findViewById(R.id.empty_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        if (chapterId != -1) {
            loadContent();
        }

        tabVideos.setOnClickListener(v -> switchTab(true));
        tabFiles.setOnClickListener(v -> switchTab(false));

        // تعيين الحالة الأولية
        switchTab(true); 
    }

    private void loadContent() {
        allItems.clear();
        List<VideoEntity> videos = db.videoDao().getVideosForChapter(chapterId);
        for (VideoEntity v : videos) allItems.add(new ContentItem(ContentItem.TYPE_VIDEO, v.id, v.title, v.sortOrder, v.youtubeVideoId));

        List<PdfEntity> pdfs = db.pdfDao().getPdfsForChapter(chapterId);
        for (PdfEntity p : pdfs) allItems.add(new ContentItem(ContentItem.TYPE_PDF, p.id, p.title, p.sortOrder, null));
        
        Collections.sort(allItems, (o1, o2) -> Integer.compare(o1.sortOrder, o2.sortOrder));
    }

    private void switchTab(boolean videos) {
        isVideoTab = videos;
        
        if (videos) {
            // الفيديو نشط: أزرق ونص أسود
            tabVideos.setBackgroundResource(R.drawable.tab_active);
            tabVideos.setTextColor(Color.BLACK);
            
            // الملفات غير نشط: رمادي ونص أبيض
            tabFiles.setBackgroundResource(R.drawable.tab_inactive);
            tabFiles.setTextColor(Color.WHITE);
        } else {
            // الملفات نشط: أزرق ونص أسود
            tabFiles.setBackgroundResource(R.drawable.tab_active);
            tabFiles.setTextColor(Color.BLACK);
            
            // الفيديو غير نشط: رمادي ونص أبيض
            tabVideos.setBackgroundResource(R.drawable.tab_inactive);
            tabVideos.setTextColor(Color.WHITE);
        }
        updateList();
    }

    private void updateList() {
        List<ContentItem> filteredList = new ArrayList<>();
        for (ContentItem item : allItems) {
            if (isVideoTab && item.type == ContentItem.TYPE_VIDEO) filteredList.add(item);
            else if (!isVideoTab && item.type == ContentItem.TYPE_PDF) filteredList.add(item);
        }

        if (filteredList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            if (isVideoTab) emptyView.setText("لا توجد فيديوهات");
            else emptyView.setText("لا توجد ملفات");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            adapter = new ContentAdapter(this, filteredList, subjectName, chapterName);
            recyclerView.setAdapter(adapter);
        }
    }
}
