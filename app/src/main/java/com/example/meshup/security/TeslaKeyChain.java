package com.example.meshup.security;

import android.util.Log;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Implements the TESLA broadcast authentication protocol's key chain generation and management.
 */
public class TeslaKeyChain {
    private static final String TAG = "TeslaKeyChain";

    private byte[][] keyChain;
    private int currentIndex;
    private long startTime;
    private int disclosureDelay; // in milliseconds

    /**
     * Creates a new TESLA key chain with the specified length and disclosure delay.
     *
     * @param length Total number of keys in the chain
     * @param disclosureDelayMs Time in milliseconds between key disclosures
     */
    public TeslaKeyChain(int length, int disclosureDelayMs) {
        this.keyChain = new byte[length][];
        this.currentIndex = length - 1;
        this.startTime = System.currentTimeMillis();
        this.disclosureDelay = disclosureDelayMs;

        // Generate random seed for last key
        byte[] seed = new byte[32]; // 256-bit key
        new SecureRandom().nextBytes(seed);
        keyChain[length - 1] = seed;

        // Generate rest of the chain by repeatedly hashing
        for (int i = length - 2; i >= 0; i--) {
            keyChain[i] = hash(keyChain[i + 1]);
        }

        Log.d(TAG, "Generated key chain with " + length + " keys, disclosure delay: " + disclosureDelayMs + "ms");
    }

    /**
     * Gets the current key index based on elapsed time since initialization.
     */
    public int getCurrentIndex() {
        long elapsed = System.currentTimeMillis() - startTime;
        int timeIntervals = (int)(elapsed / disclosureDelay);
        return Math.max(0, keyChain.length - 1 - timeIntervals);
    }

    /**
     * Gets the currently active key based on time.
     */
    public byte[] getCurrentKey() {
        int index = getCurrentIndex();
        return keyChain[index];
    }

    /**
     * Gets the key to use for authentication (one ahead of current disclosure).
     */
    public byte[] getAuthKey() {
        int authIndex = getCurrentIndex() - 1;
        if (authIndex < 0) {
            Log.w(TAG, "Key chain exhausted, no auth key available");
            return null; // Chain exhausted
        }
        return keyChain[authIndex];
    }

    /**
     * Gets a specific key by index (used for key disclosure).
     */
    public byte[] getKeyByIndex(int index) {
        if (index < 0 || index >= keyChain.length) {
            Log.e(TAG, "Invalid key index: " + index);
            return null;
        }
        return keyChain[index];
    }

    /**
     * Verifies a received key belongs to the chain by hashing it repeatedly.
     *
     * @param receivedKey The key to verify
     * @param receivedIndex The claimed index of the received key
     * @return true if the key is valid, false otherwise
     */
    public boolean verifyKey(byte[] receivedKey, int receivedIndex) {
        if (receivedKey == null) {
            Log.e(TAG, "Received null key for verification");
            return false;
        }

        // Must be previously undisclosed
        if (receivedIndex >= getCurrentIndex()) {
            Log.w(TAG, "Received key index " + receivedIndex + " has not been disclosed yet");
            return false;
        }

        // Verify by hashing repeatedly to a known key
        byte[] computed = receivedKey;
        int knownIndex = getCurrentIndex();

        for (int i = receivedIndex; i < knownIndex; i++) {
            computed = hash(computed);
        }

        boolean isValid = Arrays.equals(computed, keyChain[knownIndex]);
        Log.d(TAG, "Key verification result: " + isValid + " for index " + receivedIndex);
        return isValid;
    }

    /**
     * Gets the commitment value (root of chain) - public anchor that can be shared.
     */
    public byte[] getCommitment() {
        return keyChain[0];
    }

    /**
     * @return The disclosure delay in milliseconds
     */
    public int getDisclosureDelay() {
        return disclosureDelay;
    }

    /**
     * Simple hash function for key chain generation.
     */
    private byte[] hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            Log.e(TAG, "Hashing error", e);
            throw new RuntimeException("Hashing error", e);
        }
    }
}