# Tracking increment

Implemented: daily private weigh-ins, weight windows/chart/history, local session history,
private background sync, Health Connect step/exercise totals, foreground GPS routes,
optional camera rep estimates, and account deletion covering the new private data.

## Data and time conventions

Room v3 preserves v1/v2 data. Weigh-ins add capture time and sync issues. Sessions add
source, movement seconds, distance and rep totals. Encrypted outdoor-run JSON contains
route points and a paused checkpoint; tracking preferences store per-account Health
Connect consent. A separate local deletion marker blocks writes while erasure is pending.
All sensitive payloads remain in SQLCipher. WorkManager input contains the UID and a
refresh flag, not health records or credentials.

Daily weights use UTC epoch days and one immutable entry per UID/day. Day/week/month
windows mean the last 1/7/30 UTC days, with recorded points only, actual date spacing and
no zero-filled missing weights. Short-term change is descriptive, never a reward. A
compound daily loss exceeding 2% is flagged; exactly 2% and gains are not. Out-of-order
cloud uploads update the next entry's classification. A five-minute self-reported
activity threshold may show activity-supported status only for an unflagged day. This
is not a medical or independent verification badge. The subsequent Social module now calculates consent-aware standings from these claims,
without treating them as independent verification.

The latest weigh-in is used for new workout burn estimates; existing sessions retain
their original weight snapshot. Calories use MET × 3.5 × kg / 200 × movement minutes.
No burn estimate is treated as a target or a reliable measure of workout quality.

## Private sync and authoritative daily uniqueness

Completed guided, GPS and camera sessions, plus weigh-ins, persist locally before an
upload is queued. Demo accounts never upload. Verified Firebase accounts use WorkManager
with connectivity constraints, exponential backoff and periodic upload recovery. History
is downloaded in bounded pages on account opening, explicit refresh or a weigh-in sync;
periodic session uploads avoid repeatedly downloading the full history. No client direct
write to private health collections is allowed by Firestore rules.

`functions/syncTracking` validates input and binds the intended UID to the authenticated
UID, preventing an account change during an asynchronous request from routing health data
to the newly signed-in account. All server transactions read the deletion tombstone.
Daily document IDs are UTC epoch days; first writer wins. A collision keeps the earlier
cloud entry and reports that fact to the device. Session UUIDs similarly provide retry
idempotence, with canonical server data replacing the local pending copy. Local entries
are not deleted on network failure. Permission/configuration errors keep a visible issue
and allow explicit retry. Server bounds reject invalid/future data; historical private
claims from 2000 onward remain syncable, without making them eligible challenge scores.

Server paths:

- `users/{uid}/weighIns/{epochDay}`: epochDay, weightKg, createdAt, flagged, receivedAt.
- `users/{uid}/sessions/{uuid}`: start/duration/burn/source/distance/reps, receivedAt,
  `verification: self_reported`. There are no client-created verified badges or scores.
- `accountTombstones/{uid}`: deletion timestamp and expiry only; admin-managed, no client
  access, no health data. Rules use it to block a still-valid old ID token after erasure.

Callables use `us-central1`. Deployment, billing/project setup and a real Firebase Android
configuration are required for live sync. Those operations were not performed here.
The server uses Firebase Admin only; its credentials never belong in the Android app.

## Deletion and race protection

Deletion requires recent authentication. The device first records a pending marker and
cancels its tracking work. The callable creates a tombstone, recursively deletes the UID's
private Firestore tree and deletes Firebase Auth. Only after confirmed success does the
device remove local private rows and the marker. Repeated requests are safe. SDK queue
writes are blocked by rules after the tombstone exists. Local save paths check both the
current UID and deletion marker before committing rows.

