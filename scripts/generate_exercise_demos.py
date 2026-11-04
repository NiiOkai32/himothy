"""Author the offline 2D human's normalized keyframes. No network or runtime dependencies.
Joint order: head, neck, shoulders L/R, elbows L/R, hands L/R, pelvis,
knees L/R, ankles L/R. Side views keep the far limbs slightly offset.
Run from the repository root after editing choreography.
"""
import copy
import json
from pathlib import Path

NAMES = ['head','neck','sl','sr','el','er','hl','hr','hip','kl','kr','fl','fr']
stand = [(.5,.17),(.5,.28),(.43,.31),(.57,.31),(.37,.44),(.63,.44),(.36,.57),(.64,.57),(.5,.58),(.44,.75),(.56,.75),(.42,.91),(.58,.91)]
def p(base=stand, **changes):
    out = copy.deepcopy(base)
    for key, value in changes.items(): out[NAMES.index(key)] = value
    return out

def shift(base, x=0, y=0): return [(a+x,b+y) for a,b in base]
def mirror(base):
    out = [(1-x,y) for x,y in base]
    for a,b in [(2,3),(4,5),(6,7),(9,10),(11,12)]: out[a],out[b]=out[b],out[a]
    return out

reach = p(el=(.32,.23),er=(.68,.23),hl=(.35,.07),hr=(.65,.07))
squat = p(head=(.5,.35),neck=(.5,.46),sl=(.43,.48),sr=(.57,.48),el=(.35,.44),er=(.65,.44),hl=(.4,.35),hr=(.6,.35),hip=(.5,.73),kl=(.32,.76),kr=(.68,.76),fl=(.33,.91),fr=(.67,.91))
shallow = p(squat,head=(.5,.25),neck=(.5,.36),sl=(.43,.39),sr=(.57,.39),hip=(.5,.65))
wide = p(reach,kl=(.32,.76),kr=(.68,.76),fl=(.25,.91),fr=(.75,.91))
step = p(stand,el=(.31,.30),er=(.69,.30),hl=(.29,.13),hr=(.71,.13),kr=(.7,.75),fr=(.79,.91))
knee = p(kl=(.34,.59),fl=(.39,.73),el=(.38,.48),hl=(.38,.58),er=(.64,.35),hr=(.57,.23))
low_knee = p(knee,kl=(.37,.67),fl=(.41,.81))
heel = p(kl=(.38,.76),fl=(.30,.91),fr=(.58,.89),hl=(.4,.55),hr=(.65,.52))
kick = p(knee,kl=(.40,.76),fl=(.34,.62))
side = p(head=(.43,.17),neck=(.43,.28),sl=(.36,.31),sr=(.5,.31),hip=(.47,.58),kl=(.37,.75),kr=(.61,.75),fl=(.30,.91),fr=(.70,.91),hl=(.27,.57),hr=(.60,.56))
disco = p(head=(.45,.19),neck=(.46,.30),sl=(.39,.32),sr=(.53,.32),el=(.3,.20),hl=(.34,.07),er=(.66,.43),hr=(.7,.57),hip=(.51,.59),fl=(.34,.91),fr=(.65,.91))
bend = p(head=(.39,.23),neck=(.42,.33),sl=(.35,.36),sr=(.49,.30),el=(.34,.49),hl=(.37,.61),er=(.53,.15),hr=(.37,.08))
turn = p(sl=(.47,.31),sr=(.56,.31),el=(.36,.40),er=(.66,.40),hl=(.35,.29),hr=(.71,.29))
hinge = p(head=(.28,.39),neck=(.37,.44),sl=(.38,.46),sr=(.41,.45),el=(.37,.58),er=(.43,.57),hl=(.36,.70),hr=(.45,.68),hip=(.61,.58),kl=(.52,.75),kr=(.63,.75),fl=(.48,.91),fr=(.62,.91))
fold = p(hinge,head=(.32,.66),neck=(.4,.61),sl=(.42,.61),sr=(.45,.60),el=(.4,.75),er=(.47,.74),hl=(.4,.88),hr=(.48,.87))
plank = [(.16,.42),(.25,.47),(.28,.49),(.30,.48),(.28,.65),(.33,.64),(.30,.85),(.35,.84),(.57,.62),(.73,.74),(.75,.72),(.89,.86),(.91,.84)]
push = p(plank,head=(.16,.62),neck=(.25,.66),sl=(.28,.67),sr=(.30,.66),el=(.40,.73),er=(.42,.72),hip=(.57,.73),kl=(.73,.79),kr=(.75,.77))
knee_plank = p(plank,hip=(.54,.61),kl=(.65,.84),kr=(.68,.83),fl=(.81,.72),fr=(.84,.71))
knee_push = p(push,hip=(.54,.71),kl=(.65,.84),kr=(.68,.83),fl=(.81,.72),fr=(.84,.71))
quad = p(plank,head=(.18,.43),neck=(.28,.49),sl=(.30,.51),sr=(.33,.50),hip=(.60,.53),kl=(.60,.83),kr=(.63,.81),fl=(.78,.86),fr=(.80,.84))
bird = p(quad,el=(.19,.50),hl=(.07,.49),kr=(.76,.53),fr=(.92,.54))
cat = p(quad,head=(.19,.52),neck=(.28,.50),sl=(.31,.48),sr=(.34,.47),hip=(.59,.48))
cow = p(quad,head=(.18,.38),neck=(.28,.46),sl=(.31,.51),sr=(.34,.50),hip=(.60,.58))
dog = p(plank,head=(.25,.63),neck=(.33,.55),sl=(.36,.51),sr=(.38,.50),el=(.25,.69),er=(.29,.68),hl=(.13,.85),hr=(.17,.84),hip=(.57,.28),kl=(.72,.56),kr=(.75,.55),fl=(.84,.86),fr=(.88,.84))
child = p(quad,head=(.29,.69),neck=(.39,.65),sl=(.42,.64),sr=(.45,.63),el=(.27,.74),er=(.31,.73),hl=(.12,.83),hr=(.16,.82),hip=(.71,.67),kl=(.59,.85),kr=(.62,.83),fl=(.84,.86),fr=(.87,.84))
supine = [(.14,.79),(.23,.79),(.27,.79),(.29,.77),(.36,.85),(.38,.83),(.47,.87),(.49,.85),(.52,.82),(.66,.59),(.69,.57),(.81,.86),(.84,.84)]
bridge = p(supine,hip=(.52,.63),kl=(.66,.58),kr=(.69,.56))
dead = p(supine,el=(.28,.61),er=(.33,.59),hl=(.29,.43),hr=(.35,.41),kl=(.55,.56),kr=(.59,.54),fl=(.74,.55),fr=(.78,.53))
dead_extend = p(dead,el=(.17,.68),hl=(.06,.60),kr=(.70,.74),fr=(.88,.80))
crunch = p(supine,head=(.22,.64),neck=(.29,.68),sl=(.32,.69),sr=(.34,.67),el=(.39,.68),er=(.41,.66),hl=(.48,.66),hr=(.50,.64))
wall = [(.50,.19),(.54,.29),(.56,.32),(.58,.31),(.69,.36),(.71,.35),(.82,.32),(.84,.31),(.47,.59),(.42,.75),(.45,.74),(.37,.91),(.41,.90)]
wall_press = p(wall,head=(.66,.24),neck=(.68,.35),sl=(.69,.38),sr=(.71,.37),el=(.76,.50),er=(.78,.49),hip=(.55,.62))
lunge = p(stand,head=(.48,.30),neck=(.48,.41),sl=(.42,.43),sr=(.55,.43),hip=(.51,.68),kl=(.31,.72),fl=(.29,.91),kr=(.68,.86),fr=(.79,.91),hl=(.37,.65),hr=(.63,.63))
wall_sit = p(stand,head=(.70,.33),neck=(.70,.44),sl=(.67,.46),sr=(.71,.46),hip=(.73,.69),kl=(.50,.69),kr=(.53,.67),fl=(.48,.91),fr=(.53,.90),el=(.59,.59),er=(.63,.57),hl=(.50,.64),hr=(.54,.62))
warrior = p(stand,head=(.49,.24),neck=(.49,.35),sl=(.42,.37),sr=(.56,.37),el=(.27,.37),er=(.72,.37),hl=(.11,.37),hr=(.88,.37),hip=(.51,.63),kl=(.28,.66),kr=(.69,.78),fl=(.27,.91),fr=(.84,.91))
tree = p(stand,el=(.42,.43),er=(.58,.43),hl=(.48,.35),hr=(.52,.35),kr=(.69,.64),fr=(.48,.76),kl=(.47,.76),fl=(.46,.91))
side_plank = p(plank,head=(.26,.30),neck=(.34,.40),sl=(.35,.43),sr=(.39,.40),el=(.30,.64),hl=(.31,.85),er=(.47,.24),hr=(.49,.08),hip=(.55,.59),kl=(.68,.83),kr=(.70,.80),fl=(.84,.86),fr=(.87,.84))
side_low = p(side_plank,head=(.26,.39),neck=(.34,.49),sl=(.35,.52),sr=(.39,.49),hip=(.55,.75))
forearm = p(plank,head=(.20,.52),neck=(.29,.57),sl=(.32,.59),sr=(.34,.58),el=(.35,.84),er=(.38,.82),hl=(.17,.85),hr=(.20,.83),hip=(.58,.67))
cobra = p(plank,head=(.20,.53),neck=(.27,.62),sl=(.30,.65),sr=(.32,.64),el=(.34,.78),er=(.37,.77),hl=(.27,.86),hr=(.30,.84),hip=(.59,.83),kl=(.75,.84),kr=(.77,.82))
cobra_low = p(cobra,head=(.19,.64),neck=(.27,.72),sl=(.30,.75),sr=(.32,.74))

