package com.example.secureapp.network;

public class DeviceCheckRequest {
    public String userId;
    public String fingerprint; // بصمة الجهاز

    public DeviceCheckRequest(String userId, String fingerprint) {
        this.userId = userId;
        this.fingerprint = fingerprint;
    }
}
