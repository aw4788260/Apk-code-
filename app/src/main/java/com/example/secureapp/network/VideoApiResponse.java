package com.example.secureapp.network;

import com.google.gson.annotations.SerializedName;

public class VideoApiResponse {
    // الرابط المباشر (القادم من سيرفر Railway عبر Next.js)
    @SerializedName("url") 
    public String streamUrl;

    @SerializedName("youtube_video_id")
    public String youtubeId;

    @SerializedName("message")
    public String message; // في حالة الخطأ
}
