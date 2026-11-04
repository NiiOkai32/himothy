# Validation · 3 November 2026

Implemented increments: foundation/Auth, Workouts, Tracking, Challenges/Leaderboards,
and Notifications/engagement. All five requested code increments are present. This
records current-instance development checks, not production or device readiness.

Verified in this cloud instance:

- Android `:app:assembleDebug` passed using JDK 21.0.12.1, Gradle wrapper 9.8.2,
  AGP 9.5.0, compile SDK 37.2, build tools 37.0.0; min SDK 26, target SDK 37.
- `:app:testDebugUnitTest`: **99 tests passed**, 0 failures/errors/skips. The earlier
  73 tests plus 26 reminder/calendar/mini-task/notification-route policy tests. Tests
  using repositories use fakes; this does not establish Android integration behavior.
- `:app:lintDebug`: **0 errors, 9 warnings**: five dependency update notices (Location,
  ZXing, SQLCipher and two coroutines artifacts), two bitmap KTX suggestions, release
  resource shrinking advice, and the existing incompatible Lifecycle custom lint registry.
  `UnsafeLifecycleWhenUsage` and `RepeatOnLifecycleWrongUsage` were skipped, not passed.
  The RemoteViews layout suppresses Activity-window overdraw inference because its host
  is the launcher. No functional checks were disabled to make the build pass.
- `python3 scripts/check_room_migration.py`: production SQL v1→v2→v3→v4→v5 preserves
  existing data and matches exported columns/defaults/keys; daily/session/mini uniqueness,
  timestamp/duration backfill, account-scoped table erasure and rejected-outbox isolation /
  explicit retry checks passed. This is SQLite, not Android Room+SQLCipher integration.
- Node 22.23.4 `npm test --prefix functions`: **57 tests passed**, 0 failures/skips.
  Tracking's 15 and Social's 30 tests cover calculations, safety, periods, ranks and actual
  callable identity guards. Notifications adds 12 policy/payload/actual callable-guard
  tests that reject invalid callers before any database/messaging RPC. These do not
  execute Firestore transactions, real JWT verification, SMS or scheduled jobs.
- Locked backend dependency installation reproduced with `npm ci --prefix functions
  --ignore-scripts --no-audit --no-fund` (799 packages). The pinned CLI dependency tree
  still emits upstream deprecation notices; installation succeeded.
- Backend files and authored integration tests pass `node --check` syntax validation.
- The debug APK signature verified with Android `apksigner` (v2 signature).
- The Gradle wrapper and retained Node runtime use verified official checksums.

## Firebase emulator integration now verified

After environment publication, the running enforced network policy includes
`storage.googleapis.com`. The official Firestore emulator v1.19.8 downloaded and started
with the pinned Firebase CLI 14.17.0, Node 22 and JDK 21. The prior HTTP 403 blocker is
resolved; no proxy/TLS/checksum bypass was used.

The README emulator command exited 0. **Three integration tests passed**, 0 failed or
skipped, executing the authored Tracking, Social and Notifications suites against local
Auth, Firestore and Functions. They exercise private/canonical tracking, callable identity,
server-owned social enrollment/ranks/consent/groups/erasure, token ownership transfer,
inbox deduplication, read-only notification fields and deletion barriers. Expected denied
writes produce PERMISSION_DENIED logs inside assertFails checks; these assertions passed.
Notification transport is injected/fake, and Functions emulator dispatch suppresses real
FCM. Hourly scheduler execution, deployed composite indexes/TTL behavior and real push
remain unverified. The emulator processes started for this check were shut down normally.

The 57 backend policy/guard tests passed again before this integration run. The CLI's
optional MOTD/remote-config fetch failed without preventing local tests. The pinned
firebase-functions SDK emitted an update notice; no dependency upgrades were made.

## Production access and checks not run

The user supplied project `campus-fit-5e050`, matching Android/OAuth configuration, and
reports that the default Firestore database is created. No live project resources were
changed by the agent. There is no configured Google outbound identity (identity manifest
has zero connections), and the Firebase CLI reports no authorized account metadata.
Generic credential selector variables alone do not establish project authorization.
Production deployment cannot proceed from this session without authorized access.
See FIREBASE_DEPLOYMENT.md for reviewed rules and operator deployment instructions.

