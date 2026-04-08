#!/bin/bash

# Enhanced OpenClaw Android Build and Install Script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

echo "🚀 OpenClaw Enhanced Android Node - Build & Install"
echo "=================================================="

# Check for pre-built APK
APK_PATH="release/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    print_status "Found pre-built APK at $APK_PATH"
    USE_PREBUILT=true
else
    print_warning "No pre-built APK found. Will attempt to build from source."
    USE_PREBUILT=false
fi

# Check prerequisites
print_status "Checking prerequisites..."

# Check ADB
if ! command -v adb &> /dev/null; then
    print_error "ADB not found. Please install Android SDK and add to PATH"
    echo "Download from: https://developer.android.com/studio/command-line/adb"
    exit 1
fi

# Check for connected devices
DEVICES=$(adb devices -l | grep -v "List of devices attached" | grep "device" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    print_warning "No Android devices connected"
    print_status "Please connect your Samsung A25 with USB debugging enabled"
    echo ""
    echo "Steps to enable USB debugging:"
    echo "1. Go to Settings → About Phone → Tap Build Number 7 times"
    echo "2. Go to Settings → Developer Options → Enable USB Debugging"
    echo "3. Connect device via USB and accept debugging prompt"
    echo ""
    read -p "Press Enter when device is connected..."
    
    DEVICES=$(adb devices -l | grep -v "List of devices attached" | grep "device" | wc -l)
    if [ "$DEVICES" -eq 0 ]; then
        print_error "Still no devices found. Exiting."
        exit 1
    fi
fi

print_success "Found $DEVICES Android device(s) connected"
adb devices -l | grep "device"

# Installation method selection
if [ "$USE_PREBUILT" = true ]; then
    print_status "Installing pre-built APK..."
    
    adb install -r "$APK_PATH"
    
    if [ $? -eq 0 ]; then
        print_success "APK installed successfully!"
    else
        print_error "APK installation failed"
        exit 1
    fi
else
    # Build from source
    print_status "Building APK from source..."
    
    # Check for Java
    if ! command -v java &> /dev/null; then
        print_error "Java not found. Please install Java 17+ and add to PATH"
        echo "Download from: https://adoptopenjdk.net/"
        exit 1
    fi
    
    # Check for Gradle wrapper
    if [ ! -f "./gradlew" ]; then
        print_error "Gradle wrapper not found. Please ensure this is an Android project directory"
        exit 1
    fi
    
    # Build APK
    print_status "Building debug APK..."
    ./gradlew assembleDebug
    
    if [ $? -ne 0 ]; then
        print_error "Build failed. Please check the error messages above."
        print_status "You may need to:"
        echo "  • Install Android SDK"
        echo "  • Set ANDROID_HOME environment variable"  
        echo "  • Accept Android SDK licenses"
        exit 1
    fi
    
    # Install APK
    BUILT_APK="app/build/outputs/apk/debug/app-debug.apk"
    if [ ! -f "$BUILT_APK" ]; then
        print_error "Built APK not found at $BUILT_APK"
        exit 1
    fi
    
    print_status "Installing built APK..."
    adb install -r "$BUILT_APK"
    
    if [ $? -eq 0 ]; then
        print_success "APK built and installed successfully!"
    else
        print_error "APK installation failed"
        exit 1
    fi
fi

# Post-installation instructions
echo ""
print_success "🎉 Installation Complete!"
echo ""
print_status "📱 Next Steps on your Samsung A25:"
echo ""
echo "1. Open 'OpenClaw Enhanced' app"
echo ""
echo "2. Enable Accessibility Service:"
echo "   • Tap 'Open Accessibility Settings'"
echo "   • Find 'OpenClaw Enhanced Node'"  
echo "   • Toggle it ON and accept the warning"
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
echo ""
echo "5. Test from OpenClaw Gateway:"
echo "   openclaw nodes status"
echo "   openclaw nodes invoke --node \"Samsung A25 Enhanced\" --command \"app.launch\" \\"
echo "     --params '{\"packageName\":\"com.android.settings\"}'"
echo ""
print_status "🎯 Ready for TikTok automation!"
echo "Example TikTok commands:"
echo "  openclaw nodes invoke --node \"Samsung A25 Enhanced\" --command \"app.launch\" \\"
echo "    --params '{\"packageName\":\"com.zhiliaoapp.musically\"}'"
echo ""
print_status "For troubleshooting, see README.md"