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
    
    // ✅ [جديد] استقبال المدة من السيرفر
    @SerializedName("duration")
    public String duration; // قد تكون نصاً أو رقماً (Json مرن)

    @SerializedName("availableQualities")
    public List<QualityOption> availableQualities;

    public static class QualityOption {
        @SerializedName("quality")
        public int quality;

        @SerializedName("url")
        public String url;
    }
}
