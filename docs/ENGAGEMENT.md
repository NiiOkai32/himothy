# Notifications and engagement

The last requested feature increment adds opt-in movement reminders, private event
notifications, a local timetable, an Android widget, daily mini-challenges and first-view
activity-trophy celebrations. It is implemented, with device and Firebase integration
checks still outstanding. See [validation](VALIDATION.md).

## Using it

Open **Home → Movement reminders**. Android notification permission is requested only
when you enable a notification option. Android's notification settings remain accessible
from this screen if permission or a channel is disabled. Movement reminders, remote push
and widget sharing have independent switches, all off initially. A preview sends one
local notification and creates no recurring reminder. Android 26+ notification channels
separate movement reminders from social/activity events.

Set an availability window, selected weekdays, an IANA time zone, quiet hours and a
2–20 minute movement goal. A reminder needs an uninterrupted gap plus a five-minute
buffer around every class. WorkManager schedules approximately; Doze, force-stop,
battery restrictions and notification permissions may delay or suppress delivery.
Delivery checks the current account, deletion state, timetable, quiet hours, chosen
weekday, completed movement goal and local-day rest/sent markers again. At most one
movement reminder is shown per local day. **Pause movement reminders today** protects
that selected local date; this is separate from the social league's UTC rest policy.
The goal suppresses reminders based on logged movement seconds, not Health Connect totals.

Import an `.ics` file through Android's document picker (maximum 1 MiB), or add repeating
weekly busy blocks manually. Calendar titles, descriptions and the file itself are not
stored. Only absolute busy intervals stay in encrypted Room. Imports replace the previous
timetable; manual blocks add to it. Coverage is 90 days; expired coverage pauses reminders
until refreshed. Removing the timetable explicitly uses the availability window alone.
Travel/time-zone changes do not reinterpret existing absolute class times; reimport or
replace manual blocks when the timetable itself changes.

The parser supports explicit DTSTART/DTEND, UTC/floating/IANA time zones, all-day events,
daily/weekly RRULE with interval, count or until, BYDAY, Monday week starts, EXDATE, RDATE
and individual recurrence replacements/cancellations. Transparent events are ignored.
Unsupported monthly/numbered/exclusion rules, RANGE replacements, ambiguous duplicate
properties and daylight-saving gaps fail the import without altering the saved timetable.
It expands a bounded 90-day horizon; it is not a complete RFC5545 calendar client. Custom
VTIMEZONE-only identifiers need an export using a recognized IANA zone.

The mini-challenge changes daily at midnight UTC and offers a two-minute movement idea
with a gentle alternative. A logged minute of movement or 30 estimated camera reps enables
self-reported completion. One local completion per account/UTC day is enforced by a Room
key. This adds no duplicate XP, competition credit or independent verification. Workout
completion and first viewing a new activity-win trophy use the bundled celebration and
respect Android's animation setting.

Enable widget sharing explicitly, then **Add home-screen widget**, or add CampusFit from
the launcher's widget picker. It shows the most recently refreshed eligible daily activity
challenge's rank and the cached streak, with update times. It never displays weight ranks,
health values or peer names. A fresh daily board must have been opened within five minutes
when rendering; streak snapshots must be from the current UTC day. Otherwise it asks for
refresh. Android may retain a rendered widget between updates, so values are explicitly
labelled cached and may change. Tap it, open the daily challenge and refresh. Updates occur
after social cache changes, sharing/account changes and the launcher's periodic update.
Sign-out clears widget content, pending notification work and visible notifications. Demo
sessions now survive process restart so opted-in local reminders can run; explicit sign-out
ends the demo session while retaining encrypted data.

## Cloud data and delivery

| Path | Content and authority |
| --- | --- |
| `users/{uid}/notifications/{sha256}` | Server-owned kind, opaque target, creation time, read flag and 30-day expiry; verified owner reads and can only set read=true |
| `users/{uid}/devices/{sha256(token)}` | Private FCM token and 30-day expiry; callable-only |
| `notificationDeviceOwners/{sha256(token)}` | Owning UID and expiry; callable-only; transfers a device token between accounts atomically |

