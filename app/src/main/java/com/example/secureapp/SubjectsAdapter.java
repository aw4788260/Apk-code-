package com.example.secureapp;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.secureapp.database.SubjectEntity;
import com.google.firebase.crashlytics.FirebaseCrashlytics; // ✅ للاستخدام
import java.util.ArrayList;
import java.util.List;

public class SubjectsAdapter extends RecyclerView.Adapter<SubjectsAdapter.ViewHolder> {
    private static final String TAG = "SubjectsAdapter";
    private List<SubjectEntity> subjects;

    public SubjectsAdapter(List<SubjectEntity> subjects) {
        this.subjects = subjects;
    }
    // ... (باقي الكود)

    public void updateData(List<SubjectEntity> newSubjects) {
        this.subjects = newSubjects;
        notifyDataSetChanged();
    }

    // ... (onCreateViewHolder)

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectEntity subject = subjects.get(position);
        holder.title.setText(subject.title);
        
        holder.itemView.setOnClickListener(v -> {
            // ✅ لوج للمتابعة الداخلية
            Log.d(TAG, "Navigating to ChaptersActivity for Subject: " + subject.title + " ID: " + subject.id);
            
            // ✅ تسجيل الحدث في تقرير Crashlytics
            FirebaseCrashlytics.getInstance().log("SUBJECT_CLICK: Starting Chapters for " + subject.title);

            android.content.Intent intent = new android.content.Intent(v.getContext(), ChaptersActivity.class);
            // ✅ التأكد من أن المفاتيح (Keys) صحيحة
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
