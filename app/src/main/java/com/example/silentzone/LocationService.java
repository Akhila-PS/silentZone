package com.example.silentzone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class LocationService extends Service {

    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "SilentZoneService";
    private static final int NOTIFICATION_ID = 1;
    private static final float SILENT_ZONE_RADIUS = 100; // meters
    
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean isInSilentZone = false;
    private boolean isServiceRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "LocationService onCreate");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "LocationService onStartCommand");
        
        if (!isServiceRunning) {
            startForeground(NOTIFICATION_ID, createNotification());
            isServiceRunning = true;
        }
        
        startLocationMonitoring();
        
        return START_STICKY;
    }

    private void startLocationMonitoring() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        
        if (locationManager == null) {
            Log.e(TAG, "LocationManager is null");
            return;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) {
                    Log.w(TAG, "Location is null");
                    return;
                }
                
                Log.d(TAG, "Location update: " + location.getLatitude() + ", " + location.getLongitude());
                checkAndUpdateSilentMode(location);
            }

            @Override public void onStatusChanged(String s, int i, Bundle bundle) {}
            @Override public void onProviderEnabled(String s) {}
            @Override public void onProviderDisabled(String s) {}
        };

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Request updates from both GPS and Network providers
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
                Log.d(TAG, "Location updates started successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception when requesting location updates", e);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Illegal argument when requesting location updates", e);
            }
        } else {
            Log.e(TAG, "Location permission not granted");
        }
    }

    private void checkAndUpdateSilentMode(Location currentLocation) {
        SharedPreferences prefs = getSharedPreferences("SilentZonePrefs", MODE_PRIVATE);
        double savedLat = Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(0)));
        double savedLon = Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(0)));

        // Check if we have a valid target location
        if (savedLat == 0 && savedLon == 0) {
            Log.d(TAG, "No silent zone location set");
            return;
        }

        Location silentZone = new Location("");
        silentZone.setLatitude(savedLat);
        silentZone.setLongitude(savedLon);

        float distance = currentLocation.distanceTo(silentZone);
        Log.d(TAG, "Distance to silent zone: " + distance + " meters");

        // Check if we have DND access
        if (!hasDndAccess()) {
            Log.w(TAG, "No DND access granted - cannot change ringer mode");
            return;
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Log.e(TAG, "AudioManager is null");
            return;
        }

        if (distance <= SILENT_ZONE_RADIUS) {
            if (!isInSilentZone) {
                Log.d(TAG, "Entering silent zone - setting phone to silent");
                try {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    isInSilentZone = true;
                    Log.d(TAG, "Successfully set phone to silent mode");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception when setting silent mode", e);
                }
            }
        } else {
            if (isInSilentZone) {
                Log.d(TAG, "Leaving silent zone - setting phone to normal");
                try {
                    audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    isInSilentZone = false;
                    Log.d(TAG, "Successfully set phone to normal mode");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception when setting normal mode", e);
                }
            }
        }
    }

    private boolean hasDndAccess() {
        NotificationManager n = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean hasAccess = n != null && n.isNotificationPolicyAccessGranted();
        Log.d(TAG, "DND access check: " + hasAccess);
        return hasAccess;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SilentZone Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("SilentZone location monitoring service");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SilentZone Active")
                .setContentText("Monitoring your location for silent zones")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "LocationService onDestroy");
        
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
                Log.d(TAG, "Location updates removed");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception when removing location updates", e);
            }
        }
        
        isServiceRunning = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
