# CampusFit

An Android fitness app for the gaps between lectures. **Implemented: foundation and all five requested modules: Auth, Workouts, Tracking, Challenges/Leaderboards, and Notifications/engagement.** Kotlin, Compose Material 3, MVVM, Hilt, encrypted Room, Firebase.

[Download the latest Android test build](DOWNLOADS.md).

For this prepared cloud machine, see [tool activation](docs/CLOUD_DEVELOPMENT.md).

Read [architecture and deliverables 1–6](docs/ARCHITECTURE.md) for the folder structure,
Firestore schema/security model, Room entities/DAOs, dependencies, navigation and scope.

## Exercise demos and countdowns · 0.3

All 72 moves now feature a clothed 2D human, animated in the library and player.
Offline spoken 3–2–1 count-ins, “go”, final-three-second countdowns and a finish chime
have an independent sound switch. Count-ins add preparation time without earning
movement credit. See [exercise demos](docs/EXERCISE_DEMOS.md).

## Visual update · 0.2

A brighter lime-and-violet theme, an offline illustrated mascot, Outfit typography,
visual workout cards, a three-step profile flow and tap-to-open detail sheets replace
long default-screen explanations. Quest/trophy celebrations have their own pop-ups.
Health guidance, explicit sharing consent, recording state and deletion confirmation
remain available where needed. See [UI redesign](docs/UI_REDESIGN.md).

## Run locally

1. Install Android Studio, JDK 17 or 21, Android platform 37.2, build tools 37.0.0 and
   platform-tools. Use the existing isolated checkout; a Git worktree is unnecessary.
2. Point `local.properties` at your SDK (`sdk.dir=/absolute/path/to/Android/sdk`), or
   set `ANDROID_HOME`. No SDK path belongs in version control.
3. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on an API 26+ device:
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Without Firebase config, tap **Explore local demo** and complete onboarding. This
   uses an encrypted device-local profile; it never claims to create a cloud account.
   Demo sessions survive process restart for optional local reminders; sign out to end the session.

## Workouts

The library contains [72 bundled exercises](app/src/main/assets/exercises.json) across
HIIT, strength, cardio, yoga/stretching, core, dance, walking/running and quiet dorm moves.
Browse by category, search by name or muscle, or choose low-noise/gentle options. Details
include animated 2D human demos, written steps, easier alternatives and MET estimates.

Tap **I have a few minutes**, select 2–20 minutes (including 5/10/15/20 presets), and
generate a plan. The duration includes a 30-second warm-up, 40-second work / 20-second
rest intervals, and a 30-second cool-down. Plans respect your saved fitness level and
exclude outdoor/stair movements. Check form, then start the player. Voice cues require
an installed offline Android TTS voice; the spoken countdown is bundled and works
without one. Countdown sound, haptics and voice can each be disabled. Each movement
gets an additional 3-second count-in outside the selected plan duration.

Leaving the player or backgrounding the app pauses activity. Reopen **Resume workout**
to continue. Process restart restores the latest checkpoint paused, without credit for
time away. Completed or early-ended workouts save actual movement seconds and estimated
calories locally; skips and rests earn no movement credit. Private workout cloud sync is available after deploying the Tracking backend. GPS routes
stay local, and optional camera counts remain estimates rather than verification. See [workout design and checks](docs/WORKOUTS.md).

For the SQL migration regression check, run `python3 scripts/check_room_migration.py`.

## Tracking

Progress includes one immutable weigh-in per UTC day, day/week/month trend windows,
workout history, unusual-loss flags and optional activity support labels. Weight logging
works offline. The activity-supported label uses self-reported sessions and is not
independent verification. No extreme weight change earns a reward.

Optional tools request permission when opened:

- **Health Connect:** read today's steps and exercise duration in the device's local
  timezone. Totals remain private, are not uploaded, and are not added again to sessions.
- **GPS:** foreground walks/runs up to 20 minutes; encrypted route checkpoints stay on
  the device. Only time, estimated burn and distance totals sync. Leaving pauses it.
- **Camera:** on-device ML Kit estimates squat, push-up and jumping-jack reps. Frames
  are discarded; counts and actual camera-session time can be saved. No recording/upload.

Read [Tracking architecture and device checklist](docs/TRACKING.md). These sensor flows
compile but still need real-device testing; no emulator/device was attached here.

## Connect Firebase

1. Create a Firebase project and Android app with package `com.campusfit`.
2. Register signing SHA-1 and SHA-256 fingerprints (`./gradlew signingReport`). Enable
   Email/Password and Google providers in Firebase Authentication, and configure the
   Google OAuth consent screen. University email uses the same verified email flow.
3. Download **your own** `google-services.json` into `app/` (ignored by Git). Rebuild:
   the Google services plugin only applies when this file exists, so demo builds need
   no fabricated keys. If the download has no web OAuth client yet, email sign-in can
   still build and the Google button stays disabled. Enable Google under Authentication,
   register the app signing fingerprints in Project settings, then download the updated
   JSON. Its generated `default_web_client_id` enables the Google button.
4. Create Firestore in production mode. Install Firebase CLI and deploy rules to your
   chosen project: `firebase deploy --only firestore:rules,firestore:indexes --project YOUR_PROJECT_ID`.
   This deployment is an explicit operator step, not something the app performs.
   Storage is deny-all until that feature is implemented; initialize it only when needed,
   then deploy its rules with `firebase deploy --only storage --project YOUR_PROJECT_ID`.
