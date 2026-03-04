# Future Science Terminal - Product Introduction

## Overview

**Future Science Terminal** is an AI-powered smart terminal software designed for educational settings. Built on Android, it enables hardware-to-license binding, remote configuration, theme content distribution, and OTA updates.

---

## Core Features

### 1. Device Management
- **Hardware Binding**: Auto-register on first network connection, unique MAC identification
- **License Binding**: Automatic license assignment, batch import supported
- **Status Monitoring**: Real-time online status and last active time

### 2. License Management
- **Batch Generation**: Custom prefix + quantity for quick license creation
- **Batch Import**: Paste license key list for import
- **Status Tracking**: View bound devices, SN, expiration dates

### 3. Theme & Content Management
- **Multi-Theme Support**: Create different themes with different AI agents
- **Image Slideshow**: Configure multiple images per theme, auto-rotate on terminal
- **One-Click Distribution**: Batch bind devices to themes

### 4. Data Analytics
- **Hot Questions**: Statistics on frequently asked questions
- **Keyword Analysis**: Extract user interest topics
- **Time Distribution**: Analyze peak usage hours

### 5. Emergency Notifications
- **Instant Push**: Send notifications to all or selected devices
- **Marquee Display**: Scrolling notification on terminal screen

### 6. OTA Remote Updates
- **Version Management**: Upload new APK versions
- **Force Update**: Optional forced update
- **Auto Download & Install**: Terminal handles update automatically

### 7. Bilingual Support
- **Chinese/English**: Admin panel supports CN/EN languages
- **Multi-language Terminal**: Terminal device can configure display language

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Terminal APP | Android Native (Java) |
| Backend | Python Flask + SQLite |
| Admin Panel | HTML5 + JavaScript (SPA) |
| Storage | Local SQLite (OSS expandable) |

---

## Deployment

1. **Server**: Deploy Flask service to cloud server (Aliyun recommended)
2. **Terminal**: Connect to network, auto-register
3. **Management**: Browser-based admin panel

---

## Use Cases

- School science labs/classrooms
- Science museum AI exhibits
- Community science terminals
- Enterprise showroom AI assistants

---

## Contact

For customization or technical support, please contact the development team.
