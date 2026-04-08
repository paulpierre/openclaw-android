# OpenClaw Enhanced Android Node

**An enhanced OpenClaw Android node with native app control capabilities.**

## Features

✅ **Native app launching** - Launch any Android app by package name  
✅ **Touch automation** - Perform taps, swipes, and gestures  
✅ **Text input** - Input text via accessibility service  
✅ **Key events** - Send key presses (BACK, HOME, etc.)  
✅ **UI element detection** - Find and interact with UI elements  
✅ **OpenClaw Gateway integration** - Full WebSocket protocol support  
✅ **No root required** - Uses Android Accessibility API

## Supported Commands

### App Control
- `app.launch` - Launch Android apps
- `app.list` - List installed apps

### Input Automation  
- `input.tap` - Perform touch at coordinates
- `input.text` - Input text via clipboard
- `input.key` - Send key events (BACK, HOME, etc.)

### UI Interaction
- `ui.findElement` - Find UI elements by description
- `ui.clickElement` - Click elements by description  
- `ui.getScreenContent` - Get screen accessibility tree

## Prerequisites

1. **Android 8.0+ (API 26)**
2. **Android Studio** or **Gradle command line**
3. **USB debugging enabled** on target device
4. **OpenClaw Gateway** running and accessible

## Installation

### 1. Clone and Build

```bash
# Clone the project
git clone <this-repo-url>
cd openclaw-enhanced-android

# Build APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 2. Enable Permissions

**On your Android device:**

1. **Enable Accessibility Service:**
   - Settings → Accessibility
   - Find "OpenClaw Enhanced Node" 
   - Toggle ON
   - Accept the warning

2. **Enable Overlay Permission:**
   - Settings → Apps → Special Access → Display over other apps
   - Find "OpenClaw Enhanced"
   - Toggle ON

### 3. Configure Gateway Connection

The app connects to OpenClaw Gateway at:
- **Host**: `127.0.0.1` (localhost)
- **Port**: `18789` (default OpenClaw port)

**For remote Gateway:**
Modify `GatewayConnection.kt`:
```kotlin
private const val DEFAULT_GATEWAY_HOST = "your-gateway-ip"
private const val DEFAULT_GATEWAY_PORT = 18789
```

### 4. Start the Service

1. Open **OpenClaw Enhanced** app
2. Verify all permissions are enabled (green checkmarks)
3. Tap **"Start Node Service"**
4. Check that service shows "✓ Running"

## Usage

### From OpenClaw Gateway

```bash
# Launch TikTok
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "app.launch" \\
  --params '{"packageName":"com.zhiliaoapp.musically"}'

# Perform tap at coordinates  
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.tap" \\
  --params '{"x":540,"y":1200}'

# Input text
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.text" \\
  --params '{"text":"sophie-allison@lnkrly.com"}'

# Send BACK key
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.key" \\
  --params '{"keycode":"BACK"}'

# Find element
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "ui.findElement" \\
  --params '{"description":"login button"}'

# Click element by description
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "ui.clickElement" \\
  --params '{"description":"sign in"}'
```

### TikTok Automation Example

```bash
# Complete TikTok login sequence
# 1. Launch TikTok
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "app.launch" \\
  --params '{"packageName":"com.zhiliaoapp.musically","waitForLaunch":true}'

# 2. Tap login area
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.tap" \\
  --params '{"x":540,"y":1400}'

# 3. Input email
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.text" \\
  --params '{"text":"sophie-allison@lnkrly.com"}'

# 4. Tap password field  
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.tap" \\
  --params '{"x":540,"y":800}'

# 5. Input password
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.text" \\
  --params '{"text":"Nv7!qR2@Lm8#Xp"}'

# 6. Tap login button
openclaw nodes invoke --node "Samsung A25 Enhanced" --command "input.tap" \\
  --params '{"x":540,"y":1000}'
```

## Development

### Project Structure

```
app/src/main/java/ai/openclaw/enhanced/
├── EnhancedNodeApplication.kt     # App initialization
├── controller/
│   ├── AppController.kt           # App launching logic  
│   ├── InputController.kt         # Touch & input automation
│   └── UIController.kt            # UI element detection
├── gateway/
│   └── GatewayConnection.kt       # WebSocket connection
├── model/
│   └── NodeCommand.kt             # Command data models
├── service/
│   ├── NodeService.kt             # Main background service
│   └── EnhancedAccessibilityService.kt  # Accessibility service
├── receiver/
│   └── AppLaunchReceiver.kt       # Broadcast receiver
└── ui/
    └── MainActivity.kt            # Main UI activity
```

### Adding New Commands

1. **Define command models** in `model/NodeCommand.kt`
2. **Implement logic** in appropriate controller
3. **Add command handling** in `GatewayConnection.kt`
4. **Update capabilities list** in node registration

### Dependencies

- **Kotlin Coroutines** - Async operations
- **Kotlinx Serialization** - JSON handling
- **Java-WebSocket** - WebSocket client
- **OkHttp3** - HTTP networking  
- **Timber** - Logging
- **AndroidX** - Modern Android components

## Troubleshooting

### Connection Issues
- Verify OpenClaw Gateway is running on port 18789
- Check device can reach Gateway IP/port
- Enable USB debugging if using localhost

### Permission Problems
- Accessibility service must be enabled for all functionality
- Overlay permission required for UI automation
- Some manufacturers require additional settings

### App Launch Failures
- Verify app package name is correct
- Check app is installed and enabled
- Some apps may have launch restrictions

### Gesture/Input Issues
- Accessibility service must be active
- Some UI elements may not be automatable
- Coordinate-based taps may need calibration

## Security Considerations

- **Accessibility service** has broad system access
- **Only install on trusted devices** 
- **Review permissions** before enabling
- **Limit network exposure** of Gateway if possible

## License

This project follows the same license as OpenClaw core.

## Contributing

1. Fork the repository
2. Create feature branch
3. Submit pull request with clear description

## Support

For issues specific to this enhanced node, please open GitHub issues.
For general OpenClaw support, see official documentation.