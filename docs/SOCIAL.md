# Challenges and Leaderboards increment

Implemented Android screens: Challenges/Create, Challenge Detail, Friends/Groups, QR scan,
Campus Board, Weekly League and Achievements. Firebase provides the shared experience;
local demo shows achievements from actual device sessions without inventing opponents.
The only remaining original feature module is Notifications/engagement.

## Ownership and server authority

Android never writes ranks, XP, scores, league tiers, group rosters or trophies directly.
`socialAction` binds its requested UID to the authenticated, email-verified Firebase UID,
checks the active profile/deletion tombstone, validates actions and applies rate limits.
Every health-derived board is calculated on the server from private tracking records.
Records are still self-reported: server computation is not independent exercise or
weight verification. No raw body weights, height, age or phone numbers are returned to
other users. Rejected uploads remain on-device and no longer block later valid records;
explicit private-sync retry clears those local rejection markers.

`functions/social/policy.js` contains pure calendar, score, rank, safety, XP, streak,
team, trophy and league movement policies. `functions/social/service.js` implements
role checks and Firestore transactions. New Firestore triggers update game summaries and
challenge revision signals after tracking uploads; root profile updates invalidate
weight views. Hourly finalization handles challenges/cohorts after the upload grace period.
Deploy all functions, rules and indexes together, not only the earlier Tracking callables.

## Data model and read permissions

| Path | Contents and access |
| --- | --- |
| `publicProfiles/{uid}` | Social alias/handle, campus grouping and explicit sharing/discovery preferences. Despite the historical collection name, direct gets are owner-only; directory lookups use callables and return minimal aliases. |
| `handles/{canonicalHandle}` | Unique handle→UID binding; callable-only, never listable by clients. |
| `friendships/{pairHash}` | Pending/accepted request with both UIDs; readable by those two accounts, server-write only. Accepted edges are mirrored privately under each user for rule checks. |
| `groups/{id}` | Friends/hostel/department/class group, owner, roster, invitation code; member-read, server-write only. |
| `groupCodes/{randomCode}` | 128-bit invitation lookup; callable-only. A shared code is an invitation, not public directory content. |
| `challenges/{id}` | Metric, UTC period, scope, team names, roster, status and revision; scope-authorized metadata read only. |
| `challenges/{id}/members/{uid}` | Join timestamp, metric consent and selected team; callable-only. |
| `challenges/{id}/messages/{id}` | Preset friendly cheers; callable-only, latest 50 returned to authorized viewers. |
| `users/{uid}/gameDays/{day}` | Private capped daily activity summaries. |
| `users/{uid}/game/state` | Private cumulative XP/level/badges/streak, backfill cursor, tier and league assignment. |
| `users/{uid}/restDays/{day}` | Private protected rest-day choice. |
| `users/{uid}/awards/{challengeId}` | Idempotent activity winner/team trophy. |
| `leagues/{id}` | Weekly cohort roster/scores, tier and finalization; members may get their cohort, never write/list it. |
| `leagueCounters/{campus_week_tier}` | Server-owned cohort allocation cursor. New 30-person shards are allocated transactionally without a fixed campus enrollment ceiling. |
| `campusBoards/{campusKey}/members/{uid}` | Opt-in aliases and this UTC week's capped activity score; readable only in the caller's campus context. |
| `socialSignals/{uid}` | Owner-readable revision signal; no health data. |

Room v4 adds encrypted per-user `social_cache`. Private standings do not become public
Firestore documents. Cached board data is short-lived; weight snapshots expire after
five minutes even if refresh fails, and explicit withdrawal evicts them immediately.
Previously shared information cannot be retracted from screenshots or offline copies
on another person's phone. Activity lists/metadata show a cached label when stale.

## Friends, contacts, phone and QR

Saving an alias explicitly opts into exact-username discovery and friend challenge
participation. Handles use 3–20 ASCII letters/digits/underscores, begin with a letter,
and are case-insensitive. Aliases can be changed. Other directory entries returned by
callables contain only UID/alias/handle, not campus preferences or health profiles.
Campus names are self-reported, normalized and hashed for grouping; affiliation is not
institutionally verified. After editing a private university field, save social sharing
preferences again to update the shared campus context with explicit consent.

Friend requests require acceptance. Remove/cancel/block and unblock are available.
Blocking removes the friendship and hides applicable rows/messages without improving
the underlying competitive rank. Losing a private friendship/group scope withdraws the
affected challenge participation; participants can also withdraw even after losing access.

Phone lookup sends only a reviewed individual number in international E.164 format,
never an address book. The system single-phone contact picker avoids READ_CONTACTS.
Lookup is available only for a target with a linked, verified Firebase Auth phone and
explicit discovery opt-in. The number is not stored in directory documents. Firebase
Phone provider setup/billing/SMS policy and signing fingerprints are required. The
user starts SMS verification and confirms its code; credentials remain in SDK memory.
No live SMS was sent or tested here.

QR generation uses ZXing; scanning uses the bundled on-device ML Kit barcode scanner.
Frames are discarded. Codes contain `campusfit://friend/<handle>` with no credentials or
health data. The parser rejects unrelated schemes, hosts, queries, userinfo, ports and
path traversal. Incoming friend links open Friends and wait for real-account alias setup
and explicit confirmation; merely opening a link does not send a request. Treat usernames
as directory names and check the recipient before accepting a request.

