#!/bin/bash

# OpenClaw Enhanced Android Node Setup Script

set -e

echo "🚀 Setting up OpenClaw Enhanced Android Node..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
print_status "Checking prerequisites..."

# Check if Android SDK is available
if ! command -v adb &> /dev/null; then
    print_error "ADB not found. Please install Android SDK and add to PATH"
    exit 1
fi

# Check if Gradle is available or use wrapper
if [ -f "./gradlew" ]; then
    GRADLE="./gradlew"
    print_success "Using Gradle wrapper"
else
    if command -v gradle &> /dev/null; then
        GRADLE="gradle"
        print_success "Using system Gradle"
    else
        print_error "Gradle not found. Please install Gradle or use Android Studio"
        exit 1
    fi
fi

# Check for connected Android device
print_status "Checking for connected Android devices..."
DEVICES=$(adb devices -l | grep -v "List of devices attached" | grep "device" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    print_warning "No Android devices connected"
    print_status "Please connect your device with USB debugging enabled"
    echo "Steps to enable USB debugging:"
    echo "1. Go to Settings → About Phone"
    echo "2. Tap Build Number 7 times to enable Developer Options"  
    echo "3. Go to Settings → Developer Options"
    echo "4. Enable USB Debugging"
    echo "5. Connect device via USB and accept debugging prompt"
    echo ""
    read -p "Press Enter when device is connected and debugging enabled..."
    
    # Check again
    DEVICES=$(adb devices -l | grep -v "List of devices attached" | grep "device" | wc -l)
    if [ "$DEVICES" -eq 0 ]; then
        print_error "Still no devices found. Exiting."
        exit 1
    fi
fi

print_success "Found $DEVICES Android device(s)"

# List connected devices
print_status "Connected devices:"
adb devices -l | grep "device" | while read device; do
    echo "  • $device"
done

# Build the APK
print_status "Building OpenClaw Enhanced Android APK..."
$GRADLE assembleDebug

if [ $? -ne 0 ]; then
    print_error "Build failed. Please check the error messages above."
    exit 1
fi

print_success "APK built successfully"

# Install APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    print_error "APK not found at $APK_PATH"
    exit 1
fi

print_status "Installing APK on connected device(s)..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    print_success "APK installed successfully"
else
    print_error "APK installation failed"
    exit 1
fi

# Instructions for manual setup
print_status "🎯 Next Steps - Manual Setup Required:"
echo ""
echo "1. On your Android device, open 'OpenClaw Enhanced' app"
echo ""
echo "2. Enable Accessibility Service:"
echo "   • Tap 'Open Accessibility Settings'"  
echo "   • Find 'OpenClaw Enhanced Node'"
echo "   • Toggle it ON"
echo "   • Accept the warning prompt"
echo ""
echo "3. Enable Overlay Permission:"
echo "   • Tap 'Open Overlay Settings'"
echo "   • Find 'OpenClaw Enhanced'"
echo "   • Toggle 'Allow display over other apps' ON"
echo ""
echo "4. Start the Node Service:"
echo "   • Return to OpenClaw Enhanced app"
echo "   • Verify both permissions show ✓ Enabled"
echo "   • Tap 'Start Node Service'"
echo "   • Check that service shows ✓ Running"
echo ""

# OpenClaw Gateway connection info
print_status "📡 OpenClaw Gateway Connection:"
echo ""
echo "The enhanced node will connect to OpenClaw Gateway at:"
echo "  • Host: 127.0.0.1 (localhost)"  
echo "  • Port: 18789 (default)"
echo ""
echo "Make sure your OpenClaw Gateway is running:"
echo "  openclaw gateway --port 18789 --verbose"
echo ""

# Test command examples
print_status "🧪 Test Commands:"
echo ""
echo "Once the service is running, test with these commands:"
echo ""
echo "# List connected nodes"
echo "openclaw nodes status"
echo ""
echo "# Launch TikTok app"
echo "openclaw nodes invoke --node \"Samsung A25 Enhanced\" --command \"app.launch\" \\\\"
echo "  --params '{\"packageName\":\"com.zhiliaoapp.musically\"}'"
echo ""
echo "# Perform tap"
echo "openclaw nodes invoke --node \"Samsung A25 Enhanced\" --command \"input.tap\" \\\\"
echo "  --params '{\"x\":540,\"y\":1200}'"
echo ""

print_success "Setup completed! Follow the manual steps above to finish configuration."
echo ""
print_status "For troubleshooting, see README.md"