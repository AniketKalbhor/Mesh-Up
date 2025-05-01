package com.example.meshup.security;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles message authentication using the TESLA protocol.
 */
public class TeslaAuthenticator {
    private static final String TAG = "TeslaAuthenticator";

    private TeslaKeyChain keyChain;

    public TeslaAuthenticator(TeslaKeyChain keyChain) {
        this.keyChain = keyChain;
    }

    /**
     * Generates authentication data for a message.
     *
     * @param message The message to authenticate
     * @return Authentication data including key index and MAC
     */
    // Add detailed logging to the authenticate method
    public TeslaAuthData authenticate(String message) {
        int keyIndex = keyChain.getCurrentIndex() - 1; // Use next undisclosed key
        if (keyIndex < 0) {
            Log.e(TAG, "TESLA key chain exhausted");
            throw new RuntimeException("TESLA key chain exhausted");
        }

        byte[] key = keyChain.getAuthKey();
        byte[] mac = generateMac(message, key);

        Log.d(TAG, "Generated MAC for message using key index " + keyIndex +
                ", MAC: " + bytesToHex(mac).substring(0, 10) + "...");

        return new TeslaAuthData(keyIndex, mac, null);
    }

    // Add a helper method to convert bytes to hex
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Verifies a received message when the corresponding key is disclosed.
     *
     * @param message The message to verify
     * @param authData Authentication data including key index and MAC
     * @param disclosedKey The disclosed key for verification
     * @return true if the message is authentic, false otherwise
     */
    public boolean verify(String message, TeslaAuthData authData, byte[] disclosedKey) {
        if (authData == null || disclosedKey == null) {
            Log.e(TAG, "Invalid verification parameters");
            return false;
        }

        // Verify the disclosed key belongs to the chain
        if (!keyChain.verifyKey(disclosedKey, authData.keyIndex)) {
            Log.w(TAG, "Invalid disclosed key for index " + authData.keyIndex);
            return false;
        }

        // Verify the MAC using the disclosed key
        byte[] expectedMac = generateMac(message, disclosedKey);
        boolean isValid = Arrays.equals(expectedMac, authData.mac);

        Log.d(TAG, "Message verification result: " + isValid + " for key index " + authData.keyIndex);
        return isValid;
    }

    /**
     * Generate MAC for message authentication using HMAC-SHA256.
     */
    private byte[] generateMac(String message, byte[] key) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return hmac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.e(TAG, "MAC generation error", e);
            throw new RuntimeException("MAC generation error", e);
        }
    }

    /**
     * Inner class to hold TESLA authentication data.
     */
    public static class TeslaAuthData {
        public final int keyIndex;
        public final byte[] mac;
        public byte[] disclosedKey; // Null when sending, filled when key disclosed

        public TeslaAuthData(int keyIndex, byte[] mac, byte[] disclosedKey) {
            this.keyIndex = keyIndex;
            this.mac = mac;
            this.disclosedKey = disclosedKey;
        }
    }
}