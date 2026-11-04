"""Retarget the authored exercise choreography onto a 3D human rig (metres).
X = anatomical left/right, Y = height, Z = forward. Floor poses face either
up or down; sagittal movements have independent depth, not a flat billboard.
Run after generate_exercise_demos.py whenever choreography changes.
"""
import json
import math
from pathlib import Path

PATH = Path('app/src/main/assets/exercises.json')
FLOOR_UP = set('glute-bridge dead-bug gentle-crunch bent-knee-leg-raise heel-slides'.split())
FLOOR_DOWN = set('mountain-climbers knee-push-up push-up cat-cow child-pose downward-dog low-cobra forearm-plank high-plank bird-dog side-plank-from-knees'.split())
SAGITTAL = set('wall-push-up quiet-wall-press wall-plank wall-sit gentle-hip-hinge standing-forward-fold'.split())
MARCH = set('high-knees standing-knee-drive low-impact-knee-lifts easy-march slow-standing-march dance-knee-lift standing-cross-crunch'.split())
WALK = set('campus-walk brisk-campus-walk easy-jog walk-jog-intervals'.split())
SQUAT = set('bodyweight-squat shallow-squat squat-pulses squat-to-reach'.split())

def retarget(key, pose, index):
    points = list(zip(pose['points'][::2], pose['points'][1::2]))
    floor = key in FLOOR_UP | FLOOR_DOWN or (key == 'half-burpees' and points[0][0] < .3)
    side = floor or key in SAGITTAL
    out = []
    for joint,(x,y) in enumerate(points):
        if side:
            lateral = {2:-.19,3:.19,4:-.23,5:.23,6:-.24,7:.24,9:-.13,10:.13,11:-.14,12:.14}.get(joint,0)
            if key in FLOOR_UP: lateral *= -1
            height = max(.045, (.89-y)*1.65) if floor else (.94-y)*2.2
            depth = (.5-x)*2.2 if floor or key in ('gentle-hip-hinge','standing-forward-fold') else (x-.5)*2.2
            out.append([lateral,height,depth])
        else:
            out.append([(x-.5)*2.35,(.94-y)*2.2,0.])
    if not side:
        # Anatomical shoulders and hips retain volume even in front-facing poses.
        out[2][2]=out[3][2]=.015
        out[4][2]=out[5][2]=.025
        out[6][2]=out[7][2]=.035
        out[9][2]=out[10][2]=.035
        out[11][2]=out[12][2]=.03
    if key in SQUAT or (key == 'half-burpees' and not floor and points[8][1] > .65):
        amount=max(0.,min(1.,(points[8][1]-.58)/.15))
        out[8][2]=-.26*amount
        out[0][2]=.10*amount
        out[1][2]=out[2][2]=out[3][2]=.035*amount
        for j in (9,10):out[j][2]=.27*amount
        if amount > .15:
            for j,side_sign in ((4,-1),(5,1)):
                out[j]=[side_sign*.24, out[2][1]-.13,.30*amount]
            for j,side_sign in ((6,-1),(7,1)):
                out[j]=[side_sign*.17,out[2][1]-.03,.53*amount]
    if key in MARCH:
        for knee,foot,sign in ((9,11,-1),(10,12,1)):
            lift=max(0.,min(1.,(.89-points[foot][1])/.16))
            out[knee][0]=sign*.14;out[foot][0]=sign*.14
            out[knee][2]=.38*lift;out[foot][2]=.32*lift
        left=max(0.,(.89-points[11][1])/.16)
        right=max(0.,(.89-points[12][1])/.16)
        for elbow,hand,amount in ((4,6,right-left),(5,7,left-right)):
            out[elbow][2]=.12*amount;out[hand][2]=.27*amount
    if key in WALK:
        for knee,foot,sign in ((9,11,-1),(10,12,1)):
            depth=(.5-points[foot][0])*2.1
            out[knee][0]=sign*.14;out[foot][0]=sign*.14
            out[knee][2]=depth*.55;out[foot][2]=depth
        for elbow,hand,foot in ((4,6,12),(5,7,11)):
            depth=out[foot][2]
            out[elbow][2]=depth*.45;out[hand][2]=depth*.8
    if key == 'butt-kicks':
        for knee,foot,sign in ((9,11,-1),(10,12,1)):
            lift=max(0.,(.90-points[foot][1])/.28)
            out[knee][0]=sign*.14;out[foot][0]=sign*.14
            out[knee][2]=-.05;out[foot][2]=-.37*lift
    if key == 'reverse-lunge' and points[8][1]>.63:
        left_front=points[11][0]<.5
        for knee,foot,sign,front in ((9,11,-1,left_front),(10,12,1,not left_front)):
            out[knee][0]=sign*.14;out[foot][0]=sign*.14
            out[knee][2]=.46 if front else -.32
            out[foot][2]=.46 if front else -.56
        out[8][2]=-.025
    if key in ('heel-digs','dance-heel-taps'):
        for knee,foot,sign in ((9,11,-1),(10,12,1)):
            amount=max(0.,(.40-points[foot][0])/.1) if sign<0 else max(0.,(points[foot][0]-.60)/.1)
            out[knee][0]=sign*.14;out[foot][0]=sign*.14
            out[knee][2]=amount*.16;out[foot][2]=amount*.35
    if key in ('gentle-torso-turn','standing-cross-crunch'):
        angle=(0.,.45,0.,-.45)[index%4] if key=='gentle-torso-turn' else (.5-points[0][0])*6
        for j in range(8):
            x,y,z=out[j];out[j]=[x*math.cos(angle)+z*math.sin(angle),y,-x*math.sin(angle)+z*math.cos(angle)]
    if key in ('standing-chest-opener','quiet-chest-opening') and index%2:
        out[4][2]=out[5][2]=-.18
        out[6][2]=out[7][2]=-.08
    if key == 'shoulder-rolls':
        for j in (2,3,4,5): out[j][2]=(0.,-.04,-.10)[index%3]
    if key == 'shadow-boxing':
        extended=6 if points[6][0]<.25 else 7 if points[7][0]>.75 else None
        if extended is not None:
            sign=-1 if extended==6 else 1
            out[extended]=[sign*.12,1.37,.64]
            out[extended-2]=[sign*.19,1.32,.35]
    if key in ('skater-steps','grapevine-steps','box-step','salsa-basic'):
        out[11][2]=-.16 if index%2 else .14
        out[12][2]=.14 if index%2 else -.16
    if key == 'stair-climbing':
        for knee,foot,sign in ((9,11,-1),(10,12,1)):
            lift=max(0.,(.89-points[foot][1])/.16)
            out[knee][0]=sign*.14;out[foot][0]=sign*.14
            out[knee][2]=.22*lift;out[foot][2]=.30*lift
    if key == 'side-plank-from-knees':
        # The supporting arm is under the shoulder; the other points up.
        out=[[0,1.0,.85],[0,.84,.67],[0,.70,.59],[0,.94,.59],
             [0,.37,.64],[0,1.20,.59],[0,.07,.68],[0,1.46,.59],
             [0,.50,.03],[0,.08,-.40],[.07,.08,-.40],[0,.06,-.78],[.07,.06,-.78]]
        out[8][1]+=.006*index
    return {'points':[round(n,5) for p in out for n in p]}

items=json.loads(PATH.read_text())
for item in items:
    item['demo3d']=[retarget(item['id'],pose,i) for i,pose in enumerate(item['demo'])]
    assert all(math.isfinite(v) and -2.5<=v<=2.5 for p in item['demo3d'] for v in p['points']),item['id']
PATH.write_text(json.dumps(items,indent=2)+'\n')
print(f'Authored 3D rigs for {len(items)} movements.')
