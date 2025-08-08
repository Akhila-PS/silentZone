package com.example.silentzone;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeFragment extends Fragment {

    private TextView locationText;
    private MaterialButton buttonSetZone;
    private MaterialButton buttonPickFromMap;
    private MaterialButton buttonClearLocation;
    private FloatingActionButton fabQuickSilent;
    private Handler updateHandler;
    private Runnable updateRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupClickListeners();
        setupPeriodicUpdates();
        updateLocationText();
    }

    private void initializeViews(View view) {
        locationText = view.findViewById(R.id.locationText);
        buttonSetZone = view.findViewById(R.id.buttonSetZone);
        buttonPickFromMap = view.findViewById(R.id.buttonPickFromMap);
        buttonClearLocation = view.findViewById(R.id.buttonClearLocation);
        fabQuickSilent = view.findViewById(R.id.fabQuickSilent);
    }

    private void setupClickListeners() {
        buttonSetZone.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.setCurrentLocationAsSilentZone();
            }
        });

        buttonPickFromMap.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MapActivity.class);
            startActivityForResult(intent, 100);
        });

        buttonClearLocation.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.clearSilentZone();
                updateLocationText();
            }
        });

        fabQuickSilent.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.quickSilentToggle();
            }
        });
    }

    private void updateLocationText() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            double lat = activity.getTargetLat();
            double lon = activity.getTargetLon();
            String locationName = activity.getCurrentLocationName();
            String locationStatus = activity.getLocationStatus();
            
            if (lat == 0 && lon == 0) {
                locationText.setText("No silent zone set\n\nAdd a location using the buttons below to enable automatic silent mode when you enter the zone.");
                buttonClearLocation.setVisibility(View.GONE);
            } else {
                StringBuilder statusText = new StringBuilder();
                
                // Show current zone info
                if (locationName != null && !locationName.isEmpty()) {
                    statusText.append("📍 Active Silent Zone:\n");
                    statusText.append(locationName).append("\n");
                    statusText.append(String.format("(%.6f, %.6f)", lat, lon)).append("\n\n");
                } else {
                    statusText.append("📍 Silent Zone Location:\n");
                    statusText.append(String.format("Lat: %.6f, Lon: %.6f", lat, lon)).append("\n\n");
                }
                
                // Show current status
                statusText.append("📱 Current Status:\n");
                statusText.append(locationStatus);
                
                locationText.setText(statusText.toString());
                buttonClearLocation.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == getActivity().RESULT_OK && data != null) {
            double lat = data.getDoubleExtra("lat", 0.0);
            double lon = data.getDoubleExtra("lon", 0.0);
            
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.setMapSelectedLocation(lat, lon);
                updateLocationText(); // Refresh the display
            }
        }
    }

    private void setupPeriodicUpdates() {
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getActivity() != null) {
                    updateLocationText();
                    updateHandler.postDelayed(this, 10000); // Update every 10 seconds
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLocationText();
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.post(updateRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
}
