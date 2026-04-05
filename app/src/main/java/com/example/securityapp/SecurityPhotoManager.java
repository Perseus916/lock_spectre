package com.ansh.lockspectre;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager class for security photos, including automatic backup scheduling
 */
public class SecurityPhotoManager {
    private static final String TAG = "SecurityPhotoManager";
    private static final long AUTO_BACKUP_DELAY_MS = 60 * 1000; // 1 minute after photo capture
    private static final long MAX_PENDING_PHOTOS = 3; // Reduced threshold to trigger backup sooner
    private static final long FILE_READINESS_DELAY = 1000; // 1 second to ensure file is ready

    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable pendingBackupTask = null;

    // Track when immediate backup was last triggered to prevent race conditions
    private static long lastImmediateBackupTime = 0;
    private static final long IMMEDIATE_BACKUP_COOLDOWN = 10000; // 10 seconds cooldown

    // Track backup failures to implement retry
    private static int backupFailureCount = 0;
    private static long lastBackupAttemptTime = 0;
    private static final int MAX_AUTO_RETRY_COUNT = 3;
    private static final long[] RETRY_DELAYS = {30000, 60000, 120000}; // 30s, 1m, 2m

    /**
     * Notify that a new security photo has been captured
     * This will schedule an automatic backup if enabled
     */
    public static void notifyNewPhoto(Context context, String photoFileName) {
        Log.d(TAG, "Cloud backup disabled in free mode; not scheduling photo: " + photoFileName);
    }

    /**
     * Schedule a retry for backup if previous attempt failed
     */
    private static void scheduleBackupRetry(Context context) {
        if (backupFailureCount >= MAX_AUTO_RETRY_COUNT) {
            Log.e(TAG, "Maximum retry count reached (" + MAX_AUTO_RETRY_COUNT + "), giving up automatic retries");
            backupFailureCount = 0; // Reset for next time
            return;
        }

        // Determine delay based on failure count (with bounds check)
        long delay = backupFailureCount < RETRY_DELAYS.length ?
                     RETRY_DELAYS[backupFailureCount] :
                     RETRY_DELAYS[RETRY_DELAYS.length - 1];

        backupFailureCount++;

        Log.d(TAG, "Scheduling backup retry #" + backupFailureCount + " in " + (delay/1000) + " seconds");

        // Create a new retry task
        handler.postDelayed(() -> {
            Log.d(TAG, "Executing backup retry #" + backupFailureCount);
            triggerAutomaticBackup(context);
        }, delay);
    }

    /**
     * Count the number of photos that need to be backed up
     */
    private static int countPendingPhotos(Context context) {
        List<File> pendingPhotos = getPendingBackupPhotos(context);
        return pendingPhotos.size();
    }

    /**
     * Get list of photos that haven't been backed up yet
     */
    private static List<File> getPendingBackupPhotos(Context context) {
        List<File> pendingPhotos = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences("security_app", Context.MODE_PRIVATE);

        try {
            File photosDir = new File(context.getFilesDir(), "security_photos");
            if (!photosDir.exists() || !photosDir.isDirectory()) {
                return pendingPhotos;
            }

            File[] photos = photosDir.listFiles((dir, name) -> name.endsWith(".jpg"));
            if (photos != null) {
                // Check each photo if it needs backup
                for (File photo : photos) {
                    String fileName = photo.getName();
                    long backupTime = prefs.getLong("backup_time_" + fileName, 0);

                    // If never backed up or backup older than file modification
                    if (backupTime == 0 || backupTime < photo.lastModified()) {
                        pendingPhotos.add(photo);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking pending photos", e);
        }

        return pendingPhotos;
    }

    /**
     * Trigger automatic backup service
     */
    public static void triggerAutomaticBackup(Context context) {
        Log.d(TAG, "Automatic cloud backup disabled in free mode");
    }

    /**
     * Reset backup failure count (called when backup succeeds)
     */
    public static void onBackupSuccess() {
        backupFailureCount = 0;
        Log.d(TAG, "Backup completed successfully, reset failure count");
    }

    /**
     * Track backup failure (called when backup fails)
     */
    public static void onBackupFailure() {
        Log.d(TAG, "Backup failure ignored because cloud backup is disabled");
    }
}
