#!/usr/bin/env bash
set -e
adb devices
adb shell svc power stayon false
adb shell lock_settings set-pin 1234 || true
adb shell input keyevent 26                   # screen off
adb shell cmd statusbar expand-notifications || true
sleep 2
adb shell input keyevent 26                   # screen on
adb shell input swipe 500 1700 500 300        # swipe up
adb shell input text 1234 && adb shell input keyevent 66
echo "Lock-screen smoke complete."
