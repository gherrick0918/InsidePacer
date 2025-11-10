#!/system/bin/sh
set -e

# --- START: Setup and Force Awake ---
# 1. Set display timeout to maximum (68 years) to override system settings
settings put system screen_off_timeout 2147483647

# 2. Enable 'Stay Awake' mode (Developer Options setting)
svc power stayon true

# --- Lockscreen Test ---
# lock_settings set-pin 1234 || true  # COMMENTED OUT due to root permission requirement
input keyevent 26                                   # screen off
cmd statusbar expand-notifications || true
sleep 2
input keyevent 26                                   # screen on (NOW ON LOCK SCREEN)
sleep 120                                           # PAUSE: Wait 120 seconds to view content
input swipe 500 1700 500 300                        # swipe up
input text 1234 && input keyevent 66                # enter pin and hit enter

# --- END: Cleanup ---
# 3. Restore a sensible display timeout (e.g., 30 seconds = 30000ms)
settings put system screen_off_timeout 30000

# 4. Disable 'Stay Awake' mode
svc power stayon false

echo "Lock-screen smoke complete."