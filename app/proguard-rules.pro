# 1. أهم قاعدة لعمل Retrofit (الحفاظ على Generics)
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# 2. الحفاظ على كلاسات البيانات (Entities & Models)
# لكي يستطيع Gson تحويل الـ JSON إليها
-keep class com.example.secureapp.database.** { *; }
-keep class com.example.secureapp.network.** { *; }

# 3. الحفاظ على واجهة الاتصال (ApiService)
-keep interface com.example.secureapp.network.ApiService { *; }

# 4. قواعد Retrofit العامة
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# 5. قواعد Gson
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }

# 6. إعدادات مكتبة التحميل (Youtube Downloader) - من الكود القديم
-keep class com.github.kiulian.downloader.** { *; }
-keep interface com.github.kiulian.downloader.** { *; }

# 7. إعدادات مكتبة FastJson - من الكود القديم
-keep class com.alibaba.fastjson.** { *; }
-keep interface com.alibaba.fastjson.** { *; }
-dontwarn com.alibaba.fastjson.**

# 8. إعدادات OkHttp و Okio - من الكود القديم
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 9. قواعد التشفير والحماية
-keep class androidx.security.crypto.** { *; }
-dontwarn java.lang.invoke.**

# تجاهل تحذيرات عامة
-dontwarn **

# ... (القواعد الموجودة)

# ✅ حماية كود التحميل لضمان عمله في الخلفية
-keep class com.example.secureapp.DownloadWorker { *; }
-keep class androidx.work.Worker { *; }
