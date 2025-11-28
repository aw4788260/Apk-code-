package com.example.secureapp;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.room.Room;
import com.example.secureapp.database.AppDatabase;
import com.example.secureapp.database.SubjectEntity;
import com.example.secureapp.network.RetrofitClient;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NativeHomeActivity extends AppCompatActivity {
    private SwipeRefreshLayout swipeRefresh;
    private SubjectsAdapter adapter;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_native_home);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubjectsAdapter();
        recyclerView.setAdapter(adapter);

        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "secure-app-db")
                .allowMainThreadQueries().build();

        // 1. تحميل البيانات المخزنة فوراً
        loadLocalData();

        // 2. تفعيل السحب للتحديث
        swipeRefresh.setOnRefreshListener(this::fetchDataFromServer);
        
        // 3. تحميل تلقائي عند الفتح
        fetchDataFromServer();
    }

    private void loadLocalData() {
        List<SubjectEntity> data = db.subjectDao().getAllSubjects();
        if(data != null && !data.isEmpty()){
            adapter.updateData(data);
        }
    }

    private void fetchDataFromServer() {
        String userId = getSharedPreferences("SecureAppPrefs", MODE_PRIVATE).getString("TelegramUserId", "");
        
        if(userId.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        RetrofitClient.getApi().getCourses(userId).enqueue(new Callback<List<SubjectEntity>>() {
            @Override
            public void onResponse(Call<List<SubjectEntity>> call, Response<List<SubjectEntity>> response) {
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    db.subjectDao().deleteAll();
                    db.subjectDao().insertAll(response.body());
                    loadLocalData();
                    Toast.makeText(NativeHomeActivity.this, "تم التحديث", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<SubjectEntity>> call, Throwable t) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(NativeHomeActivity.this, "فشل الاتصال", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