## Challenges and safe weight rankings

Daily periods are midnight-to-midnight UTC; weeks start Monday UTC; monthly periods use
actual calendar boundaries, including leap February. Activity challenges can be current
or upcoming; only sessions starting after both enrollment and the period start count.
Uploading has a 24-hour post-period grace window. Late uploads beyond that window do not
alter finalized-period activity credit. Sessions are UUID-deduplicated, daily movement
credit caps at 60 minutes, and burn is proportionally clipped with the credited duration.
The backend accepts up to 300 session records per UTC day; rejected records stay local.

Weight challenges are private friends/groups only, always upcoming, and enrollment
closes at the period start. Joining needs an explicit metric-specific consent checkbox
and compatible private profile settings: activity-only blocks both weight metrics;
percentage privacy blocks raw-kilogram loss. Height, age and goal safety are checked.
A baseline must come from the previous UTC day and be received before the start, so an
offline/backdated upload cannot invent a post-start baseline. Other users see only lost
percentage or kilograms, never starting/current weights.

No eligible score is returned if the baseline is missing, a period loss is flagged,
a change exceeds 1 kg/week over the measured days, a baseline/latest BMI is below the
app threshold, or health/privacy settings disallow it. Weight gains score zero with
supportive messaging. Only the affected user receives the private reason. Changing
activity-only preferences immediately affects newly requested standings once the private
profile update reaches the server. Nonparticipants can preview metadata but never weight
standings. Weight rankings award neither XP nor habit trophies.

Groups/challenges support up to 50 participants; private groups cover hostels, departments,
classes or friends. A creator can define 2–8 teams. Team averages include all enrolled
members, including zero/ineligible contributors, to avoid automatically favoring larger
rosters. Only contributing members of tied winning activity teams receive team trophies.
Standalone activity challenges award all tied positive first-place participants. Shared
competition ranks use 1,1,3 numbering and six-decimal tie precision, with stable UID order.

## XP, streaks, achievements and leagues

XP is 2 per whole credited movement minute plus 10 for a day with at least two minutes,
maximum 130/day. Levels require 100 XP. Lifetime activity is backfilled in bounded pages;
the UI explicitly marks partial history until indexing completes. Duplicate function
execution recalculates a daily delta transactionally rather than awarding XP twice.

Badges: First Move, Ten Sessions, 100 Minutes, Early Bird (05:00–08:00 UTC start, at
least 2 minutes) and 7-Day Streak. Stair Master is visibly locked because stair-specific
tracking is not available; it is never fabricated. A protected rest day can bridge a
streak once per UTC week, with no XP or rank credit. Current streak scans are bounded at
400 days and display 400+ when capped. Ordinary rest remains valid even without protection.

The deployed function service account needs Firestore access and Firebase Auth user
lookup/deletion permissions; confirm Eventarc and Cloud Scheduler setup during deployment.

Weekly activity leagues use Bronze/Silver/Gold/Diamond cohorts of up to 30 students,
matched by campus and tier. With at least five members, the top 20% with positive activity
promote and bottom 20% relegate, bounded to the four tiers. Ties share ranks; no zero-score
promotion is invented. A full 24-hour upload grace period precedes finalization and next
cohort enrollment. Opting out removes current membership without awarding a tier change.
Campus public boards require explicit opt-in, use activity only and show the top 100;
shared names/affiliations are not official university attestations.

Preset cheers and friendly competitive messages are rate-limited, create no XP, and
avoid body-shaming copy. Users can cheer a participant or the group. Free-text chat and
bulk contact discovery are intentionally not provided by this feature.

## Deletion and validation

The existing deletion callable now also erases aliases/handles/campus rows, revision
signals, friendships and reciprocal edges, user-authored messages, memberships, owned
challenges and groups, invitation codes and league roster/score entries. Closing a group
also removes its associated challenges. Private game/award/cache data is included in
recursive server or local erasure. Every private game update checks the deletion
barrier, so delayed triggers do not recreate private records after deletion.

Verified: Android compilation, 73 JVM tests, 45 Node tests and the v1→v4 SQL migration
check (including rejected-outbox isolation/manual retry and cache erasure). Node tests
exercise actual scoring code and callable identity guards, not Firestore transactions.

Two demo-project emulator integration tests are authored (Tracking and Social), but
neither has executed: the Firestore emulator download host storage.googleapis.com
remains denied by the running policy. The domain addition is saved in environment
settings; apply it before retrying the README emulator command. Actual rule enforcement,
transaction contention, triggers, scheduled finalization and recursive social erasure
therefore remain unverified in Firebase. There is no production Firebase deployment.

Before release, run those emulator assertions and physical-device checks: two-account
request/accept/remove/block; group invitation/revocation/closure; consent changes and
five-minute offline expiry; cross-account switching; simultaneous join/cohort allocation;
UTC end/grace rollover; XP/streak backfill; team trophy/tie outcomes; camera QR resource
cleanup; deep links through signup/alias setup; phone OTP/discovery permission denial;
and deletion races. JVM/policy tests do not establish those device or cloud results.
