# CampusFit 0.3.0 - human exercise demos and offline countdowns

Download: https://raw.githubusercontent.com/NiiOkai32/himothy/081e880c8148dfdd0fedc8f789088af3920ad3a0/CampusFit-0.3.0-download.zip

Extract the ZIP, then install CampusFit.apk on Android 8.0 or later. Version code 3
uses the existing signing certificate and can update the previous app without uninstalling.

- Animated, clothed 2D people demonstrate all 72 exercises in the library, details and player.
- Exercise-specific movements include standing, floor, dance and outdoor exercises.
- Spoken 3, 2, 1 and go before each movement, plus the final-three-second countdown.
- Countdown clips work offline, with a separate sound switch, optional form narration and haptics.
- Previews can be paused; disabled Android animations show a representative still pose.
- Count-ins add 3 seconds per movement without earning activity time or calories.

106 JVM tests pass; lint reports no errors and 9 existing warnings. Android 8 emulator
checks cover human illustrations, live animation, reduced motion and upgrade retention. The player puts start/pause beside the countdown
and moves audio controls into a settings sheet.
The signed universal APK includes ARM and x86 variants. Audio clips are bundled and
validated; speaker playback on a physical phone remains to be checked.

This is a debug/test build. The supplied Firebase client configuration is retained;
Cloud Functions deployment is still deferred. No screenshots or app source are included
in this artifact update. ZIP and APK checksums accompany the download.
