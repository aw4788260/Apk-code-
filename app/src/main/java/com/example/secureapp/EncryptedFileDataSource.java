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

            // 1. إعداد مفاتيح التشفير
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            
            // 2. تجهيز كائن الملف المشفر للقراءة
            EncryptedFile encryptedFileObj = new EncryptedFile.Builder(
                    encryptedFile,
                    context,
                    masterKeyAlias,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build();

            // 3. فتح الـ Stream (القراءة ستتم في الذاكرة)
            inputStream = encryptedFileObj.openFileInput();

            // (دعم التقديم والتأخير: تخطي البايتات إذا طلب المشغل البدء من منتصف الفيديو)
            if (dataSpec.position > 0) {
                long skipped = inputStream.skip(dataSpec.position);
                if (skipped < dataSpec.position) {
                    throw new IOException("Unable to skip bytes in encrypted stream");
                }
            }

            // تحديد الطول (مهم لشريط التقدم)
            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesRemaining = dataSpec.length;
            } else {
                bytesRemaining = inputStream.available();
                if (bytesRemaining == 0) bytesRemaining = C.LENGTH_UNSET;
            }

            opened = true;
            transferStarted(dataSpec);
            return bytesRemaining;

        } catch (Exception e) {
            // تسجيل الخطأ في Crashlytics للمراقبة
            FirebaseCrashlytics.getInstance().recordException(new Exception("EncryptedDataSource Open Error: " + encryptedFile.getName(), e));
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int bytesToRead = readLength;
        if (bytesRemaining != C.LENGTH_UNSET) {
            bytesToRead = (int) Math.min(readLength, bytesRemaining);
        }

        int bytesRead;
        try {
            // القراءة وفك التشفير يتم هنا في الذاكرة (RAM)
            bytesRead = inputStream.read(buffer, offset, bytesToRead);
        } catch (IOException e) {
            FirebaseCrashlytics.getInstance().recordException(new Exception("EncryptedDataSource Read Error", e));
            throw e;
        }

        if (bytesRead == -1) {
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
            } catch (IOException e) {
                FirebaseCrashlytics.getInstance().recordException(new Exception("EncryptedDataSource Close Error", e));
                throw e;
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
