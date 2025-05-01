package com.example.meshup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Activity for displaying and managing the mesh network status
 */
public class NetworkStatusActivity extends AppCompatActivity {
    private static final String TAG = "NetworkStatusActivity";

    // UI Components
    private TextView statusText;
    private TextView nodeCountText;
    private TextView routingInfoText;
    private TextView teslaStatusText;
    private NetworkVisualizationView networkView;
    private SwitchCompat fastAuthSwitch;
    private Button testBroadcastButton;

    // Network state (shared with MainActivity)
    private Map<String, Long> deviceLastSeenTime = new ConcurrentHashMap<>();
    private Set<String> directConnections = new HashSet<>();

    // Handler for UI updates
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_status);

        // Set up action bar with back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mesh Network Status");
        }

        uiHandler = new Handler(Looper.getMainLooper());

        // Initialize UI components
        initializeUI();

        // Load network state from shared preferences or intent
        loadNetworkState();

        // Set up refresh timer
        startRefreshTimer();
    }

    private void initializeUI() {
        statusText = findViewById(R.id.statusText);
        nodeCountText = findViewById(R.id.nodeCountText);
        routingInfoText = findViewById(R.id.routingInfoText);
        teslaStatusText = findViewById(R.id.teslaStatusText);
        networkView = findViewById(R.id.networkVisualization);
        fastAuthSwitch = findViewById(R.id.fastAuthSwitch);
        testBroadcastButton = findViewById(R.id.testBroadcastButton);

        // Set up fast authentication toggle
        fastAuthSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save preference
            getSharedPreferences("NetworkPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("fastAuth", isChecked)
                    .apply();

            // Show toast
            String message = isChecked ?
                    "Fast authentication enabled - reduced security" :
                    "Standard authentication enabled - full security";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            // Update display
            updateTeslaStatus();
        });

        // Set up test broadcast button
        testBroadcastButton.setOnClickListener(v -> {
            // Send test message to measure propagation
            sendTestBroadcast();
        });
    }

    private void loadNetworkState() {
        // Get device ID from shared preferences
        String deviceId = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                .getString("deviceId", "Unknown");

        // Set self ID for network visualization
        networkView.setSelfId(deviceId);

        // Initialize with data from intent if available
        Intent intent = getIntent();
        if (intent != null) {
            // Populate device maps from serialized data in intent
            try {
                String[] deviceIds = intent.getStringArrayExtra("deviceIds");
                long[] lastSeenTimes = intent.getLongArrayExtra("lastSeenTimes");
                String[] directDevices = intent.getStringArrayExtra("directDevices");

                if (deviceIds != null && lastSeenTimes != null && deviceIds.length == lastSeenTimes.length) {
                    deviceLastSeenTime.clear();
                    for (int i = 0; i < deviceIds.length; i++) {
                        deviceLastSeenTime.put(deviceIds[i], lastSeenTimes[i]);
                    }
                }

                if (directDevices != null) {
                    directConnections.clear();
                    for (String direct : directDevices) {
                        directConnections.add(direct);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Add some demo devices if empty
        if (deviceLastSeenTime.isEmpty()) {
            addDemoDevices();
        }

        // Initialize fast auth switch
        boolean fastAuth = getSharedPreferences("NetworkPrefs", MODE_PRIVATE)
                .getBoolean("fastAuth", false);
        fastAuthSwitch.setChecked(fastAuth);

        // Update UI
        updateUI();
    }

    private void addDemoDevices() {
        // Add some demo devices for visual testing
        long now = System.currentTimeMillis();
        deviceLastSeenTime.put("device1", now);
        deviceLastSeenTime.put("device2", now);
        deviceLastSeenTime.put("device3", now - 30000);
        deviceLastSeenTime.put("device4", now - 70000); // Inactive

        directConnections.add("device1");
        directConnections.add("device2");
    }

    private void updateUI() {
        // Count active and inactive devices
        long now = System.currentTimeMillis();
        int activeCount = 0;
        int inactiveCount = 0;

        for (Map.Entry<String, Long> entry : deviceLastSeenTime.entrySet()) {
            if (now - entry.getValue() < 60000) { // 1 minute timeout
                activeCount++;
            } else {
                inactiveCount++;
            }
        }

        // Update UI components
        nodeCountText.setText(String.format("Active Nodes: %d, Inactive: %d",
                activeCount, inactiveCount));

        statusText.setText(activeCount > 0 ?
                "Network Status: Connected" :
                "Network Status: Searching for peers...");

        routingInfoText.setText(String.format(
                "Direct Connections: %d\nMax Hops: %d\nRouting: AODV + Flooding",
                directConnections.size(), 5));

        // Update network visualization
        networkView.updateNetwork(deviceLastSeenTime, directConnections);

        // Update Tesla status
        updateTeslaStatus();
    }

    private void updateTeslaStatus() {
        boolean fastAuth = fastAuthSwitch.isChecked();

        if (fastAuth) {
            teslaStatusText.setText(
                    "TESLA Status: Fast Mode (5s)\n" +
                            "Authentication timeout: 10s\n" +
                            "Message Trust: Quick");
        } else {
            teslaStatusText.setText(
                    "TESLA Status: Standard (30s)\n" +
                            "Authentication timeout: None\n" +
                            "Message Trust: Verified Only");
        }
    }

    private void startRefreshTimer() {
        // Refresh UI every 5 seconds
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUI();
                uiHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private void sendTestBroadcast() {
        // Create test broadcast intent
        Intent intent = new Intent("com.example.meshup.TEST_BROADCAST");
        intent.putExtra("timestamp", System.currentTimeMillis());
        sendBroadcast(intent);

        Toast.makeText(this, "Test message broadcast sent", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Helper static method to launch this activity
     */
    public static void launch(Context context, Map<String, Long> deviceLastSeenTime, Set<String> directConnections) {
        Intent intent = new Intent(context, NetworkStatusActivity.class);

        // Convert map and set to arrays for intent extras
        String[] deviceIds = deviceLastSeenTime.keySet().toArray(new String[0]);
        long[] lastSeenTimes = new long[deviceIds.length];
        for (int i = 0; i < deviceIds.length; i++) {
            lastSeenTimes[i] = deviceLastSeenTime.get(deviceIds[i]);
        }

        String[] directDevices = directConnections.toArray(new String[0]);

        // Add extras
        intent.putExtra("deviceIds", deviceIds);
        intent.putExtra("lastSeenTimes", lastSeenTimes);
        intent.putExtra("directDevices", directDevices);

        context.startActivity(intent);
    }
}