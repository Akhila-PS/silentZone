package com.example.silentzone;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.room.Room;

import com.google.android.gms.location.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    // Core functionality components (preserved from original)
    private double targetLat;
    private double targetLon;
    private static final int LOCATION_PERMISSION_CODE = 1;
    private static final int BACKGROUND_LOCATION_PERMISSION_CODE = 2;
    private static final int MAP_REQUEST_CODE = 100;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity onCreate started");

        // Initialize database
        db = Room.databaseBuilder(getApplicationContext(),
                        AppDatabase.class, "silentzone-db")
                .allowMainThreadQueries()
                .build();

        // Initialize core functionality
        initializeCoreFunctionality();
        
        // Load HomeFragment
        loadFragment(new HomeFragment());
        
        // Check and request permissions
        checkAndRequestPermissions();
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    private void initializeCoreFunctionality() {
        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);
        
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    float[] result = new float[1];
                    Location.distanceBetween(lat, lon, targetLat, targetLon, result);

                    if (result[0] < 100) {
                        setPhoneToSilentMode();
                        Log.d(TAG, "Inside silent zone - distance: " + result[0] + "m");
                    } else {
                        setPhoneToNormalMode();
                        Log.d(TAG, "Outside silent zone - distance: " + result[0] + "m");
                    }
                }
            }
        };
        
        // Load saved location
        loadSavedLocation();
    }

    // Core functionality methods (preserved from original)
    private void loadSavedLocation() {
        SharedPreferences prefs = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE);
        targetLat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(0.0)));
        targetLon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(0.0)));
    }

    public void setCurrentLocationAsSilentZone() {
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        targetLat = location.getLatitude();
                        targetLon = location.getLongitude();
                        saveZoneWithName(targetLat, targetLon, "Current Location");
                        Log.d(TAG, "Set silent zone to current location: " + targetLat + ", " + targetLon);

                        SilentZone zone = new SilentZone(targetLat, targetLon, "Current Location");
                        db.silentZoneDao().insert(zone);
                        
                        restartLocationService();
                        showSuccessSnackbar("Silent zone set to current location");
                    } else {
                        Log.e(TAG, "Could not get current location");
                        showErrorSnackbar("Could not get current location");
                    }
                });
    }

    public void quickSilentToggle() {
        if (!hasDndAccess()) {
            showErrorSnackbar("No DND access - please grant in settings");
            checkDndAccess();
            return;
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            showErrorSnackbar("Error: AudioManager is null");
            return;
        }

        try {
            int currentMode = audioManager.getRingerMode();
            if (currentMode == AudioManager.RINGER_MODE_SILENT) {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                showSuccessSnackbar("Quick: Phone set to NORMAL");
            } else {
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                showSuccessSnackbar("Quick: Phone set to SILENT");
            }
        } catch (SecurityException e) {
            showErrorSnackbar("Error: Cannot change ringer mode");
        }
    }

    public void showStatusInfo() {
        SharedPreferences statusPrefs = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE);
        double savedLat = Double.longBitsToDouble(statusPrefs.getLong("lat", Double.doubleToLongBits(0)));
        double savedLon = Double.longBitsToDouble(statusPrefs.getLong("lon", Double.doubleToLongBits(0)));
        
        if (savedLat == 0 && savedLon == 0) {
            showInfoSnackbar("No silent zone location set");
        } else {
            showInfoSnackbar("Silent zone set at: " + savedLat + ", " + savedLon);
        }
    }

    public void checkCurrentStatus() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            int currentMode = audioManager.getRingerMode();
            String modeText = "";
            switch (currentMode) {
                case AudioManager.RINGER_MODE_SILENT:
                    modeText = "SILENT";
                    break;
                case AudioManager.RINGER_MODE_NORMAL:
                    modeText = "NORMAL";
                    break;
                case AudioManager.RINGER_MODE_VIBRATE:
                    modeText = "VIBRATE";
                    break;
                default:
                    modeText = "UNKNOWN";
                    break;
            }
            Log.d(TAG, "Current ringer mode: " + modeText + " (" + currentMode + ")");
            showInfoSnackbar("Current mode: " + modeText);
        }
    }

    private void saveZone(double lat, double lon) {
        SharedPreferences.Editor editor = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE).edit();
        editor.putLong("lat", Double.doubleToRawLongBits(lat));
        editor.putLong("lon", Double.doubleToRawLongBits(lon));
        editor.apply();
        Log.d(TAG, "Zone saved: " + lat + ", " + lon);
    }

    private void saveZoneWithName(double lat, double lon, String name) {
        SharedPreferences.Editor editor = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE).edit();
        editor.putLong("lat", Double.doubleToRawLongBits(lat));
        editor.putLong("lon", Double.doubleToRawLongBits(lon));
        editor.putString("locationName", name);
        editor.apply();
        Log.d(TAG, "Zone saved with name: " + lat + ", " + lon + " - " + name);
    }

    public String getCurrentLocationName() {
        SharedPreferences prefs = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE);
        return prefs.getString("locationName", "");
    }

    public String getLocationStatus() {
        if (targetLat == 0 && targetLon == 0) {
            return "No silent zone configured";
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Location permission needed";
        }
        
        try {
            fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double currentLat = location.getLatitude();
                        double currentLon = location.getLongitude();
                        
                        float[] result = new float[1];
                        Location.distanceBetween(currentLat, currentLon, targetLat, targetLon, result);
                        
                        // Update status in a way that can be accessed by fragments
                        SharedPreferences.Editor editor = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE).edit();
                        if (result[0] < 100) {
                            editor.putString("currentStatus", "INSIDE");
                            editor.putFloat("distance", result[0]);
                        } else {
                            editor.putString("currentStatus", "OUTSIDE");
                            editor.putFloat("distance", result[0]);
                        }
                        editor.apply();
                    }
                });
        } catch (SecurityException e) {
            return "Location access denied";
        }
        
        // Return current status from preferences
        SharedPreferences prefs = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE);
        String status = prefs.getString("currentStatus", "UNKNOWN");
        float distance = prefs.getFloat("distance", 0f);
        
        if (status.equals("INSIDE")) {
            return String.format("INSIDE silent zone (%.1fm away)", distance);
        } else if (status.equals("OUTSIDE")) {
            return String.format("OUTSIDE silent zone (%.1fm away)", distance);
        }
        
        return "Checking location...";
    }

    public void setMapSelectedLocation(double lat, double lon) {
        targetLat = lat;
        targetLon = lon;
        saveZoneWithName(lat, lon, "Map Selected Location");
        Log.d(TAG, "Set silent zone from map: " + lat + ", " + lon);

        SilentZone zone = new SilentZone(lat, lon, "Map Selected Location");
        db.silentZoneDao().insert(zone);
        
        restartLocationService();
        showSuccessSnackbar("Silent zone set from map");
    }

    public void clearSilentZone() {
        targetLat = 0.0;
        targetLon = 0.0;
        
        SharedPreferences.Editor editor = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE).edit();
        editor.putLong("lat", Double.doubleToRawLongBits(0.0));
        editor.putLong("lon", Double.doubleToRawLongBits(0.0));
        editor.putString("locationName", "");
        editor.apply();
        
        // Clear all zones from database
        db.silentZoneDao().deleteAll();
        
        // Stop location service since no zone is set
        stopService(new Intent(this, LocationService.class));
        
        Log.d(TAG, "Silent zone cleared");
        showSuccessSnackbar("Silent zone cleared");
    }

    private void restartLocationService() {
        stopService(new Intent(this, LocationService.class));
        startService(new Intent(this, LocationService.class));
        Log.d(TAG, "LocationService restarted");
    }

    private void startLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.d(TAG, "LocationService started");
    }

    public void checkDndAccess() {
        if (!hasDndAccess()) {
            Log.w(TAG, "No DND access - requesting permission");
            showErrorSnackbar("Please grant DND access in settings");
            startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } else {
            Log.d(TAG, "DND access granted");
            showSuccessSnackbar("DND access granted");
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
        Log.d(TAG, "Location updates started");
    }

    private boolean hasDndAccess() {
        NotificationManager n = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return n != null && n.isNotificationPolicyAccessGranted();
    }

    private void setPhoneToSilentMode() {
        if (!hasDndAccess()) {
            Log.w(TAG, "Cannot set silent mode - no DND access");
            return;
        }
        
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Log.d(TAG, "Phone set to silent mode");
        }
    }

    private void setPhoneToNormalMode() {
        if (!hasDndAccess()) {
            Log.w(TAG, "Cannot set normal mode - no DND access");
            return;
        }
        
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            Log.d(TAG, "Phone set to normal mode");
        }
    }

    public void checkAndRequestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    BACKGROUND_LOCATION_PERMISSION_CODE);
        } else {
            startLocationUpdates();
            startLocationService();
            checkDndAccess();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted");
                showSuccessSnackbar("Location permission granted");
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            BACKGROUND_LOCATION_PERMISSION_CODE);
                } else {
                    startLocationUpdates();
                    startLocationService();
                    checkDndAccess();
                }
            } else {
                Log.e(TAG, "Location permission denied");
                showErrorSnackbar("Location permission denied");
            }
        } else if (requestCode == BACKGROUND_LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Background location permission granted");
                showSuccessSnackbar("Background location permission granted");
                startLocationUpdates();
                startLocationService();
                checkDndAccess();
            } else {
                Log.e(TAG, "Background location permission denied");
                showErrorSnackbar("Background location permission is required for the app to work properly");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MAP_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            targetLat = data.getDoubleExtra("lat", 0.0);
            targetLon = data.getDoubleExtra("lon", 0.0);
            saveZoneWithName(targetLat, targetLon, "Map Selected Location");
            Log.d(TAG, "Set silent zone from map: " + targetLat + ", " + targetLon);

            SilentZone zone = new SilentZone(targetLat, targetLon, "Map Selected Location");
            db.silentZoneDao().insert(zone);
            
            restartLocationService();
            showSuccessSnackbar("Silent zone set from map");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // Snackbar methods
    public void showSuccessSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getResources().getColor(R.color.success))
                .show();
    }

    public void showErrorSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getResources().getColor(R.color.error))
                .show();
    }

    public void showInfoSnackbar(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getResources().getColor(R.color.info))
                .show();
    }

    // Getters for fragments
    public double getTargetLat() { return targetLat; }
    public double getTargetLon() { return targetLon; }
    public AppDatabase getDatabase() { return db; }
}
