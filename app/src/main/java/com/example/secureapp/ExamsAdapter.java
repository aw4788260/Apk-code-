package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.secureapp.database.ExamEntity;
import java.util.List;

public class ExamsAdapter extends RecyclerView.Adapter<ExamsAdapter.ViewHolder> {
    private List<ExamEntity> exams;
    private Context context;

    public ExamsAdapter(Context context, List<ExamEntity> exams) {
        this.context = context;
        this.exams = exams;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exam, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExamEntity exam = exams.get(position);
        
        // 1. تعيين عنوان الامتحان
        holder.title.setText(exam.title);

        // 2. تعيين حالة الامتحان
        if (exam.isCompleted) {
            holder.status.setText("تم الحل ✅");
            holder.status.setTextColor(Color.parseColor("#4CAF50")); // أخضر
        } else {
            holder.status.setText("جديد - ابدأ الآن 🆕");
            holder.status.setTextColor(Color.parseColor("#FFD700")); // ذهبي
        }
        
        // 3. معالجة النقر (روابط نظيفة بدون بيانات حساسة)
        holder.itemView.setOnClickListener(v -> {
            // الرابط الأساسي
            String baseUrl = RetrofitClient.BASE_URL.replaceAll("/$", "");
            String targetUrl;

            // التحقق من الحالة لتحديد الوجهة
            if (exam.isCompleted && exam.firstAttemptId != null) {
                // توجيه لصفحة النتائج (رابط نظيف)
                targetUrl = baseUrl + "/results/" + exam.firstAttemptId;
            } else {
                // توجيه لصفحة بدء الامتحان (رابط نظيف)
                targetUrl = baseUrl + "/exam/" + exam.id;
            }
            
            // فتح الرابط في WebViewActivity (الذي سيقوم بحقن الهوية تلقائياً)
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("URL", targetUrl);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { 
        return exams != null ? exams.size() : 0; 
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView status;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.exam_title);
            status = itemView.findViewById(R.id.exam_status); 
        }
    }
}
