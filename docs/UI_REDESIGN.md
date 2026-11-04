# CampusFit 0.2 · visual redesign

The main flows now use an illustrated lime mascot, a violet/lime/peach palette, rounded
cards, larger Outfit typography and short action labels. All artwork is drawn locally in
Compose; the bundled Outfit font includes its SIL Open Font License. No artwork/font
network request is needed when using the app. Light and dark appearances follow Android.

Welcome offers Google, email in a bottom sheet, or local exploration. Profile setup is
three short steps: private values, movement/health guidance, and campus/sharing. Existing
health validation still applies, including the BMI floor, age and acknowledged guidance.
The health guide remains available before its explicit acknowledgment; safety and social
sharing remain opt-in rather than disappearing behind an automatic popup.

Home shows an illustrated quick start, actual logged movement and streak data, a daily
side quest and four action tiles. The workout library uses two-column illustrated move
cards. Quick plans show the interval lineup on demand. Progress focuses on the trend and
recorded movement, with optional weigh-ins in a sheet. Friends, QR codes, phone lookup,
groups, social privacy, reminders and timetable forms open from focused actions. Badges
use a visual shelf with tap-to-explore details. Quest completion and first-view trophies
show a dismissible illustrated celebration; no duplicate XP is introduced.

Long explanations live in user-triggered bottom sheets. Errors, saving failures, privacy
choices, important recording state and irreversible deletion confirmation remain visible
where they affect a decision. Opening workout form/safety tips pauses the player. Camera
and GPS detail sheets also pause active capture/tracking. Closing these sheets never
automatically resumes recording. Android's animation setting disables mascot motion;
decorative art is excluded from TalkBack. Actions retain text labels and touch targets.

Domain calculations, Room schema and Firebase permissions were not changed. Cloud Functions
are still deferred, so this redesign does not enable shared games, remote push, cloud
activity uploads or full cloud deletion. Device sign-in, physical sensors and live push
still need their existing verification.

Validation: universal APK assembly and 99 existing JVM tests pass; lint has 0 errors
and 9 existing warnings. Android 8 emulator checks covered the new setup, local profile
retention, quick-workout entry, recording pause on tips, and detail sheets. UI review
fixed half-open sheets, retained setup scroll positions and double bottom inset spacing.
The emulator-only test package uses matching app code/resources with x86_64 libraries;
the download remains universal. Live authentication and physical sensors remain untested.
