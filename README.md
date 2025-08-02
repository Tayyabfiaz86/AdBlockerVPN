# Ad Blocker VPN - Android App

A VPN-based ad blocker app for Android that uses the VpnService API to intercept and block ad domains.

## Features

- **VPN-based Ad Blocking**: Uses Android's VpnService to intercept network traffic
- **DNS Filtering**: Blocks ads by filtering DNS requests to known ad domains
- **Real-time Statistics**: Shows number of ads blocked in real-time
- **Modern UI**: Clean Material Design interface
- **Background Service**: Runs in foreground with persistent notification

## Prerequisites

- Android Studio or Cursor
- Android SDK (API 21+)
- Java JDK 8 or higher
- Physical Android device for testing (VPN doesn't work on emulator)

## Setup Instructions

### 1. Clone/Download Project
```bash
git clone <repository-url>
cd AdBlockerVPN
```

### 2. Open in Android Studio
- Open Android Studio
- Select "Open an existing Android Studio project"
- Navigate to the project folder and select it

### 3. Build and Run
```bash
# Using Gradle CLI
./gradlew assembleDebug

# Or using Android Studio
# Click "Run" button or press Shift+F10
```

### 4. Install on Device
- Enable USB Debugging on your Android device
- Connect device via USB
- Install the APK: `app/build/outputs/apk/debug/app-debug.apk`

## How It Works

1. **VPN Service**: Creates a virtual network interface using VpnService
2. **Traffic Interception**: All device traffic goes through the VPN
3. **DNS Filtering**: Analyzes DNS requests and blocks known ad domains
4. **Packet Processing**: Filters network packets in real-time
5. **Statistics**: Counts and displays blocked ads

## Ad Domains Blocked

The app currently blocks these ad domains:
- ads.google.com
- doubleclick.net
- googlesyndication.com
- googleadservices.com
- And more...

## Usage

1. **Start VPN**: Tap "Start VPN" button
2. **Grant Permission**: Allow VPN permission when prompted
3. **Monitor**: Watch the ads blocked counter increase
4. **Stop VPN**: Tap "Stop VPN" to disconnect

## Project Structure

```
app/
├── src/main/
│   ├── java/com/adblockervpn/app/
│   │   ├── MainActivity.kt          # Main UI
│   │   └── vpn/
│   │       └── AdBlockerVpnService.kt  # VPN service
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml    # Main UI layout
│   │   ├── values/
│   │   │   ├── strings.xml          # String resources
│   │   │   ├── colors.xml           # Color definitions
│   │   │   └── themes.xml           # App theme
│   │   └── drawable/
│   │       └── ic_vpn.xml           # VPN icon
│   └── AndroidManifest.xml          # App permissions & services
```

## Important Notes

- **Play Store Policy**: This app cannot be published on Google Play Store due to ad-blocking functionality
- **Distribution**: Share via APK file, GitHub releases, or other platforms
- **Testing**: Test on ad-heavy websites or free games from Play Store
- **Performance**: Monitor battery usage and performance impact

## Troubleshooting

### Common Issues:

1. **VPN Permission Denied**
   - Go to Settings > Apps > Ad Blocker VPN > Permissions
   - Enable "Display over other apps" and "VPN"

2. **App Not Working**
   - Ensure device is running Android 5.0+ (API 21+)
   - Check that USB Debugging is enabled
   - Verify VPN permission is granted

3. **Build Errors**
   - Sync project with Gradle files
   - Clean and rebuild project
   - Update Android SDK tools

## Development

### Adding More Ad Domains:
Edit `AdBlockerVpnService.kt` and add domains to the `adDomains` set:

```kotlin
private val adDomains = setOf(
    "ads.google.com",
    "doubleclick.net",
    // Add more domains here
    "your-ad-domain.com"
)
```

### Customizing UI:
- Edit `activity_main.xml` for layout changes
- Modify `colors.xml` for theme changes
- Update `strings.xml` for text changes

## License

This project is for educational purposes. Use responsibly and in accordance with local laws.

## Contributing

Feel free to submit issues and enhancement requests!

---


**Note**: This app is for educational purposes. Always respect website terms of service and local regulations. 

