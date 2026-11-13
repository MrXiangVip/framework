#!/usr/bin/env python3
"""Original deterministic instrumental loops; no samples or third-party music."""
from pathlib import Path
from array import array
import math, random, wave, hashlib, json
RATE=22050
BPM=112
BEAT=60/BPM
BARS=16
LENGTH=BEAT*4*BARS
OUT=Path(__file__).resolve().parents[1]/'res/arena/audio'

def compose(battle):
    count=round(LENGTH*RATE)
    left=array('f',[0])*count
    right=array('f',[0])*count
    rng=random.Random(4301+int(battle))
    def note(start,beats,midi,gain,pan=0,kind='bell'):
        duration=beats*BEAT
        freq=440*2**((midi-69)/12)
        for i in range(round(duration*RATE)):
            t=i/RATE
            attack=min(1,t/.018)
            release=min(1,(duration-t)/.09)
            phase=2*math.pi*freq*t
            if kind=='pad':
                value=(math.sin(phase)+.22*math.sin(phase*2)+.1*math.sin(phase*3))*attack*release
            elif kind=='bass':
                value=(math.sin(phase)+.3*math.sin(phase*2))*attack*release*math.exp(-t*1.2)
            else:
                value=(math.sin(phase)+.32*math.sin(phase*2)+.12*math.sin(phase*3))*attack*release*math.exp(-t*3)
            at=(round(start*RATE)+i)%count
            value*=gain
            left[at]+=value*(1-pan)*.5
            right[at]+=value*(1+pan)*.5
    chords=[(50,57,62,65),(46,53,58,62),(53,60,65,69),(48,55,60,64)]
    melody=[[74,77,81,77,76,74,72,69],[70,74,77,74,72,70,69,65],[77,81,84,81,79,77,76,72],[72,76,79,76,74,72,69,72]]
    for bar in range(BARS):
        chord=chords[bar%4]
        for j,pitch in enumerate(chord):note(bar*4*BEAT,4,pitch,.055,-.6+j*.4,'pad')
        for beat in range(4):
            note((bar*4+beat)*BEAT,.85,chord[0]-12,.16 if battle else .085,0,'bass')
        for step in range(8):
            note((bar*4+step*.5)*BEAT,.65,chord[step%4]+12,.075 if battle else .055,(-1 if step%2 else 1)*.55)
        if bar>=4:
            for step,pitch in enumerate(melody[bar%4]):
                if not battle and step%2:continue
                note((bar*4+step*.5)*BEAT,.85,pitch+(12 if bar>=12 else 0),.095 if battle else .065,.15)
        if battle:
            for beat in range(4):
                start=round((bar*4+beat)*BEAT*RATE)
                for i in range(round(.16*RATE)):
                    t=i/RATE
                    kick=math.sin(2*math.pi*(48*t+6*(1-math.exp(-t*25))))*math.exp(-t*26)*.2 if beat%2==0 else rng.uniform(-1,1)*math.exp(-t*32)*.1
                    at=(start+i)%count;left[at]+=kick;right[at]+=kick
            for step in range(8):
                start=round((bar*4+step*.5)*BEAT*RATE)
                prev=0
                for i in range(round(.04*RATE)):
                    noise=rng.uniform(-1,1);v=(noise-prev)*math.exp(-i/RATE*110)*.025;prev=noise
                    at=(start+i)%count;left[at]+=v*.7;right[at]+=v
    # Soft limiting, quiet headroom, and wraparound reverb produce a seamless loop.
    pcm=array('h')
    for i in range(count):
        for samples in (left,right):
            value=samples[i]+samples[(i-round(.27*RATE))%count]*.18+samples[(i-round(.43*RATE))%count]*.10
            pcm.append(round(math.tanh(value)*22000))
    import sys
    if sys.byteorder!='little':pcm.byteswap()
    path=OUT/('battle.wav' if battle else 'menu.wav')
    with wave.open(str(path),'wb') as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(RATE);f.writeframes(pcm.tobytes())
    return dict(file=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),seconds=count/RATE)

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    tracks=[compose(False),compose(True)]
    (OUT/'manifest.json').write_text(json.dumps(dict(origin='Original procedural composition for Rift Arena; synthesized without external samples.',bpm=BPM,tracks=tracks),indent=2)+'\n')
    print(tracks)
