#!/usr/bin/env python3
"""Synthesize original swing and metallic impact effects, without external samples."""
from pathlib import Path
import array, math, random, wave, json, hashlib, sys
OUT=Path(__file__).resolve().parents[1]/'res/arena/audio'
RATE=22050
records=[]
for name,duration in [('scatter-release',.20),('bow-release',.18),('arrow-hit',.18),('sword-swing',.18),('sword-hit',.20),('stone-hit',.16),('kill-hit',.28),('slam-charge',.23),('slam-impact',.38),('dash-whoosh',.23),('meteor-charge',.24),('meteor-impact',.55)]:
    rng=random.Random(8743 if name=='sword-swing' else 1741)
    pcm=array.array('h');low=0;previous=0
    for i in range(round(duration*RATE)):
        t=i/RATE;noise=rng.uniform(-1,1);low=.7*low+.3*noise
        if name=='scatter-release':
            value=sum(math.sin(2*math.pi*((340+j*70)*t-350*t*t))*math.exp(-max(0,t-j*.008)*26) for j in range(3))*.17+(noise-low)*math.exp(-t*32)*.22
        elif name=='bow-release':
            value=(math.sin(2*math.pi*(420*t-480*t*t))*.5+(noise-low)*.2)*math.exp(-t*28)*.65
        elif name=='arrow-hit':
            value=(low*.65+math.sin(2*math.pi*170*t)*.35)*math.exp(-t*36)*.75
        elif name=='sword-swing':
            env=math.sin(math.pi*t/duration)**1.4
            value=((noise-low)*.55+low*.8+math.sin(2*math.pi*(180*t+700*t*t))*.13)*env*.65
        elif name=='dash-whoosh':
            env=math.sin(math.pi*t/duration)**.7
            value=((noise-low)*.35+low*.65+math.sin(2*math.pi*(200*t-170*t*t))*.10)*env*.65
        elif name=='meteor-charge':
            env=math.sin(math.pi*t/duration)**.7
            value=(math.sin(2*math.pi*(180*t+450*t*t))*.28+low*.35)*env*.75
        elif name=='meteor-impact':
            value=(math.sin(2*math.pi*(62*t-22*t*t))*.75*math.exp(-t*10)+low*.85*math.exp(-t*12)+noise*.35*math.exp(-t*55))*min(1,t/.003)
        elif name=='slam-charge':
            env=math.sin(math.pi*t/duration)**.8
            value=(low*.4+math.sin(2*math.pi*(95*t+180*t*t))*.18)*env*.6
        elif name=='slam-impact':
            value=(math.sin(2*math.pi*(85*t-35*t*t))*.6*math.exp(-t*16)+low*.65*math.exp(-t*22)+noise*.25*math.exp(-t*80))*min(1,t/.002)
        elif name=='stone-hit':
            value=(noise*.65*math.exp(-t*48)+math.sin(2*math.pi*165*t)*.5*math.exp(-t*35))*min(1,t/.001)
        elif name=='kill-hit':
            value=sum(math.sin(2*math.pi*f*t)*gain for f,gain in [(523.25,.22),(783.99,.13),(1046.5,.09)])*math.exp(-t*15)*min(1,t/.003)
        else:
            attack=min(1,t/.002)
            ring=sum(math.sin(2*math.pi*f*t)*math.exp(-t*decay)*gain for f,decay,gain in [(740,23,.28),(1370,32,.16),(2210,44,.11)])
            value=attack*(ring+noise*.45*math.exp(-t*75)+math.sin(2*math.pi*115*t)*.24*math.exp(-t*42))*.8
        value=math.tanh(value)*24000
        pcm.extend((round(value*.94),round(value)))
    if sys.byteorder!='little':pcm.byteswap()
    path=OUT/(name+'.wav')
    with wave.open(str(path),'wb') as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(pcm.tobytes())
    records.append(dict(file=path.name,seconds=duration,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
p=OUT/'sword-manifest.json';p.write_text(json.dumps(dict(origin='Original synthesized effects for Rift Arena; no external audio samples.',effects=records),indent=2)+'\n')
print('Generated sword swing and hit WAVs')
