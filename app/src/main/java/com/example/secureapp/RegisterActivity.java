package com.example.secureapp;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.secureapp.network.RetrofitClient;
import com.example.secureapp.network.SignupRequest;
import com.example.secureapp.network.SignupResponse;
// ✅ إضافة مكتبات Gson لتحليل رسالة الخطأ
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etPhone, etUsername, etPassword, etConfirmPass;
    private Button btnRegister;
    private TextView tvLoginLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.et_fullname);
        etPhone = findViewById(R.id.et_phone);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirmPass = findViewById(R.id.et_confirm_password);
        btnRegister = findViewById(R.id.btn_register);
        tvLoginLink = findViewById(R.id.tv_login_link);

        btnRegister.setOnClickListener(v -> attemptRegister());
        tvLoginLink.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPass = etConfirmPass.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "يرجى ملء جميع الحقول", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "كلمة المرور يجب أن تكون 6 أحرف على الأقل", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPass)) {
            Toast.makeText(this, "كلمة المرور غير متطابقة", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("جاري إنشاء الحساب...");
        dialog.setCancelable(false);
        dialog.show();

        RetrofitClient.getApi().signup(new SignupRequest(name, username, password, phone))
                .enqueue(new Callback<SignupResponse>() {
                    @Override
                    public void onResponse(Call<SignupResponse> call, Response<SignupResponse> response) {
                        dialog.dismiss();
                        if (response.isSuccessful() && response.body() != null) {
                            if (response.body().success) {
                                new AlertDialog.Builder(RegisterActivity.this)
                                        .setTitle("تم بنجاح ✅")
                                        .setMessage(response.body().message)
                                        .setPositiveButton("تسجيل الدخول", (d, w) -> finish())
                                        .setCancelable(false)
                                        .show();
                            } else {
                                showError("خطأ", response.body().message);
                            }
                        } else {
                            // ✅ التعديل هنا: قراءة رسالة الخطأ من السيرفر (مثل التكرار)
                            String errorMsg = "حدث خطأ غير معروف";
                            try {
                                if (response.errorBody() != null) {
                                    String errorStr = response.errorBody().string();
                                    // تحليل الـ JSON لاستخراج الرسالة
                                    JsonObject jsonObject = new Gson().fromJson(errorStr, JsonObject.class);
                                    if (jsonObject.has("message")) {
                                        errorMsg = jsonObject.get("message").getAsString();
                                    }
                                }
                            } catch (Exception e) {
                                errorMsg = "خطأ في الاتصال: " + response.code();
                            }
                            showError("تنبيه", errorMsg);
                        }
                    }

                    @Override
                    public void onFailure(Call<SignupResponse> call, Throwable t) {
                        dialog.dismiss();
                        showError("خطأ اتصال", t.getMessage());
                    }
                });
    }

    private void showError(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("حسناً", null)
                .show();
    }
}
