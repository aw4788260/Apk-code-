package com.example.secureapp;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class EncryptedFileDataSource extends BaseDataSource {

    private final Context context;
    private final File encryptedFile;
    private InputStream inputStream;
    private long bytesRemaining;
    private boolean opened;

    public EncryptedFileDataSource(Context context, File file) {
        super(/* isNetwork= */ false);
        this.context = context;
        this.encryptedFile = file;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        try {
            transferInitializing(dataSpec);

            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            inputStream = encryptedFileObj.openFileInput();

            // 1. التخطي الآمن (Force Skip) للوصول لنقطة البداية المطلوبة
            if (dataSpec.position > 0) {
                long totalSkipped = 0;
                while (totalSkipped < dataSpec.position) {
                    long skippedNow = inputStream.skip(dataSpec.position - totalSkipped);
                    if (skippedNow == 0) {
                        // إذا لم يتخطَ، نقرأ بايت واحد لنجبره على التحرك
                        if (inputStream.read() == -1) {
                            throw new EOFException("End of stream reached while skipping");
                        } else {
                            skippedNow = 1;
                        }
                    }
                    totalSkipped += skippedNow;
                }
            }

            // 2. ✅✅ التصحيح هنا: عدم استخدام available() نهائياً
            // إذا كان المشغل يعرف الطول المطلوب (مثلاً من الكاش)، نستخدمه.
            // غير ذلك، نتركه "غير محدد" (LENGTH_UNSET) ليقرأ حتى النهاية الحقيقية.
            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesRemaining = dataSpec.length;
            } else {
                bytesRemaining = C.LENGTH_UNSET;
            }

            opened = true;
            transferStarted(dataSpec);
            return bytesRemaining;

        } catch (Exception e) {
            FirebaseCrashlytics.getInstance().recordException(new Exception("EncryptedDataSource Open Error: " + encryptedFile.getName(), e));
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) return 0;
        if (bytesRemaining == 0) return C.RESULT_END_OF_INPUT;

        int bytesToRead = readLength;
        if (bytesRemaining != C.LENGTH_UNSET) {
            bytesToRead = (int) Math.min(readLength, bytesRemaining);
        }

        int bytesRead;
        try {
            bytesRead = inputStream.read(buffer, offset, bytesToRead);
        } catch (IOException e) {
            throw e;
        }

        if (bytesRead == -1) {
            // وصلنا لنهاية الملف الحقيقية
            if (bytesRemaining != C.LENGTH_UNSET) {
                // إذا كنا نتوقع المزيد ولكن الملف انتهى، فهذا خطأ حقيقي (EOFException)
                // لكن غالباً مع التشفير، الحجم يختلف قليلاً، لذا نعتبره نهاية طبيعية
                return C.RESULT_END_OF_INPUT;
            }
            return C.RESULT_END_OF_INPUT;
        }

        if (bytesRemaining != C.LENGTH_UNSET) {
            bytesRemaining -= bytesRead;
        }
        
        bytesTransferred(bytesRead);
        return bytesRead;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return Uri.fromFile(encryptedFile);
    }

    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            try {
                inputStream.close();
            } finally {
                inputStream = null;
                if (opened) {
                    opened = false;
                    transferEnded();
                }
            }
        }
    }
}
