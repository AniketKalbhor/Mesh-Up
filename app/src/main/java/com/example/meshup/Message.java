package com.example.meshup;

import java.util.Arrays;

public class Message {
    private String senderName;
    private String macAddress;
    private String ipAddress;
    private String content;
    private long timestamp;
    private String messageId;
    private boolean isDelivered;
    private boolean isAuthenticated;

    // TESLA authentication fields
    private int authInterval;
    private byte[] authMac;
    private byte[] disclosedKey;
    private int disclosureInterval;
    private String originDeviceId;

    public Message(String senderName, String macAddress, String ipAddress,
                   String content, String messageId) {
        this.senderName = senderName;
        this.macAddress = macAddress;
        this.ipAddress = ipAddress;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.messageId = messageId;
        this.isDelivered = false;
        this.isAuthenticated = false;
    }

    // Constructor with authentication data
    public Message(String senderName, String macAddress, String ipAddress,
                   String content, String messageId, String originDeviceId,
                   int authInterval, byte[] authMac, byte[] disclosedKey, int disclosureInterval) {
        this(senderName, macAddress, ipAddress, content, messageId);
        this.originDeviceId = originDeviceId;
        this.authInterval = authInterval;
        this.authMac = authMac != null ? Arrays.copyOf(authMac, authMac.length) : null;
        this.disclosedKey = disclosedKey != null ? Arrays.copyOf(disclosedKey, disclosedKey.length) : null;
        this.disclosureInterval = disclosureInterval;
    }

    // Getters and setters
    public String getSenderName() { return senderName; }
    public String getMacAddress() { return macAddress; }
    public String getIpAddress() { return ipAddress; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public String getMessageId() { return messageId; }
    public boolean isDelivered() { return isDelivered; }
    public void setDelivered(boolean delivered) { isDelivered = delivered; }

    // Authentication getters and setters
    public boolean isAuthenticated() { return isAuthenticated; }
    public void setAuthenticated(boolean authenticated) { isAuthenticated = authenticated; }
    public int getAuthInterval() { return authInterval; }
    public byte[] getAuthMac() { return authMac != null ? Arrays.copyOf(authMac, authMac.length) : null; }
    public byte[] getDisclosedKey() { return disclosedKey != null ? Arrays.copyOf(disclosedKey, disclosedKey.length) : null; }
    public int getDisclosureInterval() { return disclosureInterval; }
    public String getOriginDeviceId() { return originDeviceId; }
}