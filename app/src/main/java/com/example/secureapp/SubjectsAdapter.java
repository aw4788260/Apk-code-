package com.example.secureapp;

import android.content.Intent;
import android.util.Log; // لاستخدام Log.d
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.secureapp.database.SubjectEntity;
import com.google.firebase.crashlytics.FirebaseCrashlytics; // لاستخدام Crashlytics Log
import java.util.ArrayList;
import java.util.List;

public class SubjectsAdapter extends RecyclerView.Adapter<SubjectsAdapter.ViewHolder> {
    private static final String TAG = "SubjectsAdapter";
    private List<SubjectEntity> subjects;

    public SubjectsAdapter(List<SubjectEntity> subjects) {
        this.subjects = subjects;
    }

    public SubjectsAdapter() {
        this.subjects = new ArrayList<>();
    }

    public void updateData(List<SubjectEntity> newSubjects) {
        this.subjects = newSubjects;
        notifyDataSetChanged();
    }

    // ✅✅ الدالة الإلزامية المفقودة (onCreateViewHolder)
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
        
        // ✅ منطق الاستجابة للنقرة والتنقل
        holder.itemView.setOnClickListener(v -> {
            // تسجيل الحدث في Logcat وفي Firebase
            Log.d(TAG, "Navigating to ChaptersActivity for Subject: " + subject.title + " ID: " + subject.id);
            FirebaseCrashlytics.getInstance().log("SUBJECT_CLICK: Starting Chapters for " + subject.title);

            android.content.Intent intent = new android.content.Intent(v.getContext(), ChaptersActivity.class);
            intent.putExtra("SUBJECT_ID", subject.id);
            intent.putExtra("SUBJECT_NAME", subject.title);
            v.getContext().startActivity(intent);
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
