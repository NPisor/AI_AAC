package com.example.ai_aac;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import ai.onnxruntime.genai.GenAIException;

public class ModelDownloader {
    private static final int MAX_RETRIES = 3; // Maximum number of retries for a file
    private static final int TIMEOUT = 15000; // 15 seconds timeout for each connection attempt

    public interface DownloadCallback {
        void onProgress(long lastBytesRead, long bytesRead, long bytesTotal);
        void onDownloadComplete() throws GenAIException;
    }

    public static void downloadModel(Context context, List<Pair<String, String>> urlFilePairs, DownloadCallback callback) {
        try {
            for (Pair<String, String> urlFilePair : urlFilePairs) {
                String url = urlFilePair.first;
                String fileName = urlFilePair.second;
                File file = new File(context.getFilesDir(), fileName);
                File tempFile = new File(context.getFilesDir(), fileName + ".tmp");

                boolean downloadComplete = false;
                int attempts = 0;

                while (!downloadComplete && attempts < MAX_RETRIES) {
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(url).openConnection();
                        connection.setConnectTimeout(TIMEOUT);
                        connection.setReadTimeout(TIMEOUT);

                        connection.connect();
                        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                            InputStream inputStream = connection.getInputStream();
                            FileOutputStream outputStream = new FileOutputStream(tempFile, false); // Always overwrite

                            long totalFileSize = connection.getContentLengthLong();
                            long totalBytesRead = 0;

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                totalBytesRead += bytesRead;

                                if (callback != null) {
                                    callback.onProgress(totalBytesRead - bytesRead, totalBytesRead, totalFileSize);
                                }
                            }

                            outputStream.close();
                            inputStream.close();

                            // Rename temp file to actual file after successful download
                            if (tempFile.renameTo(file)) {
                                Log.d(TAG, "File downloaded successfully: " + fileName);
                                downloadComplete = true;
                            } else {
                                Log.e(TAG, "Failed to rename temp file to original file");
                            }
                        } else {
                            Log.e(TAG, "Failed to download model. HTTP response code: " + connection.getResponseCode());
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Download failed on attempt " + (attempts + 1) + ": " + e.getMessage());
                        attempts++;
                        tempFile.delete(); // Delete partial file on failure
                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }
            }

            if (callback != null) {
                callback.onDownloadComplete();
            }
        } catch (GenAIException e) {
            throw new RuntimeException(e);
        }
    }

}