If deletion is interrupted, the recovery screen blocks new health writes and offers retry,
sign-in refresh, or removing only the device copy. Local-only removal retains the minimal
pending UID marker so a later login can finish cloud deletion. It does not claim cloud
success. This erases current-device and server records, not offline copies on other
phones or Health Connect records created by other apps. Social paths are now included in erasure; future Storage paths must also be included. Enable Firestore TTL on `expiresAt` for 24-hour marker
cleanup; without it the minimal marker is retained until operator cleanup. Never remove
it while an old token may still be valid (normally up to one hour).

## Health Connect

Supported where the platform/provider is available (Android 9+; unavailable on older
supported app devices). Access is optional, with explicit per-account consent and system
read permissions for steps and exercise sessions. Aggregates read only today's local-time
steps/exercise duration. They remain in UI memory, are not uploaded, and are not combined
with local workouts to award duplicated activity. Permission revocation clears displayed
totals at refresh. Disconnect revokes app read access and clears the preference, without
erasing provider records. No Health Connect write/background-read permission is requested.
A static privacy/rationale activity and Android 14+ permission usage alias are registered.
For Play distribution, declare these read scopes and supply the applicable privacy policy.

## GPS

Optional precise/coarse foreground location; no background permission or service. A
walk/run is limited to 20 minutes and uses monotonic time. Leaving, backgrounding or
permission loss pauses it. Checkpoints restore paused after restart. Exact route points
are stored only in encrypted Room; cloud data includes only totals. The offline sketch
uses separately scaled axes and is not a map or navigation aid.

Mock locations, invalid/stale coordinates, fixes worse than 35 m accuracy, implausible
speed jumps and jitter are rejected. Slow movement can accumulate across ordinary
accuracy radii without being incorrectly treated as a GPS outage. Gaps and pause/reboot
segments are never bridged into distance or route lines. Cached fixes from before resume
are ignored. Distance remains an estimate; foreground time can advance without a good
fix, so the UI asks users to pause when not moving. This is self-reported activity,
not independent verification. A hard process kill can lose the last in-flight checkpoint.

## Camera

CameraX supplies frames to the bundled on-device ML Kit pose detector (its official
beta API). Confidence gating, joint angles, hysteresis, neutral→extended/deep→neutral
cycles, missing-frame resets and cooldowns estimate squats, push-ups and jumping jacks.
Model outputs do not certify correct exercise form or prove that calories were burned.
Frames are never captured to files or uploaded. Camera resources and proxies are closed
when leaving. No usable pose means no movement-time credit; pause/background stops time.
Camera practices cap at 20 minutes and require explicit saving; unsaved practice is not
restored after process death. Saved totals use a distinct session UUID and source.

## Validation and remaining checks

Pure Kotlin tests cover UTC boundaries, invalid weights, fair percentage change, strict
2% thresholds, multi-day rates, flag/badge gating, haversine/date-line distance, poor/mock
fixes, slow movement, pause/reboot/gap handling, duration caps, rep cycles and bad geometry.
Node tests exercise numeric policy plus actual callable guards for missing/unverified
identity, target UID mismatch, invalid input and stale authentication. SQLite checks use
production migrations and exported v1/v2/v3 schemas to verify data/default/key preservation,
daily uniqueness, idempotent sessions and local erasure. Android encrypted migration is
not implied by the plain SQLite check.

The emulator integration test is authored but unrun: the Firestore binary download from
storage.googleapis.com was blocked with HTTP 403 by the cloud network policy. The domain
addition was saved to the environment draft, without bypassing the proxy. Once applied,
rerun the command in README to test actual rules, uploads, out-of-order flags, first-writer
collisions, cross-user denial, session idempotence, recursive deletion and old-token blocking.

Physical-device checks still required: permission denial/revocation and re-grant; Health
Connect install/update/missing provider and day totals; GPS under weak signals and app
backgrounding/reboot; camera framing, counts, unavailable camera and model errors; live
Firebase offline/reconnect/multi-device sync; deletion interruption/re-login; and TalkBack,
large fonts, rotation and dark mode. Neither compilation nor JVM tests claim those results.