`notificationAction` requires a matching authenticated UID, verified email, existing
profile and no deletion tombstone. Registration has a ten-device cap; disabling push
unregisters this device, disables SDK auto-init and deletes its FCM token. On sign-out the
local token is invalidated; stale server records expire or are removed after an invalid-token
response. Account deletion removes global token bindings before recursively erasing the
private user tree. No credentials or tokens are logged or included in the APK source.

Firestore triggers create deduplicated private inbox records for incoming/accepted friend
requests, supportive cheers, challenge joins, activity trophies and finalized leagues.
Peer events recheck blocks and current membership where relevant. They store no sender
name, message text or health values. The `deliverNotification` trigger sends **data only**:
UID, opaque inbox ID and allowlisted kind, with normal priority and a 24-hour TTL. Generic
visible text is constructed on Android after reading the canonical owner's inbox record.
An arbitrary FCM body, URL or supplied route is never used. The app suppresses old/read/shown
records and defers during quiet hours or busy class intervals. Taps open the private inbox
only for the matching current account. PendingIntents are explicit and immutable.

FCM and trigger delivery are best-effort and may repeat; stable inbox IDs and local shown
markers suppress normal retries. Inbox refresh works without push permission, both on
screen and through connected periodic work. Cloud mark-read changes upload later when
offline; local reminders and timetable use do not require Firebase. Remote push needs a
configured verified account, FCM-capable physical device, this module's callable/triggers,
and the project's permissions/billing. No production push was sent during development.

Deploy all provided functions/rules/indexes together to the chosen `us-central1` Firebase
project using the README operator steps. `firestore.indexes.json` defines TTL policies
for `notifications`, `devices` and `notificationDeviceOwners` on `expiresAt`; confirm these
are enabled after deployment. TTL deletion is asynchronous; delivery also checks expiry.
Without TTL, administrative cleanup is needed for old private records. Deletion tombstones
use the separately documented minimum lifetime.

## Local persistence and checks

Room v5 adds account-scoped `engagement_preferences`, `timetable_blocks`,
`notification_inbox`, `mini_completions`, `seen_awards` and `reminder_days`. All use the
existing SQLCipher database and are included in local account erasure. A small private
runtime preference holds the session UID and notification/widget booleans to serialize
final posting/publication against sign-out and opt-out; it contains no health values or
timetable. Android backups remain disabled.

Pure JVM tests exercise gaps/buffers, quiet hours, weekdays/local-day rest suppression,
DST windows, calendar recurrence/exceptions/rejections, mini eligibility and route
allowlists. Node tests exercise payload privacy and actual callable identity/input guards
without outbound database or messaging RPCs. SQLite checks v1→v5 schema/data preservation
and unique mini claims. These are not Android scheduling or Firestore transaction tests.

The authored `functions/test/emulator/notifications.test.js` tests token transfer,
old-owner unregister safety, inbox deduplication, generic transport payloads, read-only
rules and deletion barriers against a real local Firestore emulator with an injected
fake messaging transport. The Functions emulator also suppresses actual outbound FCM.
Its assertions now pass against the local emulator. The previously blocked download
host is allowed by the published network policy. This uses fake messaging, not real FCM.

Before release, run the README emulator command and physical-device smoke tests:

1. Deny, allow and revoke Android notification permission; toggle individual channels.
2. Import overlapping/excluded/DST classes; check manual blocks, quiet hours, weekday
   selections, rest days, completed movement goals and expired coverage at delivery.
3. Test reboot/process death, Doze, delayed delivery, offline use and clock/time-zone changes.
4. Switch accounts and sign out during queued reminders, push, token rotation and widget
   refresh; verify generic lock-screen content and no previous account's visible values.
5. With test accounts, exercise every cloud event, offline read synchronization, duplicate
   FCM delivery, 24-hour stale suppression, multi-device opt-out and account deletion.
6. Check widget pin/resize, sharing defaults, stale labels, cache refresh and signed-out
   clearing on multiple launchers. Complete a mini-challenge twice and cross UTC midnight.
7. Check trophy/workout celebrations with animations disabled and accessibility enabled.