Also unrun: physical-device Compose flows; Health Connect permissions/provider/totals;
real GPS/camera/model behavior; Android encrypted upgrades; QR/deep-link flows; Phone Auth
SMS and discovery; multi-device/offline social cache behavior; Android notification
permission/channels/Doze/reboot, FCM token rotation/delivery, local reminder opt-out and
account-switch races, widget launchers/staleness, UTC midnight mini-challenges and animation
accessibility; production Firebase and release R8 packaging. No production project, phone
provider, functions, rules, TTL policy or scheduler was deployed. See SOCIAL.md,
TRACKING.md and ENGAGEMENT.md for smoke-test checklists.

Ranks/XP use self-reported activity/weight. Neither pose estimates nor server calculations
confer independent verification. Weight ranks never award XP or habit trophies; Stair
Master remains locked. Mini completion is local/self-reported and adds no duplicate XP.
Timetable import deliberately supports the documented daily/weekly RFC5545 subset.
Widget values are visibly cached, not live standings. Android reminder timing is inexact.

The cloud installation instructions are retained, and startup instructions are saved
as a reviewable draft for the completed code increments. Saving does not apply network
policy, deploy Firebase, publish a snapshot or verify restoration in a new task.
Review/save relevant environment settings, then publish to retain the prepared filesystem.
Carry the outstanding production/device checks forward. Repository changes remain local; no commit,
push or production deployment was requested or performed.

## Firebase Android configuration follow-up

The user's `google-services.json` is now installed locally for project
`campus-fit-5e050`, package `com.campusfit`, and remains Git-ignored. Google Services
resource generation and APK assembly passed with that file. The initial file has no
OAuth clients; the build supplies a blank fallback web-client resource so Google login
stays disabled until a configured replacement is supplied. Generated project/app values
and the fallback were checked without logging API keys or configuration contents.

With this configuration, `assembleDebug`, all 99 JVM tests, `lintDebug` (0 errors,
9 warnings), `signingReport` and APK signature verification passed. Backend tests were
not rerun for this Android-only configuration change. No live Firebase calls, deployment,
new environment publication, real signup, SMS or push were performed. See
[FIREBASE_CONNECTION.md](FIREBASE_CONNECTION.md) for console steps and the current debug
signing fingerprints. Earlier emulator/network observations above are historical;
the newly published environment's emulator access has not been retested in this follow-up.

### Updated Google OAuth configuration

The replacement user-supplied JSON includes a web OAuth client and an Android OAuth
client matching the current debug APK's signing certificate. `assembleDebug` passed;
`testDebugUnitTest` was up-to-date and reused the previous 99-test result, rather than
executing a new run. Generated resources and the built APK contain the supplied web
OAuth client ID; the blank fallback is absent. APK signature and Android OAuth SHA-1
matching passed. Configuration contents/API keys were not logged, and the file remains
Git-ignored. No source logic changed, so lint/backend tests were not rerun. Actual
Google login, Firestore access and backend deployment remain unverified. Nothing was
published or deployed; console/device setup continues as described in FIREBASE_CONNECTION.md.

## Visual redesign 0.2.0

The redesigned universal APK (version code 2) built successfully. All 99 existing JVM
tests passed with 0 failures/errors/skips. Lint remains 0 errors and 9 existing warnings,
including the two skipped incompatible external Lifecycle checks described above. The
APK signature matches the previous build and the registered Firebase Android OAuth
certificate. UI illustrations and the licensed Outfit font are bundled offline.

A local Android 8/API 26 emulator exercised welcome, email sheet opening, three-step
onboarding with explicit health acknowledgment, local profile saving, home navigation,
workout library, quick-plan generation/player entry, and form tips pausing a running
workout. Updating the test APK retained the existing local demo profile. Reminder and
badge detail sheets opened/dismissed; setup steps reset their scroll position and the
email sheet opens expanded with the sign-in action visible. Light/dark appearance and
reduced-motion rendering were inspected. Screenshots use a fictional local demo profile.

For emulator transfer speed, a package containing only x86_64 native libraries was
used. Its DEX and resource-table checksums match the universal download. The distributed
APK retains all native ABIs. The first Android 11/API 30 software emulator showed system
startup ANRs before app installation; it was replaced by the usable API 26 image. No
app behavior on that API 30 attempt is claimed. Physical-device performance, sensors,
live Google/email authentication and remote push still require their existing checks.
No Firebase rules/functions deployment, billing change or cloud environment publication
was performed for this UI work. Only APK download artifacts/previews are updated on GitHub.

An extra font-scale check initially failed an assumed minimum heading height. The
emulator's active configuration was confirmed at fontScale 1.4; the body label grew
from one line (22 px) to wrapped larger text (69 px), while the heading remained 50 px.
The fixed heading-height assertion was not a valid large-text check. A fresh screenshot
was captured for manual review; this is not a claim of exhaustive accessibility testing.
The configured font scale was then restored. No screenshot containing the demo profile
name was published: automatic approval review rejected that optional upload, and the
public update includes only APK/checksum artifacts and download notes.

