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
        // تأكد من أنك قمت بتحديث ملف item_exam.xml ليحتوي على TextView بالمعرف exam_status
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exam, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExamEntity exam = exams.get(position);
        
        // 1. تعيين عنوان الامتحان
        holder.title.setText(exam.title);

        // 2. تعيين حالة الامتحان (جديد أم محلول)
        // يعتمد هذا على الحقول التي أضفناها في ExamEntity (isCompleted)
        if (exam.isCompleted) {
            holder.status.setText("تم الحل ✅");
            holder.status.setTextColor(Color.parseColor("#4CAF50")); // لون أخضر
        } else {
            holder.status.setText("جديد - ابدأ الآن 🆕");
            holder.status.setTextColor(Color.parseColor("#FFD700")); // لون ذهبي
        }
        
        // 3. معالجة النقر (التوجيه الذكي)
        holder.itemView.setOnClickListener(v -> {
            // جلب بيانات المستخدم والجهاز من التخزين المحلي
            String userId = context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE)
                                   .getString("TelegramUserId", "");
            
            // جلب بصمة الجهاز (مهمة جداً للتحقق الأمني في السيرفر)
            String deviceId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(), 
                    android.provider.Settings.Secure.ANDROID_ID
            );
            
            String baseUrl = "https://secured-bot.vercel.app";
            String targetUrl;

            // التحقق من الحالة لتحديد الوجهة
            if (exam.isCompleted && exam.firstAttemptId != null) {
                // إذا كان محلولاً -> توجيه لصفحة النتائج
                targetUrl = baseUrl + "/results/" + exam.firstAttemptId 
                          + "?userId=" + userId 
                          + "&deviceId=" + deviceId;
            } else {
                // إذا كان جديداً -> توجيه لصفحة بدء الامتحان
                targetUrl = baseUrl + "/exam/" + exam.id 
                          + "?userId=" + userId 
                          + "&deviceId=" + deviceId;
            }
            
            // فتح الرابط في النشاط المخصص (WebView)
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
        TextView status; // العنصر الجديد لعرض الحالة

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.exam_title);
            // تأكد من وجود TextView بهذا الـ ID في ملف item_exam.xml
            status = itemView.findViewById(R.id.exam_status); 
        }
    }
}
