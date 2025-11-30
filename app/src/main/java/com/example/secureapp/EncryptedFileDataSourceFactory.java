package com.example.secureapp;

import android.content.Context;
import androidx.media3.datasource.DataSource;
import java.io.File;

public class EncryptedFileDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final File encryptedFile;

    public EncryptedFileDataSourceFactory(Context context, File encryptedFile) {
        this.context = context;
        this.encryptedFile = encryptedFile;
    }

    @Override
    public DataSource createDataSource() {
        return new EncryptedFileDataSource(context, encryptedFile);
    }
}
