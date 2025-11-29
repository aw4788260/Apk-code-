package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color; // ✅ إضافة للألوان
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
        
        // 1. تعيين العنوان
        holder.title.setText(exam.title);

        // 2. ✅ تعيين الحالة (محلول أم جديد)
        if (exam.isCompleted) {
            holder.status.setText("تم الحل ✅");
            holder.status.setTextColor(Color.parseColor("#4CAF50")); // لون أخضر
        } else {
            holder.status.setText("جديد - ابدأ الآن 🆕");
            holder.status.setTextColor(Color.parseColor("#FFD700")); // لون ذهبي/أصفر
        }
        
        // 3. منطق النقر (التوجيه الذكي الذي قمنا به سابقاً)
        holder.itemView.setOnClickListener(v -> {
            String userId = context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE).getString("TelegramUserId", "");
            String deviceId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            
            String baseUrl = "https://secured-bot.vercel.app";
            String targetUrl;

            if (exam.isCompleted && exam.firstAttemptId != null) {
                targetUrl = baseUrl + "/results/" + exam.firstAttemptId 
                          + "?userId=" + userId 
                          + "&deviceId=" + deviceId;
            } else {
                targetUrl = baseUrl + "/exam/" + exam.id 
                          + "?userId=" + userId 
                          + "&deviceId=" + deviceId;
            }
            
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("URL", targetUrl);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return exams != null ? exams.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView status; // ✅ تعريف المتغير الجديد

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.exam_title);
            status = itemView.findViewById(R.id.exam_status); // ✅ ربطه بالـ XML
        }
    }
}
