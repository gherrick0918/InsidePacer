# Lock-screen testing checklist

Use a Google Play system image AVD and sign into the Play Store before running through these steps.

1. Start an InsidePacer session so that the foreground service is active.
2. Run the helper script to exercise the lock-screen flow:

   ```bash
   ./scripts/lockscreen_smoke.sh
   ```

3. (Optional) Disable doze/battery throttling for deeper testing:

   ```bash
   adb shell dumpsys battery unplug
   adb shell dumpsys deviceidle disable
   ```

4. When finished, re-enable the defaults:

   ```bash
   adb shell dumpsys deviceidle enable
   adb shell dumpsys battery reset
   ```
