# Offline exercise demonstrations and countdowns

Version 0.3.0 replaces the generic stick figure with a clothed 2D human in the library,
exercise detail, and workout player. All 72 movements have authored local keyframes.
Holds show their held position with a small breathing movement. Wall movements show
a wall, and stair climbing shows a step. Drawings are illustrative; the existing form
instructions and easier alternatives remain available.

Visible library cards animate. The pause/play icon beside the movement count stops
previews, and the detail screen has its own control. Android's disabled-animation
setting shows a representative pose. Animation state is read in Canvas's draw phase;
off-screen LazyColumn cards do not run separate animation compositions.

Each warm-up, work, or cooldown movement starts with a visible and spoken 3, 2, 1,
then 'go'. Its final three seconds repeat 3, 2, 1; a short chime marks rest or workout
completion. These English clips are bundled and need neither internet nor a TTS voice.
The Countdown sound switch controls these clips separately from optional spoken form
cues and haptics. Sound uses media volume. Pause, leaving the player, and backgrounding
stop playback. A paused partial count-in restarts at 3 on resume. Resuming movement
already in progress continues its timer without an extra count-in.

Count-ins add three seconds before each movement, separate from the chosen plan time.
They never increase stage elapsed time, movement minutes, or calories. Rest does not
get an extra count-in. Skipping during a count-in gives no activity credit. Checkpoints
save the remaining count-in without a database schema change; older JSON checkpoints
remain readable and always restore paused.

`scripts/generate_exercise_demos.py` authors the catalogue's normalized keyframes.
`scripts/generate_countdown_audio.py` reproduces the audio with FFmpeg/CMU Flite.
Audio provenance is bundled in `assets/countdown-audio-notices.txt`.

The player keeps the demo, timer, start/pause, and skip controls together. The sliders
icon opens a cue-settings sheet and pauses activity; closing it leaves the workout
paused. Form tips and safety guidance remain in user-triggered sheets.
