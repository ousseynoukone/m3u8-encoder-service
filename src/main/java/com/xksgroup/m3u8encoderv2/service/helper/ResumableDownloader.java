package com.xksgroup.m3u8encoderv2.service.helper;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;

public class ResumableDownloader {

    private final OkHttpClient client = new OkHttpClient();

    public interface ProgressListener {
        void onProgress(long bytesRead, long totalBytes);
    }

    /**
     * Downloads a file from the given URL to the specified path, resuming if possible,
     * and reporting progress to the listener.
     */
    public void download(String url, String outputPath, ProgressListener listener) throws Exception {
        File file = new File(outputPath);
        long existingFileSize = file.exists() ? file.length() : 0;

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (existingFileSize > 0) {
            requestBuilder.addHeader("Range", "bytes=" + existingFileSize + "-");
            System.out.println("📦 Resuming from byte " + existingFileSize);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed: " + response);
            }

            long contentLength = response.body() != null ? response.body().contentLength() : -1;
            long totalBytes = contentLength > 0 ? contentLength + existingFileSize : -1;

            try (ResponseBody body = response.body();
                 InputStream inputStream = body.byteStream();
                 RandomAccessFile savedFile = new RandomAccessFile(file, "rw")) {

                savedFile.seek(existingFileSize);
                byte[] buffer = new byte[8192];
                int bytesRead;
                long downloaded = existingFileSize;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    savedFile.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;
                    if (listener != null && totalBytes > 0) {
                        listener.onProgress(downloaded, totalBytes);
                    }
                }
            }
        }
        System.out.println("✅ Download complete: " + outputPath);
    }
}

