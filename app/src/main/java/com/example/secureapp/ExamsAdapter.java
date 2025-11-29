package com.example.secureapp;

import android.content.Context;
import android.content.Intent;
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

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exam, parent, false);
        return new ViewHolder(view);
    }

  @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ExamEntity exam = exams.get(position);
        holder.title.setText(exam.title);
        
        holder.itemView.setOnClickListener(v -> {
            // ✅ جلب الـ ID والبصمة
            String userId = context.getSharedPreferences("SecureAppPrefs", Context.MODE_PRIVATE).getString("TelegramUserId", "");
            String deviceId = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID); // ✅ إضافة البصمة
            
            // ✅ تمرير deviceId في الرابط
            String url = "https://secured-bot.vercel.app/exam/" + exam.id + "?userId=" + userId + "&deviceId=" + deviceId; 
            
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.putExtra("URL", url);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return exams != null ? exams.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.exam_title);
        }
    }
}