5. Install Node 22 and run `npm ci --prefix functions`, then `npm test --prefix functions`.
   Enable a Firebase billing plan supporting Cloud Functions, then deploy the **provided**
   `syncTracking` and `deleteCampusFitAccount` callables plus rules:
   `./functions/node_modules/.bin/firebase deploy --only functions,firestore:rules,firestore:indexes --project YOUR_PROJECT_ID`.
   Both client and functions use `us-central1`. Deploy before enabling cloud tracking;
   no admin key belongs in the APK. Production deployment was not performed here.
6. Enable Firestore TTL on `accountTombstones.expiresAt` if you want the minimal deletion
   marker to expire after 24 hours. Without TTL, the UID/timestamp marker stays until
   administrative cleanup; it contains no health records. Keep it for at least one hour
   to block still-valid pre-deletion ID tokens from recreating erased records.
7. Use test accounts to verify signup, email verification, reset delivery, Google
   sign-in, profile recovery after reinstall, offline profile edits and deletion.
   Enable email enumeration protection and an appropriate Firebase password policy.

Firebase's Android configuration is not an admin credential. Never embed service-account
keys, private API secrets or administrative credentials in the APK or repository.
Real Auth/cloud behavior has not been validated without a configured Firebase project.

## Privacy and health

SQLCipher encrypts Room; Android Keystore wraps its random passphrase. Android backups
are disabled. Firestore profiles are owner-only; no raw health fields are public. Camera, foreground location and Health Connect read permissions are optional and
requested only for the selected tools. No contacts, background location, or health-write
permission is requested.
Passwords are not saved to UI state. Weight competition defaults off. Underweight goals
are rejected, gradual changes encouraged, and screening guidance is shown at onboarding.
Rest-day choices include local reminder suppression and a separately protected UTC weekly social rest day. Activity streaks and rewards use the server policies described below.

Account deletion calls the server to block the UID, recursively erase private cloud
records and delete Auth, then removes local profiles, weigh-ins, sessions, routes and
integration preferences. A pending local deletion marker stops further health writes. Cloud deletion requires online access and recent authentication; if it fails,
retry after signing in again. The recovery screen permits retry or clearing only the device copy. Social resources and notification token bindings are included in erasure; future
Storage features must extend server erasure for their additional paths. Uninstalling removes local
data but does not delete a cloud account.

## Validation and implementation status

HealthPolicy unit tests cover safe goals, invalid input, age, acknowledgement and gradual
timelines. Build/lint results and their limits are recorded in [validation](docs/VALIDATION.md). No device UI or
Firebase integration results should be inferred from JVM tests.

All five requested feature increments are implemented. Physical-device and configured
Firebase verification remain pending; this is not a production-readiness claim. See the
[engagement controls and device checklist](docs/ENGAGEMENT.md).

## Notifications, timetable and daily engagement

Open **Home → Movement reminders** to opt into local reminders, social push or widget
sharing independently. Import an `.ics` timetable or add weekly busy times, choose quiet
hours and availability, and pause reminders for a local rest day. Reminders check free
gaps and completed movement goals at delivery; Android may delay background work.
Timetables remain encrypted on-device. Push uses generic text and a private inbox.

The widget shows cached daily activity rank/streak with update times. Daily mini-challenges
accept gentle alternatives and require a logged minute or 30 estimated reps before
self-reported completion; they add no extra XP. New activity trophies and completed
workouts have animation-aware celebrations. Deploy `notificationAction` and the new event
and delivery triggers together with existing functions/rules/indexes. Confirm the three
`expiresAt` TTL policies described in ENGAGEMENT.md. Live FCM delivery was not tested.

## Challenges, friends and leagues

Use **Challenges → Social privacy** to save an alias and explicit sharing preferences.
Only opt-in campus activity is published. Friends require accepted requests; groups use
private invitation codes. Friend QR codes are generated/scanned on-device, and shared
links ask before sending a request. Optional phone discovery requires a linked verified
Firebase Auth phone, its provider setup, and a separate discovery opt-in. No address book
is uploaded. See [Social module design and setup](docs/SOCIAL.md).

Create daily/weekly/monthly activity challenges or opt into private weight challenges.
Raw weights stay private. Weight enrollment closes before the start, needs a pre-start
baseline and excludes unsafe loss; it earns no XP. Team averages, shared ranks, weekly
leagues, activity XP, streaks, badges and activity-win trophies are calculated by the
server. Local demo shows only actual device achievements, with no invented friends or
opponents. Stair Master stays locked until stair-specific tracking is implemented.

Deploy all Firebase functions/rules/indexes together, including the new game triggers,
`socialAction` and hourly `finalizeGames` scheduler. This requires the same billed
Firebase project and `us-central1` region used by Tracking. Enable Firebase Phone Auth
and required signing fingerprints only if you use optional SMS linking. No functions,
indexes, phone provider or scheduler were deployed or exercised in production here.

## Backend tests and deployment

`npm test --prefix functions` executes 57 policy/callable-guard tests. All three local
Firebase integration suites (Tracking, Social, Notifications) also passed:

```sh
./functions/node_modules/.bin/firebase emulators:exec --project demo-campusfit --only auth,firestore,functions 'npm run test:emulator --prefix functions'
```

The published environment now permits the official Firestore emulator download from
`storage.googleapis.com`; the previous HTTP 403 blocker is resolved. Notification tests
use a fake transport and emulator dispatch suppresses outbound FCM. These local tests
establish no production deployment or physical-device behavior.

For project `campus-fit-5e050`, follow [deployment steps](docs/FIREBASE_DEPLOYMENT.md).
The Android configuration includes matching Google OAuth clients. Production rules,
indexes/functions/TTL policies and billing have not been changed by this session;
deployment needs an authorized Firebase/Google identity. Keep credentials out of chat.
