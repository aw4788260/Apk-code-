# Add project specific ProGuard rules here.

# [ ✅✅✅ هذا هو الإصلاح الشامل ]

# 1. إبقاء مكتبة تحميل اليوتيوب
-keep class com.github.kiulian.downloader.** { *; }
-keep interface com.github.kiulian.downloader.** { *; }

# 2. إبقاء مكتبة fastjson (تعتمد عليها مكتبة التحميل)
-keep class com.alibaba.fastjson.** { *; }
-keep interface com.alibaba.fastjson.** { *; }

# 3. [إضافة جديدة] إبقاء OkHttp و Okio (مهم جداً)
# (مكتبة التحميل والـ Worker يعتمدان عليهما للاتصال)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }

# 4. إبقاء الكلاسات اللازمة للتشفير (احتياطي)
-keep class androidx.security.crypto.** { *; }
