package com.example.secureapp.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class VideoApiResponse {
    @SerializedName("url") 
    public String streamUrl;

    @SerializedName("youtube_video_id")
    public String youtubeId;

    @SerializedName("message")
    public String message;
    
    @SerializedName("duration")
    public String duration;

    @SerializedName("availableQualities")
    public List<QualityOption> availableQualities;

    public static class QualityOption {
        // ✅ التعديل: تغيير النوع إلى String ليقبل أي صيغة من السيرفر
        @SerializedName("quality")
        public String quality; 

        @SerializedName("url")
        public String url;
    }
}
