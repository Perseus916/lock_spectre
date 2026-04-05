package com.ansh.lockspectre;

import static com.ansh.lockspectre.UserManager.getUserPhotosDirectory;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_ENABLE_ADMIN = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_SMS_PERMISSION = 101;
    private static final int REQUEST_LOCATION_PERMISSION = 102;
    private static final int REQUEST_WIFI_STATE_PERMISSION = 103;
    private static final int REQUEST_MULTIPLE_PERMISSIONS = 104;

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private TextView statusText;
    private Button enableButton;
    private Button viewPhotosButton;
    private TextView photosCountText;
    private TextView lastCaptureText;
    private ImageButton settingsButton;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean firestorePermissionsChecked = false;

    private boolean initializationInProgress = false;

    // Simplified visibility tracking variables
    private boolean isVisible = false;
    private long lastVisibilityChangeTime = 0;

    // Add these variables for cloud photo functionality
    private boolean isShowingCloudPhotos = false;
    private List<File> cloudPhotoRefs = new ArrayList<>();
    private File cloudPhotosDir;

    // Add cloud photo count tracking
    private int currentCloudPhotoCount = 0;

    // Add photo cache to prevent repeated searches
    private static final long PHOTO_CACHE_DURATION = 10000;
    private static List<File> cachedPhotosList = new ArrayList<>();
    private static long lastPhotoSearchTime = 0;

    // Add missing variable for dialog tracking
    private AlertDialog currentPhotosListDialog = null;

    private BroadcastReceiver photoCaptureReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Quick resource check
            ensureResourcesAvailable();

            // Initialize Firebase in background
            initializeFirebase();

            // Quick authentication check
            if (!checkAuthenticationStatus()) {
                Log.d(TAG, "User not authenticated, redirecting to AuthActivity");
                Intent intent = new Intent(this, AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
                overridePendingTransition(0, 0);
                return;
            }

            // Continue with app initialization
            finishInitialization();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            // Show simple error and continue
            Toast.makeText(this, "Starting app...", Toast.LENGTH_SHORT).show();

            try {
                finishInitialization();
            } catch (Exception retryException) {
                Log.e(TAG, "Initialization failed completely", retryException);
            }
        }
    }

    /**
     * Quick resource check - removed heavy verification
     */
    private void ensureResourcesAvailable() {
        // Simplified - just log that we're starting
        Log.d(TAG, "Starting resource initialization");
    }

    /**
     * Initialize Firebase components with Google Play Services checking - Optimized for fast startup
     */
    private void initializeFirebase() {
        // Initialize Firebase components quickly without blocking
        new Thread(() -> {
            try {
                // Quick Firebase Auth initialization
                try {
                    mAuth = FirebaseAuth.getInstance();
                    Log.d(TAG, "Firebase Auth initialized");
                } catch (Exception authError) {
                    Log.w(TAG, "Firebase Auth unavailable", authError);
                    mAuth = null;
                }

                // Quick Firestore initialization
                try {
                    db = FirebaseFirestore.getInstance();
                    Log.d(TAG, "Firestore initialized");
                } catch (Exception firestoreError) {
                    Log.w(TAG, "Firestore unavailable", firestoreError);
                    db = null;
                }

            } catch (Exception e) {
                Log.w(TAG, "Firebase initialization failed, using local mode", e);
                mAuth = null;
                db = null;
            }
        }).start();
    }



    /**
     * Check authentication status quickly - optimized for fast startup
     */
    private boolean checkAuthenticationStatus() {
        try {
            // Quick SharedPreferences check first (fastest)
            SharedPreferences prefs = getSharedPreferences("security_app", MODE_PRIVATE);
            String userEmail = prefs.getString("user_email", "");

            if (!userEmail.isEmpty()) {
                Log.d(TAG, "User authenticated via cached credentials");
                return true;
            }

            // Fallback to Firebase if available (done in background)
            if (mAuth != null) {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null) {
                    Log.d(TAG, "User authenticated via Firebase");
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error checking authentication status", e);
            return false;
        }
    }

    private void finishInitialization() {
        try {
            initializationInProgress = true;

            setContentView(R.layout.activity_main);

            // Setup app bar and initialize views first (fast UI operations)
            setupAppBar();
            initializeViews();
            setupDeviceAdmin();

            // Defer heavy operations to background threads
            deferHeavyInitialization();

        } catch (Exception e) {
            Log.e(TAG, "Error in finishInitialization", e);
        } finally {
            initializationInProgress = false;
        }
    }

    /**
     * Defer heavy initialization operations to improve startup speed
     */
    private void deferHeavyInitialization() {
        // Use a background thread for heavy operations
        new Thread(() -> {
            try {
                // Check permissions in background (non-blocking)
                runOnUiThread(this::checkPermissions);

                // Wait a bit before doing Firebase operations to let UI load
                Thread.sleep(500);

                // Firebase operations in background
                if (mAuth != null && db != null) {
                    checkFirestorePermissions();
                    updateFirebaseUserIfNeeded();
                } else {
                    firestorePermissionsChecked = true;
                }

                // Update statistics after a delay to not block startup
                runOnUiThread(() -> {
                    new Handler().postDelayed(this::updateStats, 1000);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in deferred initialization", e);
            }
        }).start();
    }

    private void checkFirestorePermissions() {
        if (firestorePermissionsChecked || db == null) return;

        FirebaseHelper.checkFirestorePermissions(this, new FirebaseHelper.FirestorePermissionCallback() {
            @Override
            public void onResult(boolean success, String message) {
                firestorePermissionsChecked = true;
                if (!success) {
                    Log.w(TAG, "Firestore permissions issue: " + message);
                    // Still try to continue with app functionality
                }
            }
        });
    }

    private void updateFirebaseUserIfNeeded() {
        try {
            // Skip if Firebase Auth isn't initialized
            if (mAuth == null) {
                Log.w(TAG, "Firebase Auth is not initialized, skipping profile update");
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && (user.getDisplayName() == null || user.getDisplayName().isEmpty())) {
                SharedPreferences prefs = getSharedPreferences("get_intruder", MODE_PRIVATE);
                String userName = prefs.getString("user_name", "User");

                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(userName)
                    .build();

                user.updateProfile(profileUpdates)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "User profile updated on app restart: " + userName);
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to update user profile", e);
                    });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating Firebase user", e);
        }
    }

    private void setupAppBar() {
        // Hide default action bar since we're using custom top bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Set status bar color to match top bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.primary_blue));
        }
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        enableButton = findViewById(R.id.enableButton);
        viewPhotosButton = findViewById(R.id.viewPhotosButton);
        photosCountText = findViewById(R.id.photosCountText);
        lastCaptureText = findViewById(R.id.lastCaptureText);
        settingsButton = findViewById(R.id.settingsButton);

        // Set up button listeners with optimized logic
        enableButton.setOnClickListener(v -> toggleDeviceAdmin());
        viewPhotosButton.setOnClickListener(v -> viewSecurityPhotos());
        settingsButton.setOnClickListener(v -> showMainSettings());

        // Initialize with placeholder values to avoid empty UI
        photosCountText.setText("...");
        lastCaptureText.setText("Loading...");
    }

    /**
     * Optimized device admin toggle with immediate UI updates
     */
    private void toggleDeviceAdmin() {
        boolean isAdminActive = devicePolicyManager.isAdminActive(adminComponent);
        if (isAdminActive) {
            // Quick disable confirmation
            new AlertDialog.Builder(this)
                .setTitle("Disable Security")
                .setMessage("Disable security monitoring?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Update UI immediately for responsive feel
                    enableButton.setText("Enable Detection");
                    enableButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_blue));
                    enableButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
                    statusText.setText("Intruder detection is INACTIVE");
                    statusText.setSelected(false);

                    // Set red background for inactive status
                    int inactiveColor = ContextCompat.getColor(this, android.R.color.holo_red_light);
                    setRoundedBackground(statusText, inactiveColor);

                    // Actually disable admin in background
                    new Thread(() -> {
                        devicePolicyManager.removeActiveAdmin(adminComponent);
                        runOnUiThread(() -> {
                            // Final status check after disabling
                            updateStatus();
                        });
                    }).start();
                })
                .setNegativeButton("No", null)
                .show();
        } else {
            enableDeviceAdmin();
        }
    }

    /**
     * Show main settings menu (from the settings icon)
     */
    private void showMainSettings() {
        String[] options = {
            "Account",
            "Test Intruder Capture",
            "Sign Out"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");

        builder.setItems(options, (dialog, which) -> {
            switch(which) {
                case 0:
                    showAccountInfo();
                    break;
                case 1:
                    triggerManualTestCapture();
                    break;
                case 2:
                    confirmSignOut();
                    break;
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Manual fallback for OEMs that block DeviceAdmin password-failed callbacks.
     */
    private void triggerManualTestCapture() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission is required for test capture", Toast.LENGTH_LONG).show();
                return;
            }

            Intent cameraIntent = new Intent(this, CameraService.class);
            cameraIntent.putExtra("action", "CAPTURE_INTRUDER");
            cameraIntent.putExtra("trigger", "manual_test");
            cameraIntent.putExtra("send_notification", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(cameraIntent);
            } else {
                startService(cameraIntent);
            }

            Toast.makeText(this, "📸 Test capture started. Wait 2-5 seconds.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to trigger manual test capture", e);
            Toast.makeText(this, "Failed to start test capture: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupDeviceAdmin() {
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, SecurityDeviceAdminReceiver.class);

        updateStatus();
    }

    private void checkPermissions() {
        // Create a list to hold all permissions we need to request
        List<String> permissionsNeeded = new ArrayList<>();

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Check location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Check WiFi state permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_WIFI_STATE);
        }

        // Request permissions directly without first dialog
        if (!permissionsNeeded.isEmpty()) {
            requestPermissions(permissionsNeeded);
        } else {
            // All permissions granted, check background location
            checkBackgroundLocationPermission();
        }
    }



    /**
     * Request the list of permissions
     */
    private void requestPermissions(List<String> permissionsNeeded) {
        Log.d(TAG, "Requesting permissions: " + permissionsNeeded.toString());
        ActivityCompat.requestPermissions(this,
                permissionsNeeded.toArray(new String[0]),
                REQUEST_MULTIPLE_PERMISSIONS);
    }

    /**
     * Check and request background location permission
     */
    private void checkBackgroundLocationPermission() {
        // Check background location permission for Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Only request background location if we already have foreground location
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    showBackgroundLocationDialog();
                }
            }
        }
    }

    /**
     * Show dialog explaining background location permission
     */
    private void showBackgroundLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔒 Background Location Required");

        String message = "For complete security monitoring, LockSpectre needs background location access.\n\n" +
                "This allows the app to:\n" +
                "📍 Track intruder location even when app is closed\n" +
                "🚨 Send alerts with precise location data\n" +
                "🔐 Provide continuous security monitoring\n\n" +
                "Please select 'Allow all the time' on the next screen.";

        builder.setMessage(message);
        builder.setIcon(R.drawable.ic_security_24);

        builder.setPositiveButton("Enable Background Access", (dialog, which) -> {
            dialog.dismiss();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        });

        builder.setNegativeButton("Not Now", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(this, "Background monitoring disabled. Security features may be limited.", Toast.LENGTH_LONG).show();
        });

        builder.setCancelable(true);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_MULTIPLE_PERMISSIONS) {
            boolean allGranted = true;
            boolean hasLocationPermission = false;
            StringBuilder deniedPermissions = new StringBuilder();

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    if (deniedPermissions.length() > 0) {
                        deniedPermissions.append(", ");
                    }
                    deniedPermissions.append(permissions[i]);
                } else {
                    // Check if location permission was granted
                    if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        hasLocationPermission = true;
                    }
                }
            }

            if (allGranted) {
                Log.d(TAG, "✅ All permissions granted");
                Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show();
                // Check for background location if we have foreground location
                if (hasLocationPermission) {
                    checkBackgroundLocationPermission();
                }
            } else {
                Log.w(TAG, "❌ Some permissions denied: " + deniedPermissions.toString());
                Toast.makeText(this, "Some permissions were denied. Features may be limited.", Toast.LENGTH_LONG).show();

                // Still check background location if foreground location was granted
                if (hasLocationPermission) {
                    checkBackgroundLocationPermission();
                }
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Background location enabled - Full security monitoring active", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Background location denied - Security monitoring may be limited", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Show dialog explaining why location permission is important - REMOVED
     * This method is no longer called but kept for reference if needed later
     */
    private void showLocationPermissionDialog() {
        // Method body removed - no longer shows popup
        Log.d(TAG, "Location permission dialog suppressed");
    }

    private boolean isAccessibilityServiceEnabled() {
        String settingValue = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (settingValue != null) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(settingValue);

            while (splitter.hasNext()) {
                String serviceName = splitter.next();
            }
        }
        return false;
    }

    private void enableDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Enable device admin to monitor failed unlock attempts and capture intruder photos.");
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            // Update UI immediately
            updateStatus();

            // Show feedback immediately
            boolean isActive = devicePolicyManager.isAdminActive(adminComponent);
            Toast.makeText(this,
                isActive ? "Security monitoring enabled!" : "Security monitoring not enabled",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus() {
        boolean isAdminActive = devicePolicyManager.isAdminActive(adminComponent);

        String statusMessage;

        if (isAdminActive) {
            statusMessage = "Intruder detection is ACTIVE\n(PIN/Pattern)";
            statusText.setSelected(true);

            // Set green background for active status
            int activeColor = ContextCompat.getColor(this, android.R.color.holo_green_dark);
            setRoundedBackground(statusText, activeColor);

            // When security is enabled, use transparent button with blue border
            enableButton.setText("Disable Detection");

            // Transparent button with blue border
            android.graphics.drawable.GradientDrawable transparentBorderDrawable = new android.graphics.drawable.GradientDrawable();
            transparentBorderDrawable.setCornerRadius(30);
            transparentBorderDrawable.setColor(Color.TRANSPARENT);
            transparentBorderDrawable.setStroke(2, ContextCompat.getColor(this, R.color.primary_blue));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                enableButton.setBackground(transparentBorderDrawable);
            } else {
                enableButton.setBackgroundDrawable(transparentBorderDrawable);
            }

            enableButton.setTextColor(ContextCompat.getColor(this, R.color.primary_blue));
        } else {
            statusMessage = "Intruder detection is INACTIVE";
            statusText.setSelected(false);

            // Use the same rounded shape for inactive status as active status
            int inactiveColor = ContextCompat.getColor(this, android.R.color.holo_red_light);
            setRoundedBackground(statusText, inactiveColor);

            // Use solid blue button for inactive state
            android.graphics.drawable.GradientDrawable blueButtonDrawable = new android.graphics.drawable.GradientDrawable();
            blueButtonDrawable.setCornerRadius(30);
            blueButtonDrawable.setColor(ContextCompat.getColor(this, R.color.primary_blue));

            enableButton.setText("Enable Detection");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                enableButton.setBackground(blueButtonDrawable);
            } else {
                enableButton.setBackgroundDrawable(blueButtonDrawable);
            }

            enableButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        }

        statusText.setText(statusMessage);
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.white));

        // Force UI refresh
        statusText.invalidate();
        enableButton.invalidate();

        // Only update stats occasionally to avoid performance issues
        // updateStats() - removed to speed up status updates
    }

    // Helper method to create rounded background for the status text
    private void setRoundedBackground(TextView textView, int backgroundColor) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(30); // More moderate corner radius for modern look
        shape.setColor(backgroundColor);

        // Add a slight stroke for enhanced appearance
        shape.setStroke(2, Color.parseColor("#33FFFFFF"));

        // Apply padding to make it look better
        textView.setPadding(32, 24, 32, 24);

        // Add text shadow for better readability
        textView.setShadowLayer(3, 1, 1, Color.parseColor("#66000000"));

        // Set the background drawable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            textView.setBackground(shape);
        } else {
            textView.setBackgroundDrawable(shape);
        }
    }

    /**
     * Find security photos in all possible directories with comprehensive search
     */
    private File[] findAllSecurityPhotos() {
        // Check if we have a recent cache we can use
        long currentTime = System.currentTimeMillis();
        if (!cachedPhotosList.isEmpty() && (currentTime - lastPhotoSearchTime) < PHOTO_CACHE_DURATION) {
            // Don't log every time to reduce spam
            return cachedPhotosList.toArray(new File[0]);
        }

        // Reset cache
        cachedPhotosList.clear();
        lastPhotoSearchTime = currentTime;

        Log.d(TAG, "Starting comprehensive photo search...");

        // Create a set to track unique paths to avoid duplicates more efficiently
        Set<String> uniqueFilePaths = new HashSet<>();

        // 1. Check user-specific directory from UserManager
        File userDir = getUserPhotosDirectory(this);
        Log.d(TAG, "Checking user directory: " + userDir.getAbsolutePath());

        if (userDir.exists()) {
            File[] userPhotos = userDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
            if (userPhotos != null && userPhotos.length > 0) {
                Log.d(TAG, "Found " + userPhotos.length + " photos in user directory");
                for (File photo : userPhotos) {
                    if (uniqueFilePaths.add(photo.getAbsolutePath())) {
                        cachedPhotosList.add(photo);
                    }
                }
            } else {
                Log.d(TAG, "No photos found in user directory");
            }
        } else {
            Log.d(TAG, "User photos directory doesn't exist: " + userDir.getAbsolutePath());
            // Try to create it for future use
            boolean created = userDir.mkdirs();
            Log.d(TAG, "Created user directory: " + created);
        }

        // 2. Check the root security photos directory
        File rootDir = new File(getFilesDir(), "security_photos");
        Log.d(TAG, "Checking root directory: " + rootDir.getAbsolutePath());

        if (rootDir.exists()) {
            // Check for photos directly in root directory
            File[] rootPhotos = rootDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jpg") && new File(dir, name).isFile());

            if (rootPhotos != null && rootPhotos.length > 0) {
                Log.d(TAG, "Found " + rootPhotos.length + " photos in root directory");
                for (File photo : rootPhotos) {
                    if (uniqueFilePaths.add(photo.getAbsolutePath())) {
                        cachedPhotosList.add(photo);
                    }
                }
            }

            // 3. Check subdirectories in the security_photos directory
            File[] subdirs = rootDir.listFiles(File::isDirectory);
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    if (!subdir.equals(userDir)) { // Skip if it's the user directory we already checked
                        Log.d(TAG, "Checking subdirectory: " + subdir.getAbsolutePath());
                        File[] subdirPhotos = subdir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
                        if (subdirPhotos != null && subdirPhotos.length > 0) {
                            Log.d(TAG, "Found " + subdirPhotos.length + " photos in " + subdir.getName());
                            for (File photo : subdirPhotos) {
                                if (uniqueFilePaths.add(photo.getAbsolutePath())) {
                                    cachedPhotosList.add(photo);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "Root security photos directory doesn't exist, creating it");
            boolean created = rootDir.mkdirs();
            Log.d(TAG, "Created root directory: " + created);
        }

        // 4. Check files directory directly for any orphaned photos - only if cache is empty
        if (cachedPhotosList.isEmpty()) {
            File filesDir = getFilesDir();
            File[] orphanedPhotos = filesDir.listFiles((dir, name) ->
                name.toLowerCase().startsWith("security_") && name.toLowerCase().endsWith(".jpg"));
            if (orphanedPhotos != null && orphanedPhotos.length > 0) {
                Log.d(TAG, "Found " + orphanedPhotos.length + " orphaned photos in files directory");
                for (File photo : orphanedPhotos) {
                    if (uniqueFilePaths.add(photo.getAbsolutePath())) {
                        cachedPhotosList.add(photo);
                    }
                }
            }
        }

        // Log final results
        if (!cachedPhotosList.isEmpty()) {
            Log.d(TAG, "Found a total of " + cachedPhotosList.size() + " unique photos across all directories");
            // Limit logging to first photo only to prevent log spam
            File firstPhoto = cachedPhotosList.get(0);
            Log.d(TAG, "  - " + firstPhoto.getName() + " (" + (firstPhoto.length() / 1024) + " KB) in " + firstPhoto.getParent());

            if (cachedPhotosList.size() > 1) {
                Log.d(TAG, "  - ... and " + (cachedPhotosList.size() - 1) + " more photos");
            }
        } else {
            Log.d(TAG, "🔍 No photos found in any directory");
        }

        return cachedPhotosList.toArray(new File[0]);
    }

    private void updateStats() {
        // Check if views are initialized to prevent NullPointerException
        if (photosCountText == null || lastCaptureText == null) {
            Log.w(TAG, "updateStats called before views were initialized");
            return;
        }

        // Use background thread for file operations to avoid blocking UI
        new Thread(() -> {
            try {
                File[] allPhotos = findAllSecurityPhotos();

                runOnUiThread(() -> {
                    if (allPhotos != null && allPhotos.length > 0) {
                        photosCountText.setText(String.valueOf(allPhotos.length));

                        // Find most recent photo
                        File mostRecent = allPhotos[0];
                        for (File photo : allPhotos) {
                            if (photo.lastModified() > mostRecent.lastModified()) {
                                mostRecent = photo;
                            }
                        }

                        String lastCapture = new java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                                .format(new java.util.Date(mostRecent.lastModified()));
                        lastCaptureText.setText(lastCapture);

                    } else {
                        photosCountText.setText("0");
                        lastCaptureText.setText("Never");
                    }
                });

            } catch (Exception e) {
                Log.w(TAG, "Error updating stats", e);
                runOnUiThread(() -> {
                    photosCountText.setText("0");
                    lastCaptureText.setText("Never");
                });
            }
        }).start();
    }

    /**
     * Show cloud photos directly with fast loading feedback
     */
    private void showCloudBackupOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cloud features removed");
        builder.setMessage("Cloud storage and email alerts are disabled in free mode.\n\nYour photos are saved locally on this device.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }









    /**
     * Show download options with quantity selection
     */
    private void showDownloadOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📥 Download Cloud Photos");

        String message = "How many photos would you like to download?\n\n" +
                        "📁 Photos will be saved to your device gallery\n" +
                        "📍 Location: Gallery → LockSpectre album\n" +
                        "🔄 Latest photos downloaded first";

        builder.setMessage(message);

        if (currentCloudPhotoCount <= 5) {
            builder.setPositiveButton("All " + currentCloudPhotoCount + " Photos", (dialog, which) -> {
                startCloudPhotoDownload(currentCloudPhotoCount);
            });
        } else {
            builder.setPositiveButton("Latest 5 Photos", (dialog, which) -> {
                startCloudPhotoDownload(5);
            });

            builder.setNeutralButton("Latest 10 Photos", (dialog, which) -> {
                startCloudPhotoDownload(Math.min(10, currentCloudPhotoCount));
            });

            builder.setNegativeButton("All " + currentCloudPhotoCount + " Photos", (dialog, which) -> {
                startCloudPhotoDownload(currentCloudPhotoCount);
            });
        }

        builder.show();
    }

    /**
     * Start downloading cloud photos to external gallery
     */
    private void startCloudPhotoDownload(int photoCount) {
        Toast.makeText(this, "Cloud downloads are disabled in free mode.", Toast.LENGTH_SHORT).show();
    }

    /**
     * Download photos to external gallery (not intruder folder)
     */
    private void downloadPhotosToGallery(List<com.google.firebase.storage.StorageReference> photos, int count, AlertDialog progressDialog) {
        new Thread(() -> {
            try {
                // Create LockSpectre album in Pictures directory for gallery access
                File galleryDir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES), "LockSpectre");

                if (!galleryDir.exists()) {
                    boolean created = galleryDir.mkdirs();
                    Log.d(TAG, "Created LockSpectre gallery directory: " + created);
                }

                int downloaded = 0;
                int totalToDownload = Math.min(count, photos.size());

                for (int i = 0; i < totalToDownload; i++) {
                    com.google.firebase.storage.StorageReference photoRef = photos.get(i);
                    String fileName = photoRef.getName();

                    // Create unique filename to avoid conflicts
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                    String uniqueFileName = "cloud_" + timestamp + "_" + i + ".jpg";
                    File localFile = new File(galleryDir, uniqueFileName);

                    try {
                        // Download synchronously
                        com.google.android.gms.tasks.Tasks.await(photoRef.getFile(localFile));

                        // Add to gallery
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(android.net.Uri.fromFile(localFile));
                        sendBroadcast(mediaScanIntent);

                        downloaded++;

                        // Update progress on UI thread
                        final int currentCount = downloaded;
                        runOnUiThread(() -> {
                            if (progressDialog.isShowing()) {
                                progressDialog.setMessage("Downloaded " + currentCount + " of " + totalToDownload + " photos...\n\n" +
                                                         "📁 Saving to Gallery → LockSpectre\n" +
                                                         "🔄 Please wait...");
                            }
                        });

                        // Small delay to prevent overwhelming
                        Thread.sleep(300);

                    } catch (Exception e) {
                        Log.e(TAG, "Error downloading photo: " + fileName, e);
                    }
                }

                // Show completion on UI thread
                final int finalDownloaded = downloaded;
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }

                    if (finalDownloaded > 0) {
                        AlertDialog.Builder completionBuilder = new AlertDialog.Builder(this);
                        completionBuilder.setTitle("Download Complete!");
                        completionBuilder.setMessage("Successfully downloaded " + finalDownloaded + " photos!\n\n" +
                                                    "Location: Gallery → Albums → LockSpectre\n" +
                                                    "You can view them in your gallery app");

                        completionBuilder.setPositiveButton("Open Gallery", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setType("image/*");
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(this, "Please check your Gallery app", Toast.LENGTH_SHORT).show();
                            }
                        });

                        completionBuilder.setNegativeButton("OK", null);
                        completionBuilder.show();
                    } else {
                        Toast.makeText(this, "No photos were downloaded", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in download process", e);
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Copy login information for sharing with trusted people
     */
    private void copyLoginInfoForSharing() {
        String email = getCurrentUserEmail();
        String loginInfo = "LockSpectre Security App Access\n\n" +
                          "Login Email: " + email + "\n\n" +
                          "Steps to access photos:\n" +
                  "1. Download LockSpectre app\n" +
                          "2. Login with above email\n" +
                          "3. Go to Cloud Backup\n" +
                          "4. Download photos\n\n" +
                          "Use this if your phone is stolen!";

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("LockSpectre Access Info", loginInfo);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Login information copied!\nShare with trusted family member.", Toast.LENGTH_LONG).show();
    }

    /**
     * Start immediate backup after enabling
     */
    private void startImmediateBackup() {
        Toast.makeText(this, "Cloud backup is disabled. Photos stay on this device.", Toast.LENGTH_SHORT).show();
    }


    @Override
    protected void onResume() {
        super.onResume();

        isVisible = true;
        lastVisibilityChangeTime = System.currentTimeMillis();
        Log.d(TAG, "onResume called");

        // Update status when returning to activity to ensure real-time updates
        if (devicePolicyManager != null && adminComponent != null) {
            updateStatus();
        }

        // Only update stats if views are initialized - do it once on resume
        try {
            if (photosCountText != null && lastCaptureText != null) {
                // Delay stats update slightly to not block UI
                new Handler().postDelayed(this::updateStats, 500);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating stats in onResume", e);
        }


        // Register broadcast receiver for photo capture notifications
        registerPhotoCaptureReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();

        isVisible = false;
        lastVisibilityChangeTime = System.currentTimeMillis();
        Log.d(TAG, "onPause called");


        // Unregister broadcast receiver
        unregisterPhotoCaptureReceiver();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
    }

    // Check for visibility changes
    private void checkVisibilityChange() {
        boolean isViewVisible = isActivityVisible();
        if (isViewVisible != isVisible) {
            long currentTime = System.currentTimeMillis();
            // Log visibility change with timestamp
            Log.d(TAG, "Activity visibility changed: " + isVisible + " -> " + isViewVisible +
                  " (time since last change: " + (currentTime - lastVisibilityChangeTime) + "ms)");

            isVisible = isViewVisible;
            lastVisibilityChangeTime = currentTime;
        }
    }

    private boolean isActivityVisible() {
        return getWindow() != null &&
               getWindow().getDecorView() != null &&
               getWindow().getDecorView().isShown() &&
               getWindow().getDecorView().getWindowVisibility() == View.VISIBLE;
    }


    /**
     * Fetch cloud photos in background
     */
    private void fetchCloudPhotos() {
        Log.d(TAG, "Cloud photos disabled in free mode");
        currentCloudPhotoCount = 0;
    }

    /**
     * Delete all security photos with confirmation
     */
    private void deleteAllPhotos() {
        File[] photos = findAllSecurityPhotos();

        if (photos == null || photos.length == 0) {
            Toast.makeText(this, "No photos to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Delete All Photos")
            .setMessage("Are you sure you want to delete all " + photos.length + " intruder photos?\n\nThis action cannot be undone!")
            .setPositiveButton("Delete All", (dialog, which) -> {
                int deletedCount = 0;

                for (File photo : photos) {
                    try {
                        if (photo.exists() && photo.delete()) {
                            deletedCount++;
                            Log.d(TAG, "Deleted photo: " + photo.getName());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error deleting photo: " + photo.getName(), e);
                    }
                }

                // Clear photo cache
                cachedPhotosList.clear();
                lastPhotoSearchTime = 0;

                // Update stats
                updateStats();

                // Show result
                if (deletedCount > 0) {
                    Toast.makeText(this, "✅ Deleted " + deletedCount + " photos", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "❌ No photos were deleted", Toast.LENGTH_SHORT).show();
                }

                // Close the photos list dialog if it's open
                if (currentPhotosListDialog != null && currentPhotosListDialog.isShowing()) {
                    currentPhotosListDialog.dismiss();
                    currentPhotosListDialog = null;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Delete a single photo with confirmation
     */
    private void deletePhoto(File photoFile, Runnable onDeleted) {
        if (photoFile == null || !photoFile.exists()) {
            Toast.makeText(this, "Photo file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("🗑️ Delete Photo")
            .setMessage("Are you sure you want to delete this photo?\n\n" + photoFile.getName())
            .setPositiveButton("Delete", (dialog, which) -> {
                try {
                    if (photoFile.delete()) {
                        Log.d(TAG, "Deleted photo: " + photoFile.getName());
                        Toast.makeText(this, "✅ Photo deleted", Toast.LENGTH_SHORT).show();

                        // Clear photo cache
                        cachedPhotosList.clear();
                        lastPhotoSearchTime = 0;

                        // Update stats
                        updateStats();

                        // Call the callback if provided
                        if (onDeleted != null) {
                            onDeleted.run();
                        }
                    } else {
                        Toast.makeText(this, "❌ Failed to delete photo", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting photo: " + photoFile.getName(), e);
                    Toast.makeText(this, "❌ Error deleting photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Share a photo using system share intent
     */
    private void sharePhoto(File photoFile) {
        if (photoFile == null || !photoFile.exists()) {
            Toast.makeText(this, "Photo file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create a content URI for the photo using FileProvider
            android.net.Uri photoUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".fileprovider",
                photoFile
            );

            // Create share intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/jpeg");
            shareIntent.putExtra(Intent.EXTRA_STREAM, photoUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Intruder Photo");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Intruder photo captured by LockSpectre");

            // Grant temporary read permission
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Create chooser
            Intent chooser = Intent.createChooser(shareIntent, "Share Intruder Photo");

            // Check if there are apps that can handle this intent
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
                Log.d(TAG, "Photo share intent launched for: " + photoFile.getName());
            } else {
                Toast.makeText(this, "No apps available to share photos", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error sharing photo: " + photoFile.getName(), e);
            Toast.makeText(this, "❌ Error sharing photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * View security photos in a dialog with options
     */
    private void viewSecurityPhotos() {
        File[] photos = findAllSecurityPhotos();

        if (photos == null || photos.length == 0) {
            Toast.makeText(this, "No intruder photos found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort photos by modification date (newest first)
        java.util.Arrays.sort(photos, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Intruder Photos (" + photos.length + ")");

        // Create a custom view with image previews
        LinearLayout photoListContainer = new LinearLayout(this);
        photoListContainer.setOrientation(LinearLayout.VERTICAL);
        photoListContainer.setPadding(16, 16, 16, 16);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(photoListContainer);

        // Add each photo as a preview item
        for (int i = 0; i < Math.min(photos.length, 20); i++) { // Limit to 20 for performance
            File photo = photos[i];
            LinearLayout photoItem = createPhotoPreviewItem(photo, i);
            photoItem.setOnClickListener(v -> {
                builder.create().dismiss();
                showPhotoDialog(photo);
            });
            photoListContainer.addView(photoItem);
        }

        // If there are more than 20 photos, add a note
        if (photos.length > 20) {
            TextView moreText = new TextView(this);
            moreText.setText("... and " + (photos.length - 20) + " more photos");
            moreText.setTextColor(getResources().getColor(R.color.bw_secondary_text, null));
            moreText.setPadding(16, 8, 16, 8);
            moreText.setGravity(android.view.Gravity.CENTER);
            photoListContainer.addView(moreText);
        }

        builder.setView(scrollView);

        // Add management buttons
        builder.setPositiveButton("Stats", (dialog, which) -> {
            showPhotoStatistics(photos);
        });

        builder.setNeutralButton("Delete All", (dialog, which) -> deleteAllPhotos());

        builder.setNegativeButton("Close", null);

        currentPhotosListDialog = builder.create();
        currentPhotosListDialog.show();
    }

    /**
     * Create a photo preview item with thumbnail and details
     */
    private LinearLayout createPhotoPreviewItem(File photoFile, int index) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(8, 8, 8, 8);
        itemLayout.setBackgroundColor(getResources().getColor(R.color.bw_surface, null));

        // Add margin between items
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, 8);
        itemLayout.setLayoutParams(layoutParams);

        // Add ripple effect
        itemLayout.setBackground(ContextCompat.getDrawable(this, android.R.drawable.list_selector_background));
        itemLayout.setClickable(true);
        itemLayout.setFocusable(true);

        try {
            // Create thumbnail
            ImageView thumbnail = new ImageView(this);
            LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(80, 80);
            thumbParams.setMargins(0, 0, 16, 0);
            thumbnail.setLayoutParams(thumbParams);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);

            // Load thumbnail bitmap
            Bitmap thumbnailBitmap = createThumbnail(photoFile, 80);
            if (thumbnailBitmap != null) {
                thumbnail.setImageBitmap(thumbnailBitmap);
            } else {
                // Fallback to default image icon
                thumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
                thumbnail.setColorFilter(getResources().getColor(R.color.bw_accent, null));
            }

            itemLayout.addView(thumbnail);

        } catch (Exception e) {
            Log.e(TAG, "Error creating thumbnail for " + photoFile.getName(), e);
            // Add placeholder icon
            ImageView placeholder = new ImageView(this);
            LinearLayout.LayoutParams placeholderParams = new LinearLayout.LayoutParams(80, 80);
            placeholderParams.setMargins(0, 0, 16, 0);
            placeholder.setLayoutParams(placeholderParams);
            placeholder.setImageResource(android.R.drawable.ic_menu_gallery);
            placeholder.setColorFilter(getResources().getColor(R.color.bw_accent, null));
            itemLayout.addView(placeholder);
        }

        // Create text container
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setLayoutParams(new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        // Photo name
        TextView nameText = new TextView(this);
        nameText.setText(photoFile.getName());
        nameText.setTextSize(16);
        nameText.setTextColor(getResources().getColor(R.color.bw_primary_text, null));
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setMaxLines(1);
        nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textContainer.addView(nameText);

        // Date and size
        String date = new java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
            .format(new java.util.Date(photoFile.lastModified()));
        long sizeKB = photoFile.length() / 1024;

        TextView detailsText = new TextView(this);
        detailsText.setText(date + " • " + sizeKB + " KB");
        detailsText.setTextSize(14);
        detailsText.setTextColor(getResources().getColor(R.color.bw_secondary_text, null));
        textContainer.addView(detailsText);

        itemLayout.addView(textContainer);

        return itemLayout;
    }

    /**
     * Create a thumbnail bitmap from photo file with correct orientation
     */
    private Bitmap createThumbnail(File photoFile, int size) {
        try {
            // First, decode with inSampleSize to reduce memory usage
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, size, size);
            options.inJustDecodeBounds = false;

            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);
            if (bitmap == null) return null;

            // Apply rotation correction based on EXIF data
            bitmap = applyRotationCorrection(bitmap, photoFile);

            // Scale to exact thumbnail size
            Bitmap thumbnail = Bitmap.createScaledBitmap(bitmap, size, size, true);

            // Recycle original if different
            if (thumbnail != bitmap) {
                bitmap.recycle();
            }

            return thumbnail;
        } catch (Exception e) {
            Log.e(TAG, "Error creating thumbnail for " + photoFile.getName(), e);
            return null;
        }
    }

    /**
     * Apply rotation correction to bitmap based on EXIF data
     */
    private Bitmap applyRotationCorrection(Bitmap originalBitmap, File photoFile) {
        try {
            // Read EXIF data to get orientation
            androidx.exifinterface.media.ExifInterface exifInterface =
                new androidx.exifinterface.media.ExifInterface(photoFile.getAbsolutePath());

            int orientation = exifInterface.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

            // Calculate rotation angle based on EXIF orientation
            int rotationAngle = 0;
            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotationAngle = 0;
                    break;
            }

            // If no rotation needed, return original bitmap
            if (rotationAngle == 0) {
                return originalBitmap;
            }

            // Create rotation matrix
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationAngle);

            // Apply rotation
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.getWidth(), originalBitmap.getHeight(),
                matrix, true);

            // Recycle original bitmap to free memory
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle();
            }

            return rotatedBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error applying rotation correction", e);
            return originalBitmap; // Return original on error
        }
    }

    /**
     * Calculate sample size for bitmap loading
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     * Show detailed photo statistics
     */
    private void showPhotoStatistics(File[] photos) {
        if (photos == null || photos.length == 0) {
            Toast.makeText(this, "No photos to analyze", Toast.LENGTH_SHORT).show();
            return;
        }

        long totalSize = 0;
        File oldestPhoto = photos[0];
        File newestPhoto = photos[0];

        for (File photo : photos) {
            totalSize += photo.length();
            if (photo.lastModified() < oldestPhoto.lastModified()) {
                oldestPhoto = photo;
            }
            if (photo.lastModified() > newestPhoto.lastModified()) {
                newestPhoto = photo;
            }
        }

        String oldestDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(new java.util.Date(oldestPhoto.lastModified()));
        String newestDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(new java.util.Date(newestPhoto.lastModified()));

        long totalSizeKB = totalSize / 1024;
        double averageSizeKB = photos.length > 0 ? (double)totalSizeKB / photos.length : 0;

        StringBuilder stats = new StringBuilder();
        stats.append("PHOTO STATISTICS\n\n");
        stats.append("Total Photos: ").append(photos.length).append("\n");
        stats.append("Total Size: ").append(totalSizeKB).append(" KB\n");
        stats.append("Average Size: ").append(String.format("%.1f", averageSizeKB)).append(" KB\n\n");
        stats.append("Oldest Photo: ").append(oldestDate).append("\n");
        stats.append("Newest Photo: ").append(newestDate).append("\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Photo Statistics");
        builder.setMessage(stats.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    /**
     * Show individual photo in a dialog with options - FIXED: Handle image rotation properly
     */
    private void showPhotoDialog(File photoFile) {
        try {
            // Load and display the photo with proper orientation
            Bitmap bitmap = loadBitmapWithCorrectOrientation(photoFile);
            if (bitmap == null) {
                Toast.makeText(this, "Failed to load photo", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create ImageView
            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // Create scrollable container
            ScrollView scrollView = new ScrollView(this);
            scrollView.addView(imageView);

            // Get photo info
            long sizeKB = photoFile.length() / 1024;
            String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(photoFile.lastModified()));

            String title = photoFile.getName() + "\n" + sizeKB + " KB - " + date;

            AlertDialog photoDialog = new AlertDialog.Builder(this)
                .setTitle("Intruder Photo")
                .setMessage(title)
                .setView(scrollView)
                .setPositiveButton("Delete", (dialog, which) -> {
                    deletePhoto(photoFile, () -> {
                        // Refresh the photos list if it's still showing
                        if (currentPhotosListDialog != null && currentPhotosListDialog.isShowing()) {
                            currentPhotosListDialog.dismiss();
                            viewSecurityPhotos(); // Reopen with updated list
                        }
                    });
                })
                .setNeutralButton("Share", (dialog, which) -> {
                    sharePhoto(photoFile);
                })
                .setNegativeButton("Close", null)
                .create();

            photoDialog.show();

            // Adjust dialog size
            photoDialog.getWindow().setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );

        } catch (Exception e) {
            Toast.makeText(this, "Error loading photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error showing photo dialog", e);
        }
    }

    /**
     * Load bitmap with correct orientation based on EXIF data
     */
    private Bitmap loadBitmapWithCorrectOrientation(File photoFile) {
        try {
            // First, decode the image
            Bitmap originalBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (originalBitmap == null) {
                return null;
            }

            // Read EXIF data to get orientation
            androidx.exifinterface.media.ExifInterface exifInterface =
                new androidx.exifinterface.media.ExifInterface(photoFile.getAbsolutePath());

            int orientation = exifInterface.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

            // Calculate rotation angle based on EXIF orientation
            int rotationAngle = 0;
            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotationAngle = 0;
                    break;
            }


            // If no rotation needed, return original bitmap
            if (rotationAngle == 0) {
                return originalBitmap;
            }

            // Create rotation matrix
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationAngle);

            // Apply rotation
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.getWidth(), originalBitmap.getHeight(),
                matrix, true);

            // Recycle original bitmap to free memory
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle();
            }

            Log.d(TAG, "Image rotated " + rotationAngle + " degrees for display");
            return rotatedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap with correct orientation", e);

            // Fallback: try to load without rotation
            try {
                return BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            } catch (Exception fallbackException) {
                Log.e(TAG, "Fallback bitmap loading also failed", fallbackException);
                return null;
            }
        }
    }

    /**
     * Get current user email for display and sharing
     */
    private String getCurrentUserEmail() {
        // Try Firebase Auth first (if available)
        if (mAuth != null) {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null && currentUser.getEmail() != null) {
                return currentUser.getEmail();
            }
        }

        // Fallback to SharedPreferences
        SharedPreferences prefs = getSharedPreferences("security_app", MODE_PRIVATE);
        String email = prefs.getString("user_email", "");

        if (email != null && !email.isEmpty()) {
            return email;
        }

        return "No email available";
    }

    /**
     * Show account information with username editing functionality
     */
    private void showAccountInfo() {
        String userEmail = getCurrentUserEmail();
        String userName = "User";

        // Try to get username from Firebase if available
        if (mAuth != null) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getDisplayName() != null) {
                userName = user.getDisplayName();
            }
        }

        // Fallback to SharedPreferences
        if ("User".equals(userName)) {
            SharedPreferences prefs = getSharedPreferences("security_app", MODE_PRIVATE);
            userName = prefs.getString("user_name", "User");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("👤 Account Information");

        StringBuilder accountInfo = new StringBuilder();
        accountInfo.append("Name: ").append(userName).append("\n\n");
        accountInfo.append("Email: ").append(userEmail).append("\n\n");

        // Add service status
        if (mAuth == null || db == null) {
            accountInfo.append("Status: Limited mode (Cloud features disabled)\n");
        } else {
            accountInfo.append("Status: Full access\n");
        }

        builder.setMessage(accountInfo.toString());

        // Always show edit button (works with SharedPreferences fallback)
        builder.setPositiveButton("✏️ Edit Name", (dialog, which) -> {
            showEditUsernameDialog();
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    /**
     * Show dialog to edit username
     */
    private void showEditUsernameDialog() {
        // Get current username
        String currentUserName = "User";

        // Try to get from Firebase first
        if (mAuth != null) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getDisplayName() != null) {
                currentUserName = user.getDisplayName();
            }
        }

        // Fallback to SharedPreferences
        if ("User".equals(currentUserName)) {
            SharedPreferences prefs = getSharedPreferences("security_app", MODE_PRIVATE);
            currentUserName = prefs.getString("user_name", "User");
        }

        // Create input field
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(currentUserName);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        input.setHint("Enter your name");

        // Set padding for better appearance
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ Edit Name");
        builder.setMessage("Enter your preferred display name:");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newUserName = input.getText().toString().trim();

            if (newUserName.isEmpty()) {
                Toast.makeText(this, "❌ Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newUserName.length() > 50) {
                Toast.makeText(this, "❌ Name too long (max 50 characters)", Toast.LENGTH_SHORT).show();
                return;
            }

            updateUserName(newUserName);
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Focus the input and show keyboard
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    /**
     * Update username in both Firebase and SharedPreferences
     */
    private void updateUserName(String newUserName) {
        try {
            // Save to SharedPreferences first (always works)
            SharedPreferences prefs = getSharedPreferences("security_app", MODE_PRIVATE);
            prefs.edit().putString("user_name", newUserName).apply();

            // Also save to get_intruder preferences for compatibility
            SharedPreferences getIntruderPrefs = getSharedPreferences("get_intruder", MODE_PRIVATE);
            getIntruderPrefs.edit().putString("user_name", newUserName).apply();

            Log.d(TAG, "Username updated in SharedPreferences: " + newUserName);

            // Update Firebase profile if available
            if (mAuth != null) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(newUserName)
                        .build();

                    user.updateProfile(profileUpdates)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Firebase profile updated successfully: " + newUserName);
                            Toast.makeText(this, "✅ Name updated: " + newUserName, Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "❌ Failed to update Firebase profile", e);
                            // Still show success since SharedPreferences update worked
                            Toast.makeText(this, "✅ Name updated: " + newUserName + " (local only)", Toast.LENGTH_LONG).show();
                        });
                } else {
                    Toast.makeText(this, "✅ Name updated: " + newUserName, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "✅ Name updated: " + newUserName, Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating username", e);
            Toast.makeText(this, "❌ Error updating name: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Confirm sign out with dialog
     */
    private void confirmSignOut() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚪 Sign Out");
        builder.setMessage("Are you sure you want to sign out?\n\nThis will clear all local data and return you to the login screen.");

        builder.setPositiveButton("Yes, Sign Out", (dialog, which) -> {
            performSignOut();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Perform sign out operation
     */
    private void performSignOut() {
        try {
            // Sign out from Firebase if available
            if (mAuth != null) {
                mAuth.signOut();
            }

            // Clear local data
            SharedPreferences prefs = getSharedPreferences("security_app", MODE_PRIVATE);
            prefs.edit().clear().apply();

            SharedPreferences getIntruderPrefs = getSharedPreferences("get_intruder", MODE_PRIVATE);
            getIntruderPrefs.edit().clear().apply();

            // Show toast
            Toast.makeText(this, "👋 Signed out successfully", Toast.LENGTH_SHORT).show();

            // Redirect to auth activity
            Intent intent = new Intent(this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "Error signing out", e);
            Toast.makeText(this, "Error signing out: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Register broadcast receiver to listen for photo captures
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerPhotoCaptureReceiver() {
        if (photoCaptureReceiver == null) {
            photoCaptureReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("com.ansh.lockspectre.PHOTO_CAPTURED".equals(intent.getAction())) {
                        String photoFileName = intent.getStringExtra("photo_filename");
                        Log.d(TAG, "📸 Received photo capture broadcast: " + photoFileName);

                        // Update stats immediately
                        updateStats();

                        // Show brief notification that photo was captured and email sent
                        Toast.makeText(MainActivity.this,
                            "📸 Intruder photo captured\n📧 Email notification sent",
                            Toast.LENGTH_LONG).show();
                    }
                }
            };
        }

        IntentFilter filter = new IntentFilter("com.ansh.lockspectre.PHOTO_CAPTURED");

        // Register with proper flags for Android API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            registerReceiver(photoCaptureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
            registerReceiver(photoCaptureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            // For older Android versions
            registerReceiver(photoCaptureReceiver, filter);
        }

        Log.d(TAG, "Photo capture broadcast receiver registered with proper flags");
    }

    /**
     * Unregister broadcast receiver
     */
    private void unregisterPhotoCaptureReceiver() {
        if (photoCaptureReceiver != null) {
            try {
                unregisterReceiver(photoCaptureReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering photo capture receiver", e);
            }
        }
    }

    /**
     * Show cloud photos list for admin view
     */
    private void showCloudPhotosListForAdmin() {
        Toast.makeText(this, "Cloud photos are disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Show options for a selected cloud photo
     */
    private void showCloudPhotoOptions(Object photoRef) {
        Toast.makeText(this, "Cloud photo actions are disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Download a single cloud photo
     */
    private void downloadSingleCloudPhoto(Object photoRef) {
        Toast.makeText(this, "Cloud downloads are disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Confirm deletion of cloud photo
     */
    private void confirmDeleteCloudPhoto(Object photoRef) {
        Toast.makeText(this, "Cloud deletes are disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Delete cloud photo
     */
    private void deleteCloudPhoto(Object photoRef) {
        Toast.makeText(this, "Cloud deletes are disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Show cloud photo information
     */
    private void showCloudPhotoInfo(Object photoRef) {
        Toast.makeText(this, "Cloud photo info is disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Fetch and show cloud photos directly - optimized for speed
     */
    private void fetchAndShowCloudPhotos() {
        Toast.makeText(this, "Cloud photos are disabled in free mode", Toast.LENGTH_SHORT).show();
    }

    /**
     * Show dialog when no cloud photos are found
     */
    private void showNoCloudPhotosDialog(String errorMessage) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cloud features removed");
        builder.setMessage("Cloud storage and cloud downloads are disabled in free mode.");
        builder.setPositiveButton("OK", null);

        builder.show();
    }

    /**
     * Show dialog with cloud photos statistics and download option
     */
    private void showCloudPhotosDialog(List<?> photos) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cloud features removed");
        builder.setMessage("Cloud storage and cloud downloads are disabled in free mode.");

        builder.setPositiveButton("OK", null);

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    /**
     * Show dialog when no cloud photos are available
     */
    private void showNoCloudPhotosDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cloud features removed");
        builder.setMessage("Cloud storage and cloud downloads are disabled in free mode.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
