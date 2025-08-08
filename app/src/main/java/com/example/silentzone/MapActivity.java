package com.example.silentzone;
import java.util.List;
import java.util.ArrayList;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.Overlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Stack;

public class MapActivity extends AppCompatActivity {


    private List<Overlay> markerOverlays = new ArrayList<>();


    private MapView map;
    private AutoCompleteTextView searchInput;
    private Button searchButton, setLocationButton, undoButton;

    private Stack<Marker> markerStack = new Stack<>();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map);

        map = findViewById(R.id.map);
        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        setLocationButton = findViewById(R.id.setLocationButton);
        undoButton = findViewById(R.id.undoButton);

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        GeoPoint defaultPoint = new GeoPoint(10.8505, 76.2711);
        map.getController().setZoom(15.0);
        map.getController().setCenter(defaultPoint);

        MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        // ✅ Only respond to single tap (ignore zoom/swipe)
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                GeoPoint point = (GeoPoint) map.getProjection().fromPixels((int) e.getX(), (int) e.getY());
                placeMarker(point);
                return true;
            }
        });

        map.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

        searchButton.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                new GeocodeTask().execute(query);
            }
        });

        setLocationButton.setOnClickListener(v -> {
            if (!markerStack.isEmpty()) {
                Marker lastMarker = markerStack.peek();
                GeoPoint selectedPoint = lastMarker.getPosition();
                Intent intent = new Intent();
                intent.putExtra("lat", selectedPoint.getLatitude());
                intent.putExtra("lon", selectedPoint.getLongitude());
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });

        undoButton.setOnClickListener(v -> {
            if (!markerStack.isEmpty()) {
                Marker lastMarker = markerStack.pop();
                markerOverlays.remove(lastMarker);         // Remove from tracked list
                map.getOverlays().remove(lastMarker);      // Remove only that marker
                map.invalidate();

                if (markerStack.isEmpty()) {
                    setLocationButton.setVisibility(View.GONE);
                    undoButton.setVisibility(View.GONE);
                }
            }
        });


    }

    private void placeMarker(GeoPoint point) {
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setTitle("Selected Location");

        markerStack.push(marker);
        markerOverlays.add(marker); // 👈 Track this overlay separately

        map.getOverlays().add(marker);
        map.invalidate();

        setLocationButton.setVisibility(View.VISIBLE);
        undoButton.setVisibility(View.VISIBLE);
    }



    private class GeocodeTask extends AsyncTask<String, Void, GeoPoint> {
        @Override
        protected GeoPoint doInBackground(String... queries) {
            try {
                String urlStr = "https://nominatim.openstreetmap.org/search?q=" + queries[0] + "&format=json&limit=1";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestProperty("User-Agent", "SilentZoneApp");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray results = new JSONArray(sb.toString());
                if (results.length() > 0) {
                    JSONObject result = results.getJSONObject(0);
                    double lat = result.getDouble("lat");
                    double lon = result.getDouble("lon");
                    return new GeoPoint(lat, lon);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(GeoPoint point) {
            if (point != null) {
                map.getController().setZoom(17.0);
                map.getController().setCenter(point);
                placeMarker(point);
            } else {
                Toast.makeText(MapActivity.this, "Place not found. Try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