## Human exercise demos and countdowns 0.3.0

The universal debug APK (version code 3) builds successfully. All 106 JVM tests pass;
lint reports 0 errors and the same 9 existing warnings. New regressions cover a
three-second preparation with no elapsed/activity/calorie credit, movement/rest
transitions, pausing/skipping during preparation, older JSON checkpoints, both
countdown cue windows, and distinct squat/bridge/push-up mechanics in the catalogue.

All 72 exercises have authored normalized keyframes and a locally rendered clothed
2D human. Android 8 emulator screenshots show the standing/floor demos and compact
player. Live squat screenshots changed within the demo region; with Android animator
scale zero, useful representative still poses remain. Updating retained the existing
local profile and paused workout. The separate count-in displays 3 before movement.
The final slim emulator package's DEX and resources match the distributed universal
APK, which contains arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries.

Short English digit/go WAVs and a finish chime are bundled; clips are mono 22,050 Hz,
under 0.4 seconds each. No runtime TTS installation is needed for countdown numbers.
The emulator ran with audio output disabled, so actual speaker playback and volume
mixing on a physical phone remain unverified. No app crash or audio-loading error
was observed in the inspected AndroidRuntime/SoundPool/AudioTrack error log.

The public APK ZIP was downloaded without authentication and both ZIP and enclosed
APK SHA-256 checksums match the frozen local release. The signing certificate matches
the preceding build and registered Android OAuth certificate. Only APK/checksum files
and download instructions were pushed; screenshots and app source remain local. No
Firebase deployment, billing change, or cloud environment publication was performed.

The final player smoke test passed: skip into a paused movement showed the new 3-second
preparation, the Countdown sound switch toggled off/on, starting showed the count-in
and Go transition, and pausing stopped at 27 seconds remaining. The initial switch
assertion selected its uncheckable description child; selecting the actual checkable
parent fixed the test without an app change. The inspected audio error log was empty.

## Solid 3D exercise demos 0.4.0

The universal version-code-4 debug APK builds successfully. All 111 JVM tests pass,
including new tests for all 72 XYZ rigs, closed motion cycles, sagittal squat/knee
movement, opposite torso orientation in face-up/face-down exercises, and finite
volumetric meshes with unit normals. Lint reports 0 errors and 11 warnings: the
previous 9 plus a programmatic-view constructor/tooling notice and a bitmap KTX
suggestion. The two incompatible external Lifecycle lint checks remain skipped.

Native OpenGL ES 2 renders a solid clothed human with perspective, depth testing,
per-pixel lighting and a stage/contact shadow. A single worker/context serves all
views. The Android 8 emulator rendered library standing/floor models and a larger
squat detail, with camera turn/reset and turning while paused checked. Screenshots
are retained locally. The existing local profile and paused workout survived update.

A first playback check used the emulator's persisted animator scale of zero and
therefore showed a still pose. Switching the global setting also exposed a cache
race in ValueAnimator.areAnimatorsEnabled(). The final view reads the actual global
setting initially and from its content observer, so re-enabling motion takes effect
without leaving the screen. A bounded graphics-startup timeout selects the same
mesh's labelled CPU still preview if a graphics surface never becomes ready.

The final emulator package's DEX and resources match the frozen universal APK. Its
signature matches earlier releases and the registered Android OAuth certificate.
No new third-party model/rendering dependency is needed; model/choreography source
and all audio are bundled. Physical-phone graphics performance and actual speaker
playback remain unverified. No Firebase deployment or environment publication was
performed for this change.

The final player smoke check confirmed live native 3D playback while the work timer
advanced from 26 to 23 seconds. Four captured frames changed 3,473–4,076 pixels in
the model region, with the head and torso moving visibly. Logs confirm a hardware
TextureView, active graphics client and first mesh frame. The check began with OS
animations disabled for navigation, then re-enabled them on the player; the model
animated after that transition. The final universal APK matches the tested emulator
package's DEX/resources.

The software emulator's System UI encountered startup timeouts and UI automation was
unreliable. Direct input and visual capture were used for the final check. Testing
also exposed temporary view detach handling: cleanup now happens on actual texture
destruction or permanent view release, and readiness is signalled by the rendered
mesh rather than an initial TextureView clear. These fixes prevent losing the render
client during Compose view changes. Screenshots and temporary debugging/input helpers
remain outside the source and public artifacts.
