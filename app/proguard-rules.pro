# Add project specific ProGuard rules here.

# 1. إعدادات مكتبة التحميل (Youtube Downloader)
-keep class com.github.kiulian.downloader.** { *; }
-keep interface com.github.kiulian.downloader.** { *; }

# 2. إعدادات مكتبة FastJson
-keep class com.alibaba.fastjson.** { *; }
-keep interface com.alibaba.fastjson.** { *; }
# تجاهل تحذيرات الأجزاء غير المستخدمة في FastJson (هذا هو سبب الخطأ لديك)
-dontwarn com.alibaba.fastjson.**
-dontwarn java.awt.**
-dontwarn javax.servlet.**
-dontwarn javax.ws.rs.**
-dontwarn javax.money.**
-dontwarn org.joda.time.**
-dontwarn org.springframework.**
-dontwarn retrofit2.**

# 3. إعدادات OkHttp و Okio
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
# تجاهل تحذيرات منصات التشفير غير الموجودة
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 4. إعدادات عامة
-keep class androidx.security.crypto.** { *; }
-dontwarn java.lang.invoke.**
-dontwarn **
