# Prepared cloud tooling

Use the existing `/workspace/himothy` checkout. Tasks are isolated already; do not
create a Git worktree unless the user requests one.

The environment filesystem retains the verified JDK, SDK, and Gradle caches under
`/workspace/tooling`. Activate them in each new shell:

```sh
export PATH="/workspace/tooling/node22/bin:$PATH"
export JAVA_HOME=/workspace/tooling/jdk/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/tooling/android-sdk
export GRADLE_USER_HOME=/workspace/tooling/gradle-home
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
cd /workspace/himothy
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 scripts/check_room_migration.py
npm ci --prefix functions --ignore-scripts --no-audit --no-fund
npm test --prefix functions
```

The Gradle user-home properties select the inherited environment proxy. Preserve CA
trust and TLS verification. Networked commands require the cloud execution tool's
network permission. Do not copy proxy authentication or credential values into files.

The app needs no persistent server for local demo development. To run, attach an
Android device or launch a separately configured emulator, install the debug APK, and
open CampusFit. A compiled APK does not establish device or Firebase integration
readiness. See README for personal Firebase configuration; do not invent credentials.

The published environment now permits `storage.googleapis.com`; the official Firestore
emulator download and all three Tracking, Social and Notifications integration suites
passed. Start their local services when needed with the README emulator command; do not
assume emulator processes survive publication or task restart. Keep TLS/checksum checks.
Read FIREBASE_CONNECTION.md and FIREBASE_DEPLOYMENT.md for the configured Android project,
remaining production access requirements and deployment steps. Real device and production
Firebase deployment are unverified; no live credentials were requested in chat.
