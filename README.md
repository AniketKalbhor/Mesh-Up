# MeshUp: Secure Ad-Hoc Mesh Network for Emergency Communication

![MeshUp Logo](https://img.shields.io/badge/MeshUp-Secure%20Mesh%20Network-blue)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20ESP8266-orange)
![Status](https://img.shields.io/badge/status-experimental-yellow)

## Overview

MeshUp is a resilient, infrastructure-independent mesh network system designed for emergency communications when traditional networks fail. This project implements a comprehensive security approach combining TESLA authentication and hop count protection to provide secure messaging without centralized infrastructure.

### Key Features

- **Infrastructure-Independent Communication**: Functions without cellular networks or internet
- **Cross-Platform Compatibility**: Android, Java Desktop, and ESP8266 clients
- **TESLA Authentication**: Broadcast authentication with delayed key disclosure
- **Hop Count Protection**: Prevents routing attacks with hash chains
- **Emergency SOS Feature**: Location sharing and emergency contacts
- **Network Visualization**: Real-time mesh network topology display

## System Architecture

MeshUp operates as a fully decentralized system where each device functions as both a client and a relay node:

```
┌─────────────────────┐     ┌─────────────────────┐
│                     │     │                     │
│  Android Device     │◄────►  ESP8266 Node       │
│  - TESLA Auth       │     │  - Message Relay    │
│  - UI Interface     │     │  - Network Discovery │
│                     │     │                     │
└─────────┬───────────┘     └─────────┬───────────┘
          │                           │
          │                           │
          ▼                           ▼
┌─────────────────────┐     ┌─────────────────────┐
│                     │     │                     │
│  Desktop Client     │◄────►  Android Device     │
│  - Network Monitor  │     │  - TESLA Auth       │
│  - Message Interface│     │  - UI Interface     │
│                     │     │                     │
└─────────────────────┘     └─────────────────────┘
```

### Components

1. **ESP8266 Nodes**: Lightweight relay nodes providing network extension
2. **Android Client**: Full-featured messaging app with security protocols
3. **Java Desktop Client**: Monitoring and messaging interface
4. **Communication Protocol**: UDP-based with flooding and AODV routing

## Security Implementation

### TESLA Authentication

MeshUp implements the TESLA (Timed Efficient Stream Loss-tolerant Authentication) protocol for secure broadcast messaging:

1. **Key Chain Generation**: One-way hash chain of keys (SHA-256)
2. **Message Authentication**: HMAC-SHA256 with undisclosed keys
3. **Delayed Key Disclosure**: Time-based key reveal for verification
4. **Buffered Verification**: Messages verified when keys are disclosed

### Hop Count Protection

A cryptographic hash chain mechanism to secure routing information:

1. **Hash Chain Generation**: For each message at origination
2. **Hop Count Increment**: With hash verification at each node
3. **Path Integrity Verification**: Prevents wormhole and sinkhole attacks

## Getting Started

### Prerequisites

- Android Studio 4.0+
- Arduino IDE with ESP8266 support
- JDK 11+

### Building Android Client

1. Clone the repository
   ```bash
   git clone https://github.com/yourusername/MeshUp.git
   ```
2. Open the project in Android Studio
3. Build and install on your Android device

### Building ESP8266 Node

1. Open `ESPCode.ino` in Arduino IDE
2. Install required libraries:
   - ESP8266WiFi
   - WiFiUDP
3. Upload to your ESP8266 device

### Running Desktop Client

1. Compile the Java client:
   ```bash
   javac MeshNetworkClient.java
   ```
2. Run the application:
   ```bash
   java MeshNetworkClient
   ```

## Usage

### Android Client

1. Start the app and enter your username
2. Configure emergency contacts if prompted
3. The app automatically connects to available mesh networks
4. Send messages which are automatically secured and forwarded

### Security Status

Access the security menu to view:
- TESLA key chain status
- Hop count protection metrics
- Message verification statistics
- Security log details

### Emergency SOS

Press the SOS button to:
- Send your location to emergency contacts
- Broadcast distress signal through the mesh network
- Include device information for responders

## Architecture Details

### Message Format

Secure messages contain 11 fields to facilitate routing and security:

```
messageId|originDeviceId|teslaKeyIndex|teslaMac|disclosedKey|hopCount|hopCurrentHash|hopTopHash|senderName|ipAddress|content
```

### Message Flow

1. **Composition**: User creates message
2. **Security**: TESLA auth and hop count protection applied
3. **Broadcast**: Message sent to all connected nodes
4. **Verification**: Recipients buffer until key disclosure
5. **Forwarding**: Message rebroadcast with updated hop count

## Research Background

This project implements concepts from the paper "Securing Ad-Hoc Mesh Network Communication in Emergency Scenarios: Implementation of TESLA Authentication and Hop Count Protection", addressing security vulnerabilities while maintaining operational efficiency on resource-constrained devices.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Adrian Perrig for the TESLA protocol
- The ESP8266 community
- Android developers community
- Contributors to the research paper

## Contact

For questions or contributions, please contact [your email address]
