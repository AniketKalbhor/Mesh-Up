package com.example.meshup.security;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Protects hop count in routing messages to prevent malicious reduction.
 */
public class HopCountProtector {
    private static final String TAG = "HopCountProtector";
    private static final int MAX_HOP_COUNT = 10; // Configure based on your network size

    /**
     * Generate a new hash chain for a message's hop count protection.
     *
     * @param message The message being sent
     * @return Hop data containing initial state for message
     */
    public HopData generateHopChain(String message) {
        // Generate random seed
        byte[] seed = new byte[16];
        new SecureRandom().nextBytes(seed);

        // Generate hash chain
        byte[][] hashChain = new byte[MAX_HOP_COUNT + 1][];
        hashChain[MAX_HOP_COUNT] = seed;

        for (int i = MAX_HOP_COUNT - 1; i >= 0; i--) {
            hashChain[i] = hash(hashChain[i + 1]);
        }

        Log.d(TAG, "Generated hop chain with max hops " + MAX_HOP_COUNT);
        // Return data needed for message sending (hop count 0 for new message)
        return new HopData(0, hashChain[MAX_HOP_COUNT], hashChain[0]);
    }

    /**
     * Process and update hop data when forwarding a message.
     *
     * @param hopData Current hop data
     * @return Updated hop data for forwarding
     * @throws IllegalStateException if max hop count is reached
     */
    public HopData processForForwarding(HopData hopData) {
        // Check if max hop count reached
        if (hopData.hopCount >= MAX_HOP_COUNT) {
            Log.w(TAG, "Maximum hop count reached, cannot forward further");
            throw new IllegalStateException("Maximum hop count reached");
        }

        // Calculate expected hash for next hop
        byte[] nextHash = hash(hopData.currentHash);

        Log.d(TAG, "Processed message for forwarding, hop count: " + (hopData.hopCount + 1));
        // Update hop data
        return new HopData(hopData.hopCount + 1, nextHash, hopData.topHash);
    }

    /**
     * Verify hop count integrity by checking hash chain.
     *
     * @param hopData Hop data to verify
     * @return true if valid, false otherwise
     */
    // Add detailed logging to the verifyHopData method
    public boolean verifyHopData(HopData hopData) {
        // Sanity check the hop data
        if (hopData == null || hopData.currentHash == null || hopData.topHash == null) {
            Log.e(TAG, "Invalid hop data for verification");
            return false;
        }

        if (hopData.hopCount > MAX_HOP_COUNT) {
            Log.w(TAG, "Hop count exceeds maximum: " + hopData.hopCount);
            return false;
        }

        // Compute top hash from current hash and hop count
        byte[] computed = hopData.currentHash;
        for (int i = 0; i < MAX_HOP_COUNT - hopData.hopCount; i++) {
            computed = hash(computed);
        }

        // Verify computed hash matches top hash
        boolean isValid = java.util.Arrays.equals(computed, hopData.topHash);

        String currentHashHex = bytesToHex(hopData.currentHash).substring(0, 8);
        String topHashHex = bytesToHex(hopData.topHash).substring(0, 8);
        String computedHashHex = bytesToHex(computed).substring(0, 8);

        Log.d(TAG, String.format("Hop verification: %s, count: %d, current: %s..., top: %s..., computed: %s...",
                isValid ? "PASS" : "FAIL", hopData.hopCount,
                currentHashHex, topHashHex, computedHashHex));

        return isValid;
    }

    // Add helper method to convert bytes to hex
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Hash function for the hash chain.
     */
    private byte[] hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Hashing error", e);
            throw new RuntimeException("Hashing error", e);
        }
    }

    /**
     * Inner class to hold hop count data.
     */
    public static class HopData {
        public final int hopCount;
        public final byte[] currentHash;
        public final byte[] topHash;

        public HopData(int hopCount, byte[] currentHash, byte[] topHash) {
            this.hopCount = hopCount;
            this.currentHash = currentHash;
            this.topHash = topHash;
        }
    }
}