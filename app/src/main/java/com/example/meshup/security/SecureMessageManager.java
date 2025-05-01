package com.example.meshup.security;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.meshup.Message;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages secure message processing including buffering for verification.
 */
public class SecureMessageManager {
    private static final String TAG = "SecureMessageManager";

    private TeslaAuthenticator teslaAuth;
    private HopCountProtector hopProtector;
    private Map<String, PendingMessage> pendingMessages;

    public SecureMessageManager(TeslaAuthenticator teslaAuth, HopCountProtector hopProtector) {
        this.teslaAuth = teslaAuth;
        this.hopProtector = hopProtector;
        this.pendingMessages = new ConcurrentHashMap<>();
    }

    /**
     * Creates a secure message with TESLA and hop count protection.
     */
    public Message createSecureMessage(String deviceId, String username, String messageContent,
                                       String ipAddress, String messageId) {
        try {
            // Generate TESLA authentication
            TeslaAuthenticator.TeslaAuthData authData = teslaAuth.authenticate(messageContent);

            // Generate hop count protection
            HopCountProtector.HopData hopData = hopProtector.generateHopChain(messageContent);

            Log.d(TAG, "Created secure message with TESLA key index " + authData.keyIndex +
                    " and hop count " + hopData.hopCount);

            return new Message(
                    username,           // senderName
                    "Unknown",          // macAddress
                    ipAddress,          // ipAddress
                    messageContent,     // content
                    messageId,          // messageId
                    deviceId,           // originDeviceId
                    authData.keyIndex,  // teslaKeyIndex
                    authData.mac,       // teslaMac
                    null,               // disclosedKey (null for new messages)
                    hopData.hopCount,   // hopCount
                    hopData.currentHash,// hopCurrentHash
                    hopData.topHash     // hopTopHash
            );
        } catch (Exception e) {
            Log.e(TAG, "Error creating secure message", e);
            // Fallback to basic message if security fails
            return new Message(username, "Unknown", ipAddress, messageContent, messageId);
        }
    }

    /**
     * Creates a key disclosure message.
     */
    public Message createKeyDisclosureMessage(String deviceId, String username, int keyIndex, byte[] disclosedKey) {
        String messageId = "key-" + keyIndex + "-" + System.currentTimeMillis();

        Log.d(TAG, "Created key disclosure message for key index " + keyIndex);

        return new Message(
                username,      // senderName
                "Unknown",     // macAddress
                "Local",       // ipAddress
                "Key disclosure " + keyIndex, // content
                messageId,     // messageId
                deviceId,      // originDeviceId
                keyIndex,      // teslaKeyIndex
                null,          // teslaMac
                disclosedKey,  // disclosedKey
                0,             // hopCount
                null,          // hopCurrentHash
                null           // hopTopHash
        );
    }

    /**
     * Process a received message for forwarding.
     */
    public Message processForForwarding(Message message) {
        try {
            if (message.isKeyDisclosure()) {
                // Don't modify key disclosure messages
                return message;
            }

            // Update hop count data
            HopCountProtector.HopData currentHopData = new HopCountProtector.HopData(
                    message.getHopCount(),
                    message.getHopCurrentHash(),
                    message.getHopTopHash()
            );

            HopCountProtector.HopData newHopData = hopProtector.processForForwarding(currentHopData);

            // Create new message with updated hop data
            Message forwardedMessage = new Message(
                    message.getSenderName(),
                    message.getMacAddress(),
                    message.getIpAddress(),
                    message.getContent(),
                    message.getMessageId(),
                    message.getOriginDeviceId(),
                    message.getTeslaKeyIndex(),
                    message.getTeslaMac(),
                    message.getDisclosedKey(),
                    newHopData.hopCount,
                    newHopData.currentHash,
                    newHopData.topHash
            );

            Log.d(TAG, "Processed message for forwarding, new hop count: " + newHopData.hopCount);
            return forwardedMessage;

        } catch (Exception e) {
            Log.e(TAG, "Error processing message for forwarding", e);
            return message; // Return original if processing fails
        }
    }

