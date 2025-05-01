package com.example.meshup.security;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Logs security events for analysis and debugging
 */
public class SecureMessageLog {
    private static final String TAG = "SecureMessageLog";
    private static final String LOG_FILE = "security_log.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private Context context;
    private File logFile;

    public SecureMessageLog(Context context) {
        this.context = context;
        this.logFile = new File(context.getExternalFilesDir(null), LOG_FILE);
    }

    public void logKeyGeneration(int chainLength, int interval) {
        logEvent("TESLA_KEY_CHAIN_GENERATED",
                "Generated new key chain with " + chainLength + " keys. " +
                        "Disclosure interval: " + interval + "ms");
    }
    // Add this to SecureMessageLog.java
    private int messageCount = 0;

    // Add this method to SecureMessageLog.java
    public void incrementMessageCount() {
        messageCount++;
    }

    // Add this method to SecureMessageLog.java
    public int getMessageCount() {
        return messageCount;
    }

    // Update logMessageAuthentication to track count
    public void logMessageAuthentication(String messageId, int keyIndex) {
        incrementMessageCount();
        logEvent("MESSAGE_AUTHENTICATED",
                "Generated authentication for message " + messageId + " with key index " + keyIndex);
    }

    public void logKeyDisclosure(int keyIndex, String keyPrefix) {
        logEvent("KEY_DISCLOSED",
                "Disclosed TESLA key " + keyIndex + ": " + keyPrefix + "...");
    }

    public void logVerification(String messageId, boolean success) {
        logEvent(success ? "VERIFICATION_SUCCESS" : "VERIFICATION_FAIL",
                "Message " + messageId + " verification: " + (success ? "PASSED" : "FAILED"));
    }

    public void logHopCountUpdate(String messageId, int oldHop, int newHop) {
        logEvent("HOP_COUNT_UPDATE",
                "Message " + messageId + " hop count updated: " + oldHop + " -> " + newHop);
    }

    public void logHopVerification(String messageId, boolean success) {
        logEvent(success ? "HOP_VERIFICATION_SUCCESS" : "HOP_VERIFICATION_FAIL",
                "Message " + messageId + " hop verification: " + (success ? "PASSED" : "FAILED"));
    }

    private void logEvent(String eventType, String description) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logLine = timestamp + " | " + eventType + " | " + description;

        // Log to system
        Log.d(TAG, logLine);

        // Write to file
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(logLine);
            writer.newLine();
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    public void showSecurityLog() {
        try {
            // Show a toast with log file location
            Toast.makeText(context,
                    "Security log available at: " + logFile.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing security log", e);
        }
    }

    public File getLogFile() {
        return logFile;
    }
}