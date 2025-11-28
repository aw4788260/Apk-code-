package com.example.secureapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.secureapp.database.SubjectEntity;
import java.util.ArrayList;
import java.util.List;

public class SubjectsAdapter extends RecyclerView.Adapter<SubjectsAdapter.ViewHolder> {
    private List<SubjectEntity> subjects;

    // ✅✅ تم إضافة الـ Constructor الناقص هنا
    public SubjectsAdapter(List<SubjectEntity> subjects) {
        this.subjects = subjects;
    }

    // Constructor فارغ احتياطي
    public SubjectsAdapter() {
        this.subjects = new ArrayList<>();
    }

    public void updateData(List<SubjectEntity> newSubjects) {
        this.subjects = newSubjects;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectEntity subject = subjects.get(position);
        holder.title.setText(subject.title);
        
        // عند الضغط على المادة (سيتم تفعيلها في الخطوة القادمة)
        holder.itemView.setOnClickListener(v -> {
            // Intent to ChaptersActivity (Coming Soon)
        });
    }

    @Override
    public int getItemCount() {
        return subjects != null ? subjects.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.subject_title);
        }
    }
}
