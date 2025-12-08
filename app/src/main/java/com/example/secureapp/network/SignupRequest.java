package com.example.secureapp.network;

public class SignupRequest {
    public String firstName;
    public String username;
    public String password;
    public String phone;

    public SignupRequest(String firstName, String username, String password, String phone) {
        this.firstName = firstName;
        this.username = username;
        this.password = password;
        this.phone = phone;
    }
}
