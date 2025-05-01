package com.example.meshup;

import java.io.Serializable;

/**
 * Represents a message in the mesh network with security metadata
 */
public class Message implements Serializable {
    private String senderName;
    private String macAddress;
    private String ipAddress;
    private String content;
    private String messageId;
    private String originDeviceId;

    // TESLA authentication fields
    private int teslaKeyIndex = -1;
    private byte[] teslaMac;
    private byte[] disclosedKey;

    // Hop count protection fields
    private int hopCount = 0;
    private byte[] hopCurrentHash;
    private byte[] hopTopHash;

    // Message status flags
    private boolean delivered = false;
    private boolean authenticated = false;
    private boolean pendingVerification = false;
    private long timestamp = System.currentTimeMillis();

    /**
     * Create a basic message without security
     */
    public Message(String senderName, String macAddress, String ipAddress, String content, String messageId) {
        this.senderName = senderName;
        this.macAddress = macAddress;
        this.ipAddress = ipAddress;
        this.content = content;
        this.messageId = messageId;
        this.originDeviceId = messageId.split("-")[0]; // Simple default if not specified
    }

    /**
     * Create a secure message with full metadata
     */
    public Message(String senderName, String macAddress, String ipAddress, String content,
                   String messageId, String originDeviceId, int teslaKeyIndex, byte[] teslaMac,
                   byte[] disclosedKey, int hopCount, byte[] hopCurrentHash, byte[] hopTopHash) {
        this.senderName = senderName;
        this.macAddress = macAddress;
        this.ipAddress = ipAddress;
        this.content = content;
        this.messageId = messageId;
        this.originDeviceId = originDeviceId;
        this.teslaKeyIndex = teslaKeyIndex;
        this.teslaMac = teslaMac;
        this.disclosedKey = disclosedKey;
        this.hopCount = hopCount;
        this.hopCurrentHash = hopCurrentHash;
        this.hopTopHash = hopTopHash;
    }

    /**
     * Check if this is a key disclosure message
     */
    public boolean isKeyDisclosure() {
        return disclosedKey != null && disclosedKey.length > 0;
    }

    // Getters and setters

    public String getSenderName() {
        return senderName;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getContent() {
        return content;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getOriginDeviceId() {
        return originDeviceId;
    }

    public int getTeslaKeyIndex() {
        return teslaKeyIndex;
    }

    public byte[] getTeslaMac() {
        return teslaMac;
    }

    public byte[] getDisclosedKey() {
        return disclosedKey;
    }

    public int getHopCount() {
        return hopCount;
    }

    public byte[] getHopCurrentHash() {
        return hopCurrentHash;
    }

    public byte[] getHopTopHash() {
        return hopTopHash;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        if (authenticated) {
            this.pendingVerification = false;
        }
    }

    public boolean isPendingVerification() {
        return pendingVerification;
    }

    public void setPendingVerification(boolean pendingVerification) {
        this.pendingVerification = pendingVerification;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Trust timeout - returns true if message should be trusted despite pending verification
     */
    public boolean isTrustTimeoutReached(long timeoutMs) {
        return pendingVerification && (System.currentTimeMillis() - timestamp > timeoutMs);
    }
}