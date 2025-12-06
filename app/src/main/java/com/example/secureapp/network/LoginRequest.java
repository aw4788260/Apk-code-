package com.example.secureapp.network;

public class LoginRequest {
    public String username;
    public String password;
    public String deviceId;

    public LoginRequest(String username, String password, String deviceId) {
        this.username = username;
        this.password = password;
        this.deviceId = deviceId;
    }
}
