package com.example.secureapp.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VideoApiResponse {
    // الرابط المباشر الافتراضي (لو وجد)
    @SerializedName("url") 
    public String streamUrl;

    @SerializedName("youtube_video_id")
    public String youtubeId;

    @SerializedName("message")
    public String message;

    // ✅ [مهم جداً] قائمة الجودات المتاحة لاستقبالها من السيرفر
    @SerializedName("availableQualities")
    public List<QualityOption> availableQualities;

    // كلاس داخلي لتمثيل بيانات كل جودة
    public static class QualityOption {
        @SerializedName("quality")
        public int quality; // مثلاً 1080, 720

        @SerializedName("url")
        public String url; // رابط هذه الجودة
    }
}
