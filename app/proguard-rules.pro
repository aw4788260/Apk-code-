# Add project specific ProGuard rules here.

# [ ✅✅✅ هذا هو الإصلاح الحاسم ]
# منع R8/ProGuard من حذف كلاسات مكتبة التحميل
# التي يعتقد أنها غير مستخدمة (لأنها مستخدمة فقط في الـ Worker)

# إبقاء مكتبة تحميل اليوتيوب
-keep class com.github.kiulian.downloader.** { *; }
-keep interface com.github.kiulian.downloader.** { *; }

# إبقاء مكتبة fastjson (التي تعتمد عليها مكتبة التحميل)
# (مكتبة التحميل تستخدمها لتحليل الـ JSON)
-keep class com.alibaba.fastjson.** { *; }
-keep interface com.alibaba.fastjson.** { *; }

# إبقاء الكلاسات اللازمة للتشفير (احتياطي)
-keep class androidx.security.crypto.** { *; }
