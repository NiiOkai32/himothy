# Offline 3D exercise demonstrations and countdowns

Version 0.4.0 uses a solid, articulated 3D human in the library, exercise detail and
workout player. All 72 movements include XYZ joint keyframes in metres. Squats,
knee lifts, lunges and walking have forward/back movement; floor rigs distinguish
lying face-up, face-down and side support. Clothing, shoes, face and limbs are
triangle meshes, lit per pixel with a depth buffer, perspective camera and contact
shadow. The model and choreography are authored in the repository; no external
model licence, downloaded video, WebView or network is needed.

Drag horizontally or use the turn/reset buttons on detail/player demos to change
camera angle. A paused demo keeps its pose while the camera turns. Library previews
have a pause/play button. Disabled Android animations show a useful still pose;
changing the setting is observed while the app is open. If a device cannot initialize
OpenGL ES 2, the same mesh is projected into a labelled still 3D preview. Form tips
and easier alternatives remain available; the illustration is not a form assessment.

TextureViews share one GL worker and context. Library previews are capped at 15 fps,
with detail/player capped at 30 fps. Paused still views redraw only on changes.
Lifecycle STOP halts rendering, detached cards destroy their EGL surfaces, and the
context is released when its last view is removed. No new native dependency or
internet permission is introduced by the renderer.

Each warm-up, work, or cooldown movement starts with a visible and spoken 3, 2, 1,
then 'go'. Its final three seconds repeat 3, 2, 1; a short chime marks rest or workout
completion. English clips are bundled and need neither internet nor a TTS voice.
The Countdown sound switch controls these clips separately from optional spoken form
cues and haptics. Sound uses media volume. Pause, leaving the player, and backgrounding
stop playback. A paused partial count-in restarts at 3 on resume. Resuming movement
already in progress continues its timer without an extra count-in.

Count-ins add three seconds before each movement, separate from chosen plan time.
They never increase stage elapsed time, movement minutes, or calories. Rest does not
get an extra count-in. Skipping during a count-in gives no activity credit. Existing
workout checkpoints remain compatible; no database schema change is needed.

Choreography tools:

- `scripts/generate_exercise_demos.py` authors the original reference keyframes.
- `scripts/generate_exercise_3d.py` retargets them into the XYZ human rig. Run it after
  editing reference choreography. Both sets are bundled in `assets/exercises.json`.
- `scripts/generate_countdown_audio.py` reproduces clips with FFmpeg/CMU Flite.
  Audio provenance is in `assets/countdown-audio-notices.txt`.

The compact player keeps the demo, timer, start/pause and skip controls together.
Its sliders icon opens cue settings and pauses activity; closing it leaves the
workout paused. Form tips and safety guidance remain in user-triggered sheets.