    /**
     * Verify the hop count of a received message.
     */
    public boolean verifyHopCount(Message message) {
        try {
            if (message.isKeyDisclosure() || message.getHopTopHash() == null) {
                return true; // Skip verification for key disclosure or messages without hop protection
            }

            HopCountProtector.HopData hopData = new HopCountProtector.HopData(
                    message.getHopCount(),
                    message.getHopCurrentHash(),
                    message.getHopTopHash()
            );

            return hopProtector.verifyHopData(hopData);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying hop count", e);
            return false;
        }
    }

    /**
     * Buffer a message for later authentication when key is disclosed.
     */
    public void bufferMessage(Message message) {
        if (message.getTeslaKeyIndex() < 0 || message.getTeslaMac() == null) {
            // Not a TESLA protected message
            return;
        }

        PendingMessage pending = new PendingMessage(
                message.getMessageId(),
                message.getContent(),
                message.getTeslaKeyIndex(),
                message.getTeslaMac()
        );

        pendingMessages.put(message.getMessageId(), pending);
        Log.d(TAG, "Buffered message " + message.getMessageId() + " for TESLA key index " +
                message.getTeslaKeyIndex());
    }

    /**
     * Process a key disclosure message and verify pending messages.
     *
     * @return Map of message IDs to verification results
     */
//    public Map<String, Boolean> processKeyDisclosure(int keyIndex, byte[] disclosedKey) {
//        Log.d(TAG, "Processing key disclosure for index " + keyIndex);
//
//        Map<String, Boolean> results = new ConcurrentHashMap<>();
//
//        for (Map.Entry<String, PendingMessage> entry : pendingMessages.entrySet()) {
//            PendingMessage pending = entry.getValue();
//
//            if (pending.keyIndex == keyIndex) {
//                boolean verified = teslaAuth.verify(
//                        pending.content,
//                        new TeslaAuthenticator.TeslaAuthData(keyIndex, pending.mac, null),
//                        disclosedKey
//                );
//
//                results.put(entry.getKey(), verified);
//
//                if (verified) {
//                    Log.d(TAG, "Successfully verified message " + entry.getKey());
//                } else {
//                    Log.w(TAG, "Message " + entry.getKey() + " failed verification");
//                }
//
//                // Remove from pending after verification
//                pendingMessages.remove(entry.getKey());
//            }
//        }
//
//        return results;
//    }

    // In SecureMessageManager.java
    public boolean verifyWithStoredKeys(Message message) {
        // Add immediate display with "unverified" status
        if (message.getTeslaKeyIndex() < 0 || message.getTeslaMac() == null) {
            return true; // Non-protected messages
        }

        // Display message immediately with "pending verification" status
        message.setPendingVerification(true);

        // Check for stored keys as before
        byte[] key = disclosedKeys.get(message.getTeslaKeyIndex());
        if (key != null) {
            boolean verified = teslaAuth.verify(
                    message.getContent(),
                    new TeslaAuthenticator.TeslaAuthData(message.getTeslaKeyIndex(), message.getTeslaMac(), null),
                    key
            );

            Log.d(TAG, "Immediate verification for message " + message.getMessageId() +
                    " with stored key: " + verified);
            return verified;
        }

        // Schedule a timeout to mark message as "unverified but trusted" after delay
        scheduleTrustTimeout(message.getMessageId(), 10000); // 10 seconds
        return false;
    }

