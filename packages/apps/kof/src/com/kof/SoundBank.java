package com.packages.kof;

import java.util.Random;

/** Original arcade effects synthesized as mono signed PCM; no files or external decoders. */
final class SoundBank {
    static final int RATE = 22050;
    private SoundBank() {}

    static short[] create(FightEngine.Sound sound) {
        double duration;
        switch (sound) {
            case PUNCH: duration = .12; break;
            case KICK: duration = .20; break;
            case HIT: duration = .17; break;
            case BLOCK: duration = .18; break;
            case JUMP: duration = .16; break;
            case LAND: duration = .09; break;
            case SPECIAL: duration = .65; break;
            case KO: duration = .65; break;
            case WIN: case LOSE: duration = .96; break;
            case ROUND: case FIGHT: case TIME_UP: case DRAW: duration = .48; break;
            default: duration = .07;
        }
        short[] samples = new short[(int) (RATE * duration)];
        Random random = new Random(517 + sound.ordinal());
        double phase = 0, smoothNoise = 0;
        for (int i = 0; i < samples.length; i++) {
            double t = (double) i / RATE, progress = t / duration;
            double noise = random.nextDouble() * 2 - 1;
            smoothNoise = .78 * smoothNoise + .22 * noise;
            double frequency = 220, value = 0;
            switch (sound) {
                case PUNCH:
                    value = (noise - smoothNoise) * Math.sin(Math.PI * progress) * .38;
                    break;
                case KICK:
                    frequency = 160 - 100 * progress;
                    value = .30 * noise * Math.sin(Math.PI * progress);
                    break;
                case HIT:
                    frequency = 135 - 95 * progress;
                    value = .42 * smoothNoise * Math.exp(-18 * t);
                    break;
                case BLOCK:
                    frequency = 1050;
                    value = .18 * (Math.sin(2 * Math.PI * 1723 * t) + noise) * Math.exp(-24 * t);
                    break;
                case JUMP: frequency = 260 + 700 * progress; break;
                case LAND:
                    frequency = 72;
                    value = .27 * smoothNoise * (1 - progress);
                    break;
                case SPECIAL:
                    frequency = 180 + 1600 * progress;
                    value = .18 * noise * Math.sin(Math.PI * progress);
                    break;
                case KO: frequency = 420 * Math.pow(.13, progress); break;
                case WIN: frequency = melody(t, new double[]{523.25, 659.25, 783.99, 1046.5}, .24); break;
                case LOSE: frequency = melody(t, new double[]{392, 349.23, 311.13, 261.63}, .24); break;
                case ROUND: frequency = melody(t, new double[]{330, 440, 660}, .16); break;
                case FIGHT: frequency = melody(t, new double[]{523.25, 783.99}, .24); break;
                case DRAW: frequency = melody(t, new double[]{392, 392, 330}, .16); break;
                case TIME_UP: frequency = ((int) (t / .12) % 2 == 0) ? 880 : 440; break;
                case SELECT: frequency = 880; break;
                default: break;
            }
            phase += 2 * Math.PI * frequency / RATE;
            double tone = .24 * Math.sin(phase) + .05 * Math.sin(phase * 2);
            if (sound == FightEngine.Sound.PUNCH) tone = 0;
            if (sound == FightEngine.Sound.HIT || sound == FightEngine.Sound.BLOCK) tone *= Math.exp(-18 * t);
            double fade = Math.min(1, t / .004) * Math.min(1, (duration - t) / .025);
            if (sound == FightEngine.Sound.WIN || sound == FightEngine.Sound.LOSE)
                fade *= Math.min(1, (.24 - t % .24) / .025);
            samples[i] = (short) (Math.max(-.85, Math.min(.85, (tone + value) * fade)) * 32767);
        }
        return samples;
    }

    private static double melody(double t, double[] notes, double interval) {
        return notes[Math.min(notes.length - 1, (int) (t / interval))];
    }
}
