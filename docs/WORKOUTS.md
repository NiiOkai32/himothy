# Workouts increment

The Workouts feature provides a searchable 72-exercise library, detail/alternative
screens, interactive movement sketches, a 2–20 minute generator and a guided player.
It builds on the existing Auth/profile flow and encrypted Room database.

## Domain and catalogue

`domain/workouts` contains Exercise/Pose, WorkoutPlan/Stage, WorkoutRun, the repository
contract, generator, energy formula and pure timer engine. `assets/exercises.json` is
versioned source content: eight categories, three difficulty levels, at least three
instructions per exercise, target areas, low-noise/gentle/campus flags, alternative ID,
MET estimate, voice cue and two or more normalized pose keyframes. JSON is validated
before seeding Room. No network calls or media downloads are required for browsing.
The Canvas renderer interpolates all 13 joints and obeys the system animation setting;
users can also pause detail animations. These are schematic movement illustrations,
with written steps providing the form instructions, rather than recorded coaching videos.

MET entries are broad exercise-intensity estimates, not individually measured burn.
Calories = MET × 3.5 × saved weight kg / 200 × active minutes. UI labels estimates,
uses the profile's saved weight, and never treats burn as a measure of workout quality.
The run snapshots weight when started so later profile edits do not change past energy.

## Generator

A plan has 30 seconds of easy marching, (minutes − 1) cycles of 40 seconds of work and
20 seconds of rest, then 30 seconds of standing breathing. Its full elapsed duration
matches the requested minutes exactly. Work movements must fit the saved level,
selected category and low-noise/gentle filters. A shuffled eligible pool cycles without
adjacent repeats when more than one choice exists. Empty combinations show an error;
the planner never silently relaxes constraints. Campus walks, runs and stairs stay in
the library but are excluded from room-based plans; GPS recording comes in Tracking.

The detail screen can generate a two-minute single-exercise practice within the user's
saved level. Alternatives always exist and are no harder than the original movement.
Gentle options use standing/small-range movements; they are not medical injury treatment.
The player allows rest, pause, skip and ending early without shaming or fabricated XP.

## Player and persistence

The ViewModel serializes state changes with a Mutex. A supplied monotonic clock drives
250 ms foreground updates, with Room checkpoints every second and at boundaries/actions.
`WorkoutEngine` accounts for exact milliseconds across multiple interval boundaries.
Rest, skipped duration, pause time, time after completion, and background gaps earn no
movement credit. Pause captures the lifecycle event timestamp before waiting on storage;
slow storage cannot extend the credited duration into the background.

Every run has its own UUID, independent of the generated plan ID. New runs open paused
for instruction review. Leaving the player or receiving ON_STOP stops timing and voice,
then saves a paused checkpoint. Reloads always restore paused. Abrupt termination may
lose up to the last checkpoint interval (plus any in-flight write), rather than inventing
activity while away. A failed write pauses the workout, reports the failure, and permits
retry. Concurrent unfinished workouts cannot silently replace one another.

Finishing, ending early or skipping the last interval persists the checkpoint and inserts
a session in one Room transaction. The session ID is the run ID; repeated completion
writes use INSERT OR IGNORE, preventing duplicate history. The summary shows movement
and elapsed time separately, estimates actual movement burn, and confirms local saving.
A zero-movement run creates no activity record. Session seconds are retained precisely
to whole seconds; the older minutes field is floored, so sub-minute work is not rounded up.

TTS selects a locally installed voice in the device language or English, and is optional.
Unsupported/no offline voice leaves the timer usable and explains how to enable cues.
Haptic feedback uses the platform's feedback mechanism. Player departure shuts down TTS;
no microphone, foreground service, wake lock, or new runtime permission is required.
No auto-resume, screen-off workout counting or background fitness recording is implied.

## Scope and validation

Workouts save checkpoints/history on the device and mark completed sessions pending
sync. The subsequent [Tracking increment](TRACKING.md) now provides callable workout sync,
Health Connect, foreground GPS and optional camera reps. Direct Firestore session writes
remain denied;
no verified-activity badges, leaderboard scores, XP or streaks are awarded by this client.
Account deletion includes local runs and sessions. The Workouts repository queues local completion records for the Tracking sync worker.

Run the existing Gradle build/tests/lint commands plus `python3 scripts/check_room_migration.py`.
Tests cover every bundled seed, alternatives, all plan lengths, filters, MET math,
paused/rest/skipped credit, delayed/irregular ticks, checkpoint restore, storage failure,
foreground gaps, concurrent-plan protection and replay identities. The SQL test verifies
v1 data preservation and compares migrated SQLite columns, defaults and keys with Room's
exported v2 schema; it does not exercise Android SQLCipher itself.

Device smoke checks still needed before release:

1. Start local demo, open Workouts, browse/search all categories, toggle animation and
   follow an easier alternative. Test large fonts, TalkBack and dark mode.
2. Generate 5/10/15/20-minute and gentle/quiet plans; inspect exact total duration and
   filtered movements. Start a short practice and test pause, skip, rest and early finish.
3. Toggle voice/haptics; test an offline TTS voice and a device with no voice installed.
4. Background while active, return, and confirm paused time. Kill/relaunch, reopen demo,
   and resume the saved checkpoint. Replaying a plan must produce a distinct session.
5. Upgrade a device containing v1 data; verify encrypted migration and existing profiles.
   Finish a run, retry persistence, sign out/in, then delete the account and check erasure.

JVM tests, SQLite checks and APK compilation do not establish these device results.
