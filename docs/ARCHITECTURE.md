# 1. Architecture and folder structure

CampusFit uses Kotlin, Compose Material 3, MVVM, Hilt, Room, and repository interfaces.
Feature modules are packages in one Android application module initially, allowing each
increment to compile independently. They can be extracted into Gradle modules later.

```text
app/src/main/java/com/campusfit/
  domain/engagement/ Gap/quiet-time policy, bounded iCalendar parser, daily mini-task policy
  data/engagement/   Account-bound reminders/inbox/device workers and data-only FCM
  feature/engagement/ Notification controls, private inbox, timetable forms and native widget
  domain/           Profile, safety policy, AuthRepository contract
  domain/social/    Social DTOs, friend-link validation, local ranking/demo policies
  data/social/      Account-bound callables, encrypted snapshots, phone linking
  feature/social/   Challenges, boards, leagues, achievements, friends/groups and QR
  domain/tracking/  UTC weight policy, GPS filtering, angle/rep algorithms
  data/tracking/    WorkManager sync, local tracking repository, Health Connect gateway
  feature/tracking/ Progress/history, GPS and opt-in camera screens
  domain/workouts/  Catalogue, exact-duration planner, pure timer and MET calculations
  data/workouts/    Asset seeding, encrypted checkpoints, idempotent completion
  data/auth/        Firebase account and private profile implementation
  data/local/       Encrypted Room database, entities, DAOs, Keystore wrapping
  di/               Hilt bindings
  feature/workouts/ Workout ViewModel, library/detail/generator/player, vector demos
  feature/auth/     Auth state and ViewModel
  ui/               Navigation, auth/onboarding/dashboard/settings screens
  ui/theme/         Accessible system light/dark Material 3 palettes
app/src/main/assets/exercises.json  72 exercise records and offline animation keyframes
app/src/test/       Auth, health, catalogue, planner, timer and workout lifecycle tests
functions/          Tracking, authoritative social policy/service, game triggers/finalizer, erasure
firestore.rules     Private profile/health reads, deny direct health writes
storage.rules       Deny uploads until the Storage feature is implemented
```

UI observes immutable StateFlow state with lifecycle-aware collection. ViewModels call
repository contracts; Room is the durable local source for profiles. Firebase Auth
maintains account sessions. Firestore queues profile writes offline in memory; encrypted Room retains pending profiles for replay after restart. Its unencrypted disk cache is disabled. The SQLCipher
passphrase is random, wrapped with an Android Keystore AES-GCM key, and stored in
noBackupFilesDir. Backups are disabled; no passwords are persisted by application code.
Unrecoverable Keystore/database failures fail closed rather than deleting user data.

## 2. Firestore data model and rules

Implemented: `users/{authUid}` contains `schemaVersion: 1` and `profileJson`, a private
serialized profile: uid, name, age, heightCm, weightKg, goalKg, level, minutes,
university, group, activityOnly, percentageRanking, healthAcknowledged. It is not
queryable public health data. Only the verified owner can read/write; authenticated
owners can request recursive deletion through the callable even if email verification status changes. Direct deletion and collection lists are denied.
Rules limit document shape and size; health calculations are client-side UX validation.

Private weigh-in/session collections below are implemented through callable functions;
social and notification resources use the implemented rules and callable policies detailed in SOCIAL.md and ENGAGEMENT.md:

| Path | Intended ownership / authority |
| --- | --- |
| users/{uid}/weighIns/{epochDay} | Callable writes one canonical private entry per UTC day; owner read; safety flags |
| users/{uid}/sessions/{uuid} | Callable validates/deduplicates self-reported totals; owner read |
| publicProfiles/{uid} | Opt-in alias and campus preferences; owner-only document, minimal peer aliases through callables |
| challenges/{id} | Membership, period, metric, timezone, baseline policy |
| challenges/{id}/members/{uid} | Backend computed, consent-aware aggregates and ranks |
| friendships/{id} | Bilateral accepted memberships |
| groups/{id} | Membership and team identity |
| users/{uid}/notifications/{id} | Private notifications, written by backend |
| users/{uid}/devices/{id} | Callable-only private FCM token registrations |
| notificationDeviceOwners/{hash} | Callable-only token-to-account binding |

The trusted backend computes period scores and game state. No client may write scores,
XP, ranks or independent verification labels. Self-reported logs do not prove weight
changes are authentic. Deletion tombstones the UID, erases shared social resources and
token bindings, recursively erases its private Firestore tree, and removes Auth. Future
Storage paths must extend erasure. See SOCIAL.md for actual query permissions: public
profile documents are owner-only, while peer lookup returns only minimal aliases.

## 3. Room entities and DAOs

`ProfileEntity`: user-specific profile with pending-sync marker. `WeighInEntity`: composite
(uid, epochDay) primary key with ABORT insertion, preventing silent same-day replacement.
`WorkoutSessionEntity`: UUID session, owner, start, actual movement seconds, truncated
whole movement minutes and estimated calories. `ExerciseEntity`: metadata, written
instructions and normalized animation keyframes. `WorkoutRunEntity`: private plan and
paused checkpoint JSON, owner, finish flag and checkpoint timestamp. Completion inserts
use IGNORE on the run's unique ID inside the same transaction as the checkpoint write.
Account deletion also removes checkpoints. DAO writes guard the current authenticated
owner and existing profile before changing private run/session data.

Room exports schemas 1–5. V5 adds encrypted timetable, notification preferences/inbox, mini completion, seen-award and reminder-day tables, with per-account erasure. V4 adds an encrypted per-user social snapshot cache. `MIGRATION_1_2` preserves old data, backfills legacy
session seconds from minutes, adds exercise metadata and creates the checkpoint table.
The SQLite migration regression script uses the real migration SQL and exported schema.
No destructive migration fallback is enabled. Tracking now uploads pending sessions through authenticated callables; direct client
health writes remain denied. V3 adds capture/sync metadata, outdoor routes, per-account
health consent and pending-deletion tables. See TRACKING.md for those migration details.

## 4. Build and dependencies

See root and app Gradle files. Min API 26; compile API 37.2 and target API 37 (stable platforms
confirmed against Google's current repository2-3 metadata). Gradle 9.8.2,
AGP 9.5.0 with built-in Kotlin/Compose compiler 2.2.10, Java bytecode target 17. JDK 17 or 21 can
run the build. Room uses KSP. Firebase dependencies use its BOM; Retrofit is prepared
for future HTTP APIs but no fake endpoint is configured.

## 5. Navigation and theme

Auth flow: loading → sign-in/signup → email verification → health/profile onboarding →
home. Google authentication shares onboarding with email/university email. The signed-in
NavHost has Home, Workouts, Progress, Challenges, Profile destinations plus workout-detail,
player, quick-workout, challenge-detail, friends, badges, notifications and edit-profile.
Auth, onboarding, settings, the introductory dashboard, workout library/details,
quick generator, guided player, Progress/history, GPS sessions and camera rep tools
are implemented now. Physical-device verification is still required.
The social and notification routes are implemented. Empty states do not invent ranks or activity.
Material 3 uses an energetic green palette with system dark/light mode, scrollable forms,
keyboard handling, semantic control labels and edge-to-edge safe insets.

## 6. Auth increment

Email signup/sign-in, verification/resend/reload, password reset, Google sign-in,
local-only demo, encrypted profile persistence, profile editing, optional activity-only
competition, percentage privacy preference, health screening, and deletion of the current
account's private cloud tree/Auth account and local health rows via the deletion callable. Sign-out retains encrypted local data.
University affiliation is self-reported; email verification proves mailbox control only.
Profile cloud writes queue offline. Fresh login, verification, reset and cloud deletion
need a connection. Google sign-in uses Credential Manager and the Google ID token provider.

## 7. Workouts increment

See [Workouts](WORKOUTS.md) for domain, persistence, timing and user flows. The versioned
72-exercise asset is available without Internet and materialized into Room. The generator
builds exactly timed sessions; the pure engine takes monotonic deltas. The player pauses
on foreground loss and persists paused recovery checkpoints. Actual activity survives
process restarts; clocks never continue in the background. Voice uses only installed
offline TTS voices. Exercise animations are bundled joint keyframes, not downloaded video.

## 8. Tracking increment

See [Tracking](TRACKING.md) for UTC day rules, private backend schema, background uploads,
Health Connect consent, foreground GPS, camera estimates and deletion races. The local
source of truth stays encrypted Room. Firebase functions enforce canonical IDs, validate
claims and bind every request to the intended authenticated UID. Raw route points and
camera frames are never synced. These claims do not confer verified competition scores.

## 9. Challenges and Leaderboards increment

See [Social](SOCIAL.md) for the complete data/read-permission table, safe period scoring,
consent, team averages, server-owned XP/streaks/trophies and cohort allocation. All shared
mutations use UID-bound callables, with private health records retained under users.
Directory lookup returns minimal aliases; campus publication and verified phone discovery
are opt-in. Weight standings are never public and never create XP or habit trophies.
Realtime signals refresh server-calculated snapshots without permitting client scores.

## 10. Notifications and engagement increment

See [Engagement](ENGAGEMENT.md) for opt-in notification permission/channels, account-bound
WorkManager reminders, local timetable import, quiet/class deferral, data-only FCM, private
inbox and token ownership, cached native widget, daily mini-challenges and celebrations.
The five requested code increments are complete. Physical-device, configured Firebase
and emulator integration checks remain outstanding as listed in VALIDATION.md.
