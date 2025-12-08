package com.example.secureapp.network;

public class LoginResponse {
    public boolean success;
    public String userId;
    public String firstName;
    public boolean isAdmin; // ✅ جديد: لاستقبال صلاحية الأدمن
    public String message;
}