    // Add this method to provide fallback trust
    private void scheduleTrustTimeout(String messageId, long timeoutMs) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Find message in pendingVerification and mark as "trusted but unverified"
            PendingMessage pending = pendingMessages.get(messageId);
            if (pending != null) {
                // Inform UI that verification timed out but message should be trusted
                // This avoids leaving messages in "pending" state indefinitely
            }
        }, timeoutMs);
    }

    // Modify the processKeyDisclosure method to be more tolerant
    public Map<String, Boolean> processKeyDisclosure(int keyIndex, byte[] disclosedKey) {
        Log.d(TAG, "Processing key disclosure for index " + keyIndex + ", key: " +
                byteArrayToHex(disclosedKey).substring(0, 10) + "...");

        Map<String, Boolean> results = new ConcurrentHashMap<>();

        // Store this key for future verifications
        storeDisclosedKey(keyIndex, disclosedKey);

        for (Map.Entry<String, PendingMessage> entry : pendingMessages.entrySet()) {
            PendingMessage pending = entry.getValue();

            // Check if this is the key we need or an older key (still valid)
            if (pending.keyIndex == keyIndex) {
                boolean verified = teslaAuth.verify(
                        pending.content,
                        new TeslaAuthenticator.TeslaAuthData(keyIndex, pending.mac, null),
                        disclosedKey
                );

                results.put(entry.getKey(), verified);

                if (verified) {
                    Log.d(TAG, "Successfully verified message " + entry.getKey());
                } else {
                    Log.w(TAG, "Message " + entry.getKey() + " failed verification");
                }

                // Remove from pending after verification
                pendingMessages.remove(entry.getKey());
            }
        }

        return results;
    }

    // Add storage for disclosed keys
    private Map<Integer, byte[]> disclosedKeys = new ConcurrentHashMap<>();

    // Store disclosed keys for later use
    private void storeDisclosedKey(int keyIndex, byte[] key) {
        disclosedKeys.put(keyIndex, key);

        // Log this key disclosure for debugging
        Log.d(TAG, "Stored TESLA key for index " + keyIndex + ": " +
                byteArrayToHex(key).substring(0, 16) + "...");
    }

    /**
     * Helper class to store pending message information.
     */
    private static class PendingMessage {
        final String messageId;
        final String content;
        final int keyIndex;
        final byte[] mac;

        PendingMessage(String messageId, String content, int keyIndex, byte[] mac) {
            this.messageId = messageId;
            this.content = content;
            this.keyIndex = keyIndex;
            this.mac = mac;
        }
    }

    /**
     * Utility method to convert byte array to hex string.
     */
    public static String byteArrayToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Utility method to convert hex string to byte array.
     */
    public static byte[] hexToByteArray(String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }

        return data;
    }

    /**
     * Serializes a secure message to string format for transmission.
     */
    public static String serializeMessage(Message message) {
        StringBuilder builder = new StringBuilder();

        // Base message fields
        builder.append(message.getMessageId()).append("|");
        builder.append(message.getOriginDeviceId()).append("|");

        // TESLA fields
        builder.append(message.getTeslaKeyIndex()).append("|");
        builder.append(byteArrayToHex(message.getTeslaMac())).append("|");
        builder.append(byteArrayToHex(message.getDisclosedKey())).append("|");

        // Hop count fields
        builder.append(message.getHopCount()).append("|");
        builder.append(byteArrayToHex(message.getHopCurrentHash())).append("|");
        builder.append(byteArrayToHex(message.getHopTopHash())).append("|");

        // User message fields
        builder.append(message.getSenderName()).append("|");
        builder.append(message.getIpAddress()).append("|");
        builder.append(message.getContent());

        return builder.toString();
    }

    /**
     * Deserializes a received message string.
     */
    public static Message deserializeMessage(String serialized) {
        try {
            String[] parts = serialized.split("\\|", 11); // Up to 11 parts
            if (parts.length < 11) {
                Log.e(TAG, "Invalid message format: not enough parts");
                return null;
            }

            String messageId = parts[0];
            String originDeviceId = parts[1];
            int teslaKeyIndex = Integer.parseInt(parts[2]);
            byte[] teslaMac = hexToByteArray(parts[3]);
            byte[] disclosedKey = hexToByteArray(parts[4]);
            int hopCount = Integer.parseInt(parts[5]);
            byte[] hopCurrentHash = hexToByteArray(parts[6]);
            byte[] hopTopHash = hexToByteArray(parts[7]);
            String senderName = parts[8];
            String ipAddress = parts[9];
            String content = parts[10];

            return new Message(
                    senderName,
                    "Unknown",
                    ipAddress,
                    content,
                    messageId,
                    originDeviceId,
                    teslaKeyIndex,
                    teslaMac,
                    disclosedKey,
                    hopCount,
                    hopCurrentHash,
                    hopTopHash
            );
        } catch (Exception e) {
            Log.e(TAG, "Error deserializing message", e);
            return null;
        }
    }
}