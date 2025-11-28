package com.example.secureapp;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.secureapp.database.ChapterEntity;
import java.util.List;

public class ChaptersAdapter extends RecyclerView.Adapter<ChaptersAdapter.ViewHolder> {
    private List<ChapterEntity> chapters;

    public ChaptersAdapter(List<ChapterEntity> chapters) {
        this.chapters = chapters;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chapter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChapterEntity chapter = chapters.get(position);
        holder.title.setText(chapter.title);
        
        holder.itemView.setOnClickListener(v -> {
            // الانتقال لصفحة الفيديوهات
            Intent intent = new Intent(v.getContext(), VideosActivity.class);
            intent.putExtra("CHAPTER_ID", chapter.id);
            intent.putExtra("CHAPTER_NAME", chapter.title);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return chapters != null ? chapters.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.chapter_title);
        }
    }
}
