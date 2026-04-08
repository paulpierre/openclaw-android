# Pre-built APK Downloads

## Installation Instructions

### Option 1: Direct APK Installation (Coming Soon)
Due to the complexity of building Android APKs on this system (requires Java SDK, Android SDK, etc.), the pre-built APK will be added in a future update.

### Option 2: Build Yourself
To build the APK yourself:

1. **Install Android Studio** on your local machine
2. **Clone this repository**:
   ```bash
   git clone https://github.com/paulpierre/openclaw-android.git
   cd openclaw-android
   ```

3. **Open in Android Studio**:
   - Open Android Studio
   - File → Open → Select the `openclaw-android` directory
   - Wait for Gradle sync

4. **Build APK**:
   - Build → Generate Bundles/APKs → Build APK(s)
   - OR use terminal: `./gradlew assembleDebug`

5. **Install on device**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Option 3: Use Setup Script
The included `setup.sh` script will build and install automatically:
```bash
./setup.sh
```

## System Requirements

**For Building:**
- Android Studio 2023.1+
- Java 17+
- Android SDK API 34
- Connected Android device with USB debugging

**For Running:**
- Android 8.0+ (API 26)
- Accessibility service permission
- Display overlay permission

## Quick Start

1. Build and install APK (using one of the methods above)
2. Open "OpenClaw Enhanced" app on your device
3. Enable Accessibility Service: Settings → Accessibility → "OpenClaw Enhanced Node" → Enable
4. Enable Overlay Permission: Settings → Apps → Special Access → Display over other apps → Enable
5. Start Node Service in the app
6. Test from OpenClaw Gateway:
   ```bash
   openclaw nodes status
   openclaw nodes invoke --node "Samsung A25 Enhanced" --command "app.launch" \
     --params '{"packageName":"com.android.settings"}'
   ```

## Support

For build issues, see the main [README.md](../README.md) for detailed troubleshooting instructions.