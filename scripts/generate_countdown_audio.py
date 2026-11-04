"""Rebuild the bundled offline voice clips with FFmpeg's locally installed Flite filter.
No voice engine or executable is shipped inside the APK. Clips use media volume.
"""
import subprocess
from pathlib import Path
out = Path('app/src/main/res/raw')
out.mkdir(parents=True, exist_ok=True)
for word in ('three', 'two', 'one', 'go'):
    subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i',f'flite=text={word}:voice=slt',
        '-af','silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,atempo=1.12,apad=pad_dur=0.04',
        '-ar','22050','-ac','1',str(out / f'count_{word}.wav')], check=True)
subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','sine=frequency=660:duration=0.12',
    '-f','lavfi','-i','sine=frequency=880:duration=0.22','-filter_complex',
    '[0:a]afade=t=out:st=0.07:d=0.05[a];[1:a]afade=t=out:st=0.12:d=0.1[b];[a][b]concat=n=2:v=0:a=1',
    '-ar','22050','-ac','1',str(out / 'count_done.wav')], check=True)