cycles = {}
def add(ids, frames):
    for key in ids.split():
        assert key not in cycles, key
        cycles[key] = frames

add('bodyweight-squat', [stand,squat])
add('shallow-squat', [stand,shallow])
add('squat-pulses', [squat,shift(squat,y=-.045)])
add('squat-to-reach', [squat,stand,reach,stand])
add('jumping-jacks', [stand,shift(wide,y=-.025)])
add('step-jacks', [stand,step,stand,mirror(step)])
add('easy-march low-impact-knee-lifts slow-standing-march', [stand,low_knee,stand,mirror(low_knee)])
add('high-knees standing-knee-drive dance-knee-lift', [stand,knee,stand,mirror(knee)])
add('standing-cross-crunch', [stand,p(knee,head=(.43,.24),neck=(.46,.34),hr=(.35,.53),er=(.5,.43)),stand,mirror(p(knee,head=(.43,.24),neck=(.46,.34),hr=(.35,.53),er=(.5,.43)))])
add('fast-feet', [p(stand,fl=(.41,.86)),p(stand,fr=(.59,.86))])
add('butt-kicks', [stand,kick,stand,mirror(kick)])
add('heel-digs dance-heel-taps', [stand,heel,stand,mirror(heel)])
add('side-step-touch dance-step-touch', [stand,side,stand,mirror(side)])
add('grapevine-steps', [side,p(stand,fl=(.61,.91),fr=(.55,.88)),mirror(side),p(stand,fr=(.39,.91),fl=(.45,.88))])
add('salsa-basic', [stand,p(heel,fl=(.36,.85),hr=(.61,.45)),stand,mirror(heel)])
add('box-step', [stand,shift(stand,x=-.08,y=-.04),shift(stand,x=.08,y=-.04),mirror(heel)])
add('skater-steps', [p(side,fr=(.42,.87),kr=(.57,.77),hl=(.40,.40)),mirror(p(side,fr=(.42,.87),kr=(.57,.77),hl=(.40,.40)))])
add('quiet-dance-sway quiet-balance-shift', [side,stand,mirror(side),stand])
add('disco-reaches', [stand,disco,stand,mirror(disco)])
add('standing-side-reach quiet-side-bends', [stand,bend,stand,mirror(bend)])
add('quiet-standing-reach', [stand,reach])
add('gentle-torso-turn', [stand,turn,stand,mirror(turn)])
add('shadow-boxing', [turn,p(turn,sl=(.46,.31),hl=(.21,.32),el=(.34,.32),hr=(.58,.29)),mirror(turn),mirror(p(turn,hl=(.21,.32),el=(.34,.32),hr=(.58,.29)))])
add('standing-chest-opener quiet-chest-opening', [p(stand,hl=(.43,.48),hr=(.57,.48)),p(stand,el=(.29,.43),er=(.71,.43),hl=(.31,.30),hr=(.69,.30))])
add('shoulder-rolls', [stand,p(stand,sl=(.43,.28),sr=(.57,.28),el=(.36,.41),er=(.64,.41)),p(stand,sl=(.45,.31),sr=(.55,.31),el=(.39,.44),er=(.61,.44))])
add('standing-breathing', [p(stand,hl=(.46,.52),hr=(.54,.52)),p(stand,sl=(.42,.30),sr=(.58,.30),hl=(.44,.50),hr=(.56,.50))])
add('standing-glute-squeeze', [stand,p(stand,hip=(.50,.57),kl=(.45,.75),kr=(.55,.75),hl=(.44,.58),hr=(.56,.58))])
add('calf-raise silent-heel-raise', [stand,shift(p(stand,fl=(.43,.94),fr=(.57,.94)),y=-.035)])
add('standing-hip-abduction', [stand,p(stand,kr=(.68,.72),fr=(.79,.87)),stand,mirror(p(stand,kr=(.68,.72),fr=(.79,.87)))])
add('ankle-circles', [p(stand,kl=(.40,.70),fl=(.35,.82)),p(stand,kl=(.40,.70),fl=(.39,.85)),p(stand,kl=(.40,.70),fl=(.44,.82)),p(stand,kl=(.40,.70),fl=(.40,.79))])
add('tree-balance', [tree,p(tree,head=(.495,.17),hip=(.505,.58))])
add('warrior-two', [warrior,p(warrior,hip=(.50,.65),kl=(.27,.69))])
add('gentle-hip-hinge', [stand,hinge])
add('standing-forward-fold', [hinge,fold])
add('reverse-lunge', [stand,lunge,stand,mirror(lunge)])
add('wall-sit', [wall_sit,p(wall_sit,sl=(.67,.455),sr=(.71,.455))])
add('wall-push-up quiet-wall-press', [wall,wall_press])
add('wall-plank', [wall,p(wall,sl=(.56,.315),sr=(.58,.305))])
add('high-plank', [plank,p(plank,hip=(.57,.615))])
add('forearm-plank', [forearm,p(forearm,hip=(.58,.665))])
add('push-up', [plank,push])
add('knee-push-up', [knee_plank,knee_push])
add('mountain-climbers', [plank,p(plank,kl=(.43,.70),fl=(.57,.84)),plank,p(plank,kr=(.46,.68),fr=(.60,.82))])
add('half-burpees', [stand,squat,p(quad,hip=(.57,.61),kl=(.45,.78),kr=(.49,.77)),plank,p(quad,hip=(.57,.61),kl=(.45,.78),kr=(.49,.77)),squat])
add('bird-dog', [quad,bird,quad,p(quad,er=(.21,.48),hr=(.09,.47),kl=(.74,.55),fl=(.90,.56))])
add('cat-cow', [quad,cat,quad,cow])
add('child-pose', [child,p(child,hip=(.72,.68),head=(.28,.70))])
add('downward-dog', [dog,p(dog,kl=(.70,.57),kr=(.73,.56),hip=(.56,.30))])
add('low-cobra', [cobra_low,cobra])
add('glute-bridge', [supine,bridge])
add('gentle-crunch', [supine,crunch])
add('dead-bug', [dead,dead_extend,dead,p(dead,er=(.19,.66),hr=(.08,.58),kl=(.68,.76),fl=(.86,.82))])
add('bent-knee-leg-raise', [supine,p(supine,kl=(.51,.58),kr=(.54,.56),fl=(.69,.56),fr=(.72,.54))])
add('heel-slides', [supine,p(supine,kl=(.67,.83),fl=(.91,.87)),supine,p(supine,kr=(.69,.81),fr=(.93,.85))])
add('side-plank-from-knees', [side_plank,p(side_plank,hip=(.55,.585))])
rope = p(stand,el=(.33,.44),er=(.67,.44),hl=(.29,.53),hr=(.71,.53),fl=(.46,.91),fr=(.54,.91))
add('invisible-rope-skipping', [rope,shift(p(rope,hl=(.30,.49),hr=(.70,.49),kl=(.45,.74),kr=(.55,.74)),y=-.065)])
walk = p(stand,el=(.38,.41),hl=(.43,.32),er=(.64,.46),hr=(.69,.57),kl=(.34,.74),fl=(.27,.91),kr=(.62,.74),fr=(.70,.88))
jog = p(walk,fl=(.33,.84),fr=(.64,.78),kr=(.62,.72),hl=(.42,.33),hr=(.66,.43))
add('campus-walk brisk-campus-walk', [walk,stand,mirror(walk),stand])
add('easy-jog walk-jog-intervals', [jog,shift(stand,y=-.035),mirror(jog),shift(stand,y=-.035)])
add('stair-climbing', [p(low_knee,fl=(.37,.77)),shift(stand,y=-.075),p(mirror(low_knee),fr=(.63,.77)),shift(stand,y=-.075)])

path=Path('app/src/main/assets/exercises.json')
items=json.loads(path.read_text())
assert set(cycles) == {e['id'] for e in items}, (set(cycles) ^ {e['id'] for e in items})
for e in items:
    e['demo'] = [{'points':[round(v,4) for point in frame for v in point]} for frame in cycles[e['id']]]
    assert all(0 <= v <= 1 for f in e['demo'] for v in f['points']), e['id']
path.write_text(json.dumps(items, indent=2)+'\n')
print(f'Authored {len(items)} exercise cycles.')
