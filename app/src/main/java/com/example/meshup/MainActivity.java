package com.example.meshup;

import android.Manifest;
import com.example.meshup.R;
import com.example.meshup.security.SecureMessageLog;
import com.example.meshup.security.TeslaKeyChain;
import com.example.meshup.security.TeslaAuthenticator;
import com.example.meshup.security.HopCountProtector;
import com.example.meshup.security.SecureMessageManager;

import android.database.Cursor;
import android.graphics.Color;
import android.provider.ContactsContract;
import android.content.ContentResolver;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import android.net.Uri;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.telephony.SmsManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int BROADCAST_PORT = 8888;
    private static final int MAX_HOP_COUNT = 5; // Prevent infinite routing

    private static final long ACKNOWLEDGMENT_TIMEOUT = 5000;
    private static final int TESLA_KEY_DISCLOSURE_INTERVAL = 5000; // 5 seconds

    private static final int PICK_CONTACT_REQUEST = 1;
    private static final int MAX_EMERGENCY_CONTACTS = 2;
    private ArrayList<String> emergencyContacts;
    private int currentContactPickerIndex = 0;
    private Map<String, Long> deviceLastSeenTime = new ConcurrentHashMap<>();
    private static final long DEVICE_TIMEOUT = 60000; // 60 seconds
    private RecyclerView messageRecyclerView;
    private MessageAdapter messageAdapter;
    private TextView deviceCountText;
    private String userName;
    private String deviceMac;
    private SecureMessageLog securityLog;


    private EditText messageInput;
    private Button sendButton;
    private TextView statusText;

    private ExecutorService executorService;
    private ScheduledExecutorService scheduledExecutor;
    private Handler mainHandler;

    private volatile boolean isReceiving = true;

    // Track seen message IDs to prevent duplicates
    private Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();

    // Unique device ID
    private final String DEVICE_ID = UUID.randomUUID().toString();

    // Track connected devices
    private Set<String> connectedDevices = ConcurrentHashMap.newKeySet();

    private String localUsername = "Anonymous";
    private NetworkChangeReceiver networkChangeReceiver;

    // Security components
    private TeslaKeyChain teslaKeyChain;
    private TeslaAuthenticator teslaAuth;
    private HopCountProtector hopProtector;
    private SecureMessageManager secureMessageManager;
    private Map<String, Message> pendingVerification = new ConcurrentHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // In onCreate method of MainActivity.java
        Button securityButton = findViewById(R.id.securityButton);
        securityButton.setOnClickListener(v -> showSecurityStatusDialog());

        // Initialize security components
        initializeSecurity();

        // Initialize emergency contacts
        emergencyContacts = new ArrayList<>();

        // Initialize UI elements BEFORE using them
        initializeUIComponents();
        loadMessages();

        checkAndSetupEmergencyContacts();
        Button sosButton = findViewById(R.id.sosButton);
        sosButton.setOnClickListener(v -> sendSOSMessage());

        // Setup execution and UI handling
        executorService = Executors.newCachedThreadPool();
        scheduledExecutor = Executors.newScheduledThreadPool(1);
        mainHandler = new Handler(Looper.getMainLooper());

        // Check and request necessary permissions
        checkPermissions();
        requestLocationPermissions();

        // Setup UI interactions
        setupUI();

        // Start background message receiver
        startMessageReceiver();

        // Start key disclosure scheduler
        scheduleKeyDisclosure();

        // Update connection status
        updateConnectionStatus();

        // Prompt for username after initial setup
        promptForUserName();
        networkChangeReceiver = new NetworkChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkChangeReceiver, filter);
    }

    private void initializeSecurity() {
        // Create TESLA key chain with 100 keys and 30-second disclosure interval
        teslaKeyChain = new TeslaKeyChain(100, TESLA_KEY_DISCLOSURE_INTERVAL);
        teslaAuth = new TeslaAuthenticator(teslaKeyChain);
        hopProtector = new HopCountProtector();
        secureMessageManager = new SecureMessageManager(teslaAuth, hopProtector);
        securityLog = new SecureMessageLog(this);

        // Log security initialization
        securityLog.logKeyGeneration(100, TESLA_KEY_DISCLOSURE_INTERVAL);

        Log.d(TAG, "Initialized security components");
    }


    // Update scheduleKeyDisclosure
    private void scheduleKeyDisclosure() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                // Get current key index
                int keyIndex = teslaKeyChain.getCurrentIndex() + 1;
                if (keyIndex >= teslaKeyChain.getCurrentIndex()) {
                    // Get the key to disclose
                    byte[] key = teslaKeyChain.getKeyByIndex(keyIndex);
                    if (key != null) {
                        // Create and send key disclosure message
                        Message keyMessage = secureMessageManager.createKeyDisclosureMessage(
                                DEVICE_ID, localUsername, keyIndex, key);

                        broadcastSecureMessage(keyMessage);

                        // Log key disclosure
                        String keyPrefix = SecureMessageManager.byteArrayToHex(key).substring(0, 8);
                        securityLog.logKeyDisclosure(keyIndex, keyPrefix);

                        Log.d(TAG, "Disclosed TESLA key for index: " + keyIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in key disclosure task", e);
            }
        }, TESLA_KEY_DISCLOSURE_INTERVAL, TESLA_KEY_DISCLOSURE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void showSecurityStatusDialog() {
        try {
            // Create a simple AlertDialog if the custom layout isn't available
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            // Try to use the custom layout
            View dialogView = null;
            try {
                dialogView = getLayoutInflater().inflate(R.layout.dialog_security_status, null);
                builder.setView(dialogView);
            } catch (Exception e) {
                Log.e(TAG, "Could not inflate custom dialog layout", e);
                // Fallback to a simple dialog if the layout isn't available
                builder.setTitle("Security Status")
                        .setMessage("TESLA Authentication active\n" +
                                "Current Key Index: " + teslaKeyChain.getCurrentIndex() + "\n" +
                                "Connected Devices: " + connectedDevices.size() + "\n" +
                                "Pending Messages: " + pendingVerification.size() + "\n" +
                                "Max Hop Count: " + MAX_HOP_COUNT)
                        .setPositiveButton("OK", null);

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                return;
            }

            // Set up the dialog components
            TextView teslaInfoText = dialogView.findViewById(R.id.teslaInfoText);
            TextView hopCountInfoText = dialogView.findViewById(R.id.hopCountInfoText);
            TextView statsText = dialogView.findViewById(R.id.statsText);
            ProgressBar teslaProgressBar = dialogView.findViewById(R.id.teslaProgressBar);

            // Fill with security information
            int currentKeyIndex = teslaKeyChain.getCurrentIndex();
            int totalKeys = 100; // From your initialization
            int keysUsed = totalKeys - currentKeyIndex;
            int keysRemaining = currentKeyIndex;

            // Set TESLA information
            teslaInfoText.setText(String.format("TESLA Authentication Active\n" +
                            "Current Key Index: %d\n" +
                            "Keys Used: %d / %d\n" +
                            "Disclosure Interval: %d seconds",
                    currentKeyIndex, keysUsed, totalKeys,
                    TESLA_KEY_DISCLOSURE_INTERVAL / 1000));

            // Set progress bar
            teslaProgressBar.setMax(totalKeys);
            teslaProgressBar.setProgress(keysUsed);

            // Set hop count information
            hopCountInfoText.setText(String.format("Hop Count Protection Active\n" +
                            "Maximum Hops: %d\n" +
                            "Using SHA-256 Hash Chain",
                    MAX_HOP_COUNT));

            // Set network statistics
            statsText.setText(String.format("Connected Devices: %d\n" +
                            "Messages Pending Authentication: %d\n" +
                            "Secure Messages Sent: %d",
                    connectedDevices.size(),
                    pendingVerification.size(),
                    securityLog.getMessageCount()));

            // Show the log file path
            Button viewLogButton = dialogView.findViewById(R.id.viewLogButton);
            viewLogButton.setOnClickListener(v -> {
                Toast.makeText(this, "Security log: " + securityLog.getLogFile().getAbsolutePath(),
                        Toast.LENGTH_LONG).show();
            });

            // Create and show dialog
            AlertDialog alertDialog = builder.create();
            alertDialog.setTitle("Security Status");
            alertDialog.show();

        } catch (Exception e) {
            // Ultimate fallback if anything goes wrong
            Log.e(TAG, "Error showing security status dialog", e);
            Toast.makeText(this, "TESLA Auth Active: Key Index " +
                    teslaKeyChain.getCurrentIndex(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkAndSetupEmergencyContacts() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String contact1 = prefs.getString("emergency_contact1", null);
        String contact2 = prefs.getString("emergency_contact2", null);

        if (contact1 == null || contact2 == null) {
            showEmergencyContactsDialog();
        } else {
            emergencyContacts.clear();
            emergencyContacts.add(contact1);
            emergencyContacts.add(contact2);
        }
    }

    private void showEmergencyContactsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Contacts Setup")
                .setMessage("Please select two emergency contacts from your phone book.")
                .setPositiveButton("Select Contacts", (dialog, which) -> {
                    // Check for contacts permission first
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.READ_CONTACTS},
                                PERMISSIONS_REQUEST_CODE);
                    } else {
                        startContactPicker();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void startContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            // Handle the contact picked
            Uri contactUri = data.getData();
            String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};

            try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    String phoneNumber = cursor.getString(numberIndex);

                    // Save the contact
                    saveEmergencyContact(phoneNumber, currentContactPickerIndex + 1);
                    emergencyContacts.add(phoneNumber);

                    // If we need another contact, start picker again
                    if (currentContactPickerIndex < MAX_EMERGENCY_CONTACTS - 1) {
                        currentContactPickerIndex++;
                        startContactPicker();
                    } else {
                        // Reset index for next time
                        currentContactPickerIndex = 0;
                        Toast.makeText(this, "Emergency contacts saved successfully!",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void saveEmergencyContact(String phoneNumber, int contactIndex) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("emergency_contact" + contactIndex, phoneNumber);
        editor.apply();
    }


    private int getBatteryPercentage() {
        BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return batteryManager != null
                ? batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                : -1; // Return -1 if unavailable
    }
    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE);
        }
    }

    private void saveMessage(String message) {
        SharedPreferences prefs = getSharedPreferences("ChatHistory", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Retrieve existing messages
        String existingMessages = prefs.getString("messages", "");

        // Append new message
        existingMessages += message + "|||"; // Use "|||" as a separator to avoid confusion

        // Save updated messages
        editor.putString("messages", existingMessages);
        editor.apply();
    }

    private void loadMessages() {
        SharedPreferences prefs = getSharedPreferences("ChatHistory", MODE_PRIVATE);
        String savedMessages = prefs.getString("messages", "");

        Log.d("MainActivity", "Loaded messages: " + savedMessages); // Debugging log

        if (!savedMessages.isEmpty()) {
            String[] messages = savedMessages.split("\\|\\|\\|"); // Correctly split messages
            for (String msg : messages) {
                if (!msg.trim().isEmpty()) {
                    Message msgObj = new Message("You", "Unknown", "Local", msg, UUID.randomUUID().toString());
                    msgObj.setDelivered(true);
                    messageAdapter.addMessage(msgObj);
                }
            }
        }
    }

    private String getGPSCoordinates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "Location Permission Not Granted";
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return "Location Service Not Available";
        }

        // Check if GPS is enabled
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // Prompt user to enable GPS
            new AlertDialog.Builder(this)
                    .setMessage("GPS is disabled. Please enable it to get location coordinates.")
                    .setPositiveButton("Open Settings", (dialogInterface, i) -> {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return "GPS Disabled";
        }

        // Try multiple location providers in order of accuracy
        Location location = null;

        // Try GPS provider first
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        // If GPS failed, try NETWORK provider
        if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        // If NETWORK failed, try PASSIVE provider
        if (location == null && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }

        if (location != null) {
            return String.format("Lat: %.6f, Long: %.6f", location.getLatitude(), location.getLongitude());
        } else {
            // If still no location, request location updates
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000, // minimum time interval between updates in milliseconds
                        1,    // minimum distance between updates in meters
                        location1 -> {
                            String coordinates = String.format("Lat: %.6f, Long: %.6f",
                                    location1.getLatitude(),
                                    location1.getLongitude());
                            // Update your UI or store the coordinates as needed
                        },
                        Looper.getMainLooper()
                );
            } catch (SecurityException e) {
                Log.e("Location", "Error requesting location updates", e);
            }
            return "Acquiring Location...";
        }
    }

    private void sendSOSMessage() {
        if (emergencyContacts.size() < 2) {
            showEmergencyContactsDialog();
            return;
        }

        String batteryPercentage = getBatteryPercentage() + "%";
        String gpsCoordinates = getGPSCoordinates();

        // Predefined SOS message
        String emergencyMessage = String.format(
                "EMERGENCY SOS!\nDevice: %s %s\nBattery: %s\nLocation: %s",
                Build.MANUFACTURER, Build.MODEL, batteryPercentage, gpsCoordinates
        );

        // Send SMS to emergency contacts
        for (String contact : emergencyContacts) {
            sendSMS(contact, emergencyMessage);
        }

        // Retrieve saved emergency contact from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String emergencyNumber = prefs.getString("emergency_contact", "+91100"); // Default: Police

        // Send SMS to predefined contacts
        sendSMS(emergencyNumber, emergencyMessage); // Primary emergency contact
        sendSMS("+919503260577", emergencyMessage); // Personal emergency contact

        // Also send via mesh network with security
        broadcastMessage(emergencyMessage);

        Toast.makeText(this, "SOS sent via SMS & Mesh Network!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted
            } else {
                Toast.makeText(this, "Location permission is required for SOS!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeUIComponents() {
        // Ensure ALL UI components are initialized
        messageInput = findViewById(R.id.messageInput);
//        messageView = findViewById(R.id.messageView); // Keep this if you still want it
        sendButton = findViewById(R.id.sendButton);
        statusText = findViewById(R.id.statusText);
//        deviceCountText = findViewById(R.id.deviceCountText);
        Button securityButton = findViewById(R.id.securityButton);


        // Initialize RecyclerView
        messageRecyclerView = findViewById(R.id.messageRecyclerView);
        messageAdapter = new MessageAdapter(this);
        messageRecyclerView.setAdapter(messageAdapter);
        messageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupUI() {
        sendButton.setOnClickListener(v -> {
            // Null and empty checks
            if (messageInput == null) {
                Log.e("MainActivity", "Message input is null");
                return;
            }

            String message = messageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                broadcastMessage(message);
                messageInput.setText(""); // Clear input
            }
        });
    }
    @Override
    public void onBackPressed() {
        // Redirect to NetworkControlActivity
        super.onBackPressed();
        Intent intent = new Intent(this, NetworkControlActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish(); // End MainActivity
    }


    private void promptForUserName() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        localUsername = prefs.getString("username", null);

        if (localUsername == null) {
            BottomSheetDialog dialog = new BottomSheetDialog(this);
            dialog.setContentView(R.layout.dialog_username);

            EditText usernameInput = dialog.findViewById(R.id.usernameInput);
            Button saveButton = dialog.findViewById(R.id.saveButton);

            saveButton.setOnClickListener(v -> {
                String name = usernameInput.getText().toString().trim();
                localUsername = TextUtils.isEmpty(name)
                        ? "User-" + DEVICE_ID.substring(0, 8)
                        : name;

                // Save username
                prefs.edit().putString("username", localUsername).apply();

                dialog.dismiss();
            });

            dialog.setCancelable(false);
            dialog.show();
        }
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
                Manifest.permission.SEND_SMS, // Add SMS permission
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);
        }
    }



    private void updateConnectionStatus() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null || statusText == null) return;

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            String status = "Network Status: ";
            try {
                String localIP = getLocalIpAddress();
                status += (localIP != null ? "Connected (IP: " + localIP + ")" : "Not Connected");
            } catch (Exception e) {
                status += "Error detecting network";
            }

            final String finalStatus = status;
            if (mainHandler != null) {
                mainHandler.post(() -> {
                    if (statusText != null) {
                        statusText.setText(finalStatus);
                    }
                });
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to update connection status", e);
        }
    }

    private void broadcastMessage(final String message) {
        saveMessage(message);
        executorService.execute(() -> {
            try {
                // Get local info
                String senderName = TextUtils.isEmpty(localUsername)
                        ? "User-" + DEVICE_ID.substring(0, 8)
                        : localUsername;
                String messageId = UUID.randomUUID().toString();
                String localIP = getLocalIpAddress();

                // Create secure message with TESLA and hop-count protection
                Message secureMsg = secureMessageManager.createSecureMessage(
                        DEVICE_ID,
                        senderName,
                        message,
                        localIP != null ? localIP : "Unknown",
                        messageId
                );

                // Add to UI
                mainHandler.post(() -> {
                    if (messageAdapter != null) {
                        messageAdapter.addMessage(secureMsg);
                    }
                });

                // Broadcast secure message
                broadcastSecureMessage(secureMsg);

                // Schedule acknowledgment timeout
                scheduleAcknowledgmentTimeout(messageId);

            } catch (Exception e) {
                Log.e(TAG, "Broadcast message failed", e);
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this,
                                "Failed to broadcast message",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void broadcastSecureMessage(Message secureMsg) {
        executorService.execute(() -> {
            try {
                // Serialize message for transmission
                String serializedMessage = SecureMessageManager.serializeMessage(secureMsg);

                // Create UDP broadcast socket
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);

                // Prepare message data
                byte[] sendData = serializedMessage.getBytes();

                // Get network interface details
                List<InetAddress> broadcastAddresses = getBroadcastAddresses();

                // Send to all potential broadcast addresses
                for (InetAddress broadcastAddress : broadcastAddresses) {
                    try {
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendData,
                                sendData.length,
                                broadcastAddress,
                                BROADCAST_PORT
                        );

                        socket.send(sendPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                socket.close();

                Log.d(TAG, "Broadcasted secure message: " + secureMsg.getContent());
            } catch (Exception e) {
                Log.e(TAG, "Error broadcasting secure message", e);
            }
        });
    }

    private void scheduleAcknowledgmentTimeout(String messageId) {
        mainHandler.postDelayed(() -> {
            messageAdapter.updateMessageDeliveryStatus(messageId, false);
        }, ACKNOWLEDGMENT_TIMEOUT);
    }

    private List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> broadcastAddresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        broadcastAddresses.add(broadcast);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // Add fallback broadcast address if no addresses found
        if (broadcastAddresses.isEmpty()) {
            try {
                broadcastAddresses.add(InetAddress.getByName("255.255.255.255"));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        return broadcastAddresses;
    }

    private void startMessageReceiver() {
        executorService.execute(() -> {
            try {
                // Create UDP socket to receive broadcasts
                DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
                socket.setBroadcast(true);

                byte[] receiveBuffer = new byte[2048]; // Increased buffer size for secure messages

                while (isReceiving) {
                    // Prepare packet to receive data
                    DatagramPacket receivePacket = new DatagramPacket(
                            receiveBuffer,
                            receiveBuffer.length
                    );

                    // Receive incoming packet
                    socket.receive(receivePacket);

                    // Convert received data to message
                    String receivedMessage = new String(
                            receivePacket.getData(),
                            0,
                            receivePacket.getLength()
                    );

                    // Process the received secure message
                    processReceivedSecureMessage(receivedMessage,
                            receivePacket.getAddress().getHostAddress());
                }

                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this,
                            "Failed to receive messages",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // Modify the processReceivedSecureMessage method
    private void processReceivedSecureMessage(String receivedMessage, String senderIP) {
        try {
            // Deserialize the message
            Message secureMsg = SecureMessageManager.deserializeMessage(receivedMessage);

            if (secureMsg == null) {
                Log.w(TAG, "Failed to deserialize message");
                return;
            }

            String messageId = secureMsg.getMessageId();
            String originDeviceId = secureMsg.getOriginDeviceId();

            // Track connected device
            connectedDevices.add(originDeviceId);

            // Check if we've already seen this message
            if (seenMessageIds.contains(messageId)) {
                return; // Prevent duplicate processing
            }

            // Update last seen time for the device
            deviceLastSeenTime.put(originDeviceId, System.currentTimeMillis());

            // Add message to seen list
            seenMessageIds.add(messageId);

            // Check if it's a key disclosure message
            if (secureMsg.isKeyDisclosure()) {
                processKeyDisclosure(secureMsg);

                // Also forward key disclosures to propagate them through the network
                if (!originDeviceId.equals(DEVICE_ID)) {
                    broadcastSecureMessage(secureMsg);
                }
                return;
            }

            // Verify hop count integrity
            if (!secureMessageManager.verifyHopCount(secureMsg)) {
                Log.w(TAG, "Hop count verification failed for message: " + messageId);
                return;
            }

            // If not from this device, process and potentially rebroadcast
            if (!originDeviceId.equals(DEVICE_ID)) {
                // Try to verify with already disclosed keys first
                boolean verified = secureMessageManager.verifyWithStoredKeys(secureMsg);

                if (verified) {
                    secureMsg.setAuthenticated(true);
                } else {
                    // Buffer message for TESLA verification
                    secureMessageManager.bufferMessage(secureMsg);

                    // Add message to pending verification
                    pendingVerification.put(messageId, secureMsg);
                }

                // Display received message on UI thread
                mainHandler.post(() -> {
                    secureMsg.setDelivered(true);
                    messageAdapter.addMessage(secureMsg);
                });

                // Always rebroadcast message with updated hop count (if within limits)
                // This ensures it reaches all nodes in the network
                if (secureMsg.getHopCount() < MAX_HOP_COUNT) {
                    rebroadcastSecureMessage(secureMsg);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing received message", e);
        }
    }

    private void processKeyDisclosure(Message keyDisclosureMsg) {
        try {
            int keyIndex = keyDisclosureMsg.getTeslaKeyIndex();
            byte[] disclosedKey = keyDisclosureMsg.getDisclosedKey();

            if (keyIndex < 0 || disclosedKey == null) {
                Log.w(TAG, "Invalid key disclosure message");
                return;
            }

            // Log the key disclosure reception
            String keyPrefix = SecureMessageManager.byteArrayToHex(disclosedKey).substring(0, 8);
            securityLog.logKeyDisclosure(keyIndex, keyPrefix);

            Log.d(TAG, "Processing key disclosure for index: " + keyIndex);

            // Process buffered messages with the disclosed key
            Map<String, Boolean> verificationResults =
                    secureMessageManager.processKeyDisclosure(keyIndex, disclosedKey);

            // Update message status in UI
            mainHandler.post(() -> {
                for (Map.Entry<String, Boolean> result : verificationResults.entrySet()) {
                    String msgId = result.getKey();
                    boolean verified = result.getValue();

                    // Log verification result
                    securityLog.logVerification(msgId, verified);

                    messageAdapter.updateMessageAuthenticationStatus(msgId, verified);

                    // Remove from pending verification
                    pendingVerification.remove(msgId);
                }
            });

            // Add to processKeyDisclosure method in MainActivity.java
// After processing verification results
            if (!verificationResults.isEmpty()) {
                // Show notification if messages were verified
                String message = verificationResults.size() + " message(s) authenticated!";
                Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT)
                        .setBackgroundTint(getResources().getColor(R.color.md_theme_primary))
                        .setTextColor(Color.WHITE)
                        .show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing key disclosure", e);
        }
    }

    // In MainActivity.java, modify the rebroadcastSecureMessage method
    public void rebroadcastSecureMessage(Message secureMsg) {
        try {
            int oldHopCount = secureMsg.getHopCount();

            // Only rebroadcast if within hop limit
            if (oldHopCount >= MAX_HOP_COUNT) {
                Log.d(TAG, "Max hop count reached, not rebroadcasting");
                return;
            }

            // Process message for forwarding
            Message forwardedMsg = secureMessageManager.processForForwarding(secureMsg);

            // Add short random delay to prevent network congestion
            int randomDelay = new Random().nextInt(500); // 0-500ms

            // Use Handler for delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // Broadcast the forwarded message
                broadcastSecureMessage(forwardedMsg);

                Log.d(TAG, "Rebroadcast message with hop count: " + forwardedMsg.getHopCount() +
                        " (previous: " + oldHopCount + ")");

                // Log forwarding
                securityLog.logHopCountUpdate(
                        secureMsg.getMessageId(), oldHopCount, forwardedMsg.getHopCount());

            }, randomDelay);

        } catch (Exception e) {
            Log.e(TAG, "Error rebroadcasting message", e);
        }
    }
//    private void rebroadcastSecureMessage(Message secureMsg) {
//        try {
//            int oldHopCount = secureMsg.getHopCount();
//            Message forwardedMsg = secureMessageManager.processForForwarding(secureMsg);
//
//            // Log hop count update
//            securityLog.logHopCountUpdate(
//                    secureMsg.getMessageId(), oldHopCount, forwardedMsg.getHopCount());
//
//            broadcastSecureMessage(forwardedMsg);
//            Log.d(TAG, "Rebroadcast message, hop count: " + forwardedMsg.getHopCount());
//        } catch (Exception e) {
//            Log.e(TAG, "Error rebroadcasting message", e);
//        }
//    }

    private String constructMeshMessage(String messageId, String message) {
        // Format: messageId|originDeviceId|hopCount|senderUsername|message
        return String.format("%s|%s|0|%s|%s",
                messageId,
                DEVICE_ID,
                localUsername,  // Include actual username
                message
        );
    }

    private String getDeviceMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.getName().equals("wlan0")) continue;
                byte[] mac = intf.getHardwareAddress();
                if (mac == null) return "Unknown";

                StringBuilder buf = new StringBuilder();
                for (byte aMac : mac) {
                    buf.append(String.format("%02X:", aMac));
                }
                if (buf.length() > 0) {
                    buf.deleteCharAt(buf.length() - 1);
                }
                return buf.toString();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "Unknown";
    }

    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    boolean isConnected = networkInfo.isConnected();

                    mainHandler.post(() -> {
                        if (isConnected) {
                            updateConnectionStatus();
                            // Reinitialize network-dependent components
                            startMessageReceiver();
                        } else {
                            statusText.setText("Network Status: Disconnected");
                            // Clear connected devices
                            connectedDevices.clear();
                            deviceLastSeenTime.clear();
                        }
                    });
                }
            }
        }
    }

    // Remove disconnected devices periodically
    private void startDeviceCleanup() {
        executorService.execute(() -> {
            while (isReceiving) {
                try {
                    Thread.sleep(30000); // Check every 30 seconds

                    long currentTime = System.currentTimeMillis();
                    deviceLastSeenTime.entrySet().removeIf(entry ->
                            currentTime - entry.getValue() > DEVICE_TIMEOUT
                    );

                    // Remove from connected devices
                    connectedDevices.retainAll(deviceLastSeenTime.keySet());

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    private void sendAcknowledgment(String messageId, String targetDeviceId) {
        // Send UDP acknowledgment packet
        executorService.execute(() -> {
            try {
                String ackMessage = "ACK|" + messageId + "|" + DEVICE_ID;
                // ... (similar to broadcast code, but with ack message)
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void sendSMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Log.d("SOS", "SMS sent to: " + phoneNumber);
        } catch (Exception e) {
            Log.e("SOS", "Failed to send SMS", e);
        }
    }

    private void saveEmergencyContact(String phoneNumber) {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("emergency_contact", phoneNumber);
        editor.apply();
    }
    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() &&
                            inetAddress.getHostAddress().contains(".")) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkChangeReceiver != null) {
            unregisterReceiver(networkChangeReceiver);
        }
        // Stop receiving messages
        isReceiving = false;

        // Shutdown executors
        if (executorService != null) {
            executorService.shutdown();
        }

        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
    }
}