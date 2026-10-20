package com.packages.kof;

import com.android.util.Log;
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;

/** Bounded polyphonic effects. One daemon owns all audio-device operations. */
public final class FightAudio implements AutoCloseable {
    interface Output extends AutoCloseable {
        int write(byte[] data, int offset, int count) throws Exception;
        void flush();
        @Override void close();
    }
    interface OutputFactory { Output open() throws Exception; }
    private static final class Cue {
        final FightEngine.Sound sound;
        final long generation;
        Cue(FightEngine.Sound sound, long generation) { this.sound = sound; this.generation = generation; }
    }
    private static final class Voice {
        final short[] samples;
        int position;
        final long generation;
        Voice(short[] samples, long generation) { this.samples = samples; this.generation = generation; }
    }
    private final BlockingQueue<Cue> queue = new ArrayBlockingQueue<>(32);
    private final OutputFactory factory;
    private final Object control = new Object();
    private volatile boolean muted, paused, closed, unavailable;
    private volatile long generation;
    private Thread worker;

    public FightAudio() {
        this(FightAudio::openDevice);
        muted = !Boolean.parseBoolean(System.getProperty("framework.kof.sound", "true"));
    }
    FightAudio(OutputFactory factory) { this.factory = Objects.requireNonNull(factory); }
    public boolean isMuted() { return muted; }
    public boolean isAvailable() { return !unavailable; }
    public void setMuted(boolean value) {
        synchronized (control) { if (muted != value) { muted = value; clearLocked(); } }
    }
    public void setPaused(boolean value) {
        synchronized (control) { if (paused != value) { paused = value; clearLocked(); } }
    }
    public void clear() { synchronized (control) { clearLocked(); } }
    private void clearLocked() { generation++; queue.clear(); }
    public void play(FightEngine.Sound sound) {
        Objects.requireNonNull(sound);
        synchronized (control) {
            if (closed || muted || paused || unavailable) return;
            if (!queue.offer(new Cue(sound, generation))) {
                queue.poll(); queue.offer(new Cue(sound, generation));
            }
            if (worker == null) {
                worker = new Thread(this::run, "quanhuang-audio"); worker.setDaemon(true); worker.start();
            }
        }
    }
    @Override public void close() {
        // Never stop/close a SourceDataLine or wait for hardware on the UI thread.
        synchronized (control) { closed = true; clearLocked(); }
    }
    private void run() {
        Output output = null;
        List<Voice> voices = new ArrayList<>();
        Map<FightEngine.Sound, short[]> bank = new EnumMap<>(FightEngine.Sound.class);
        long currentGeneration = generation;
        byte[] pcm = new byte[882]; // 20 ms, signed 16-bit, little endian.
        try {
            while (!closed) {
                if (currentGeneration != generation) {
                    // A fresh cue may wake an idle poll after clear()/restart. Keep voices
                    // belonging to the new generation instead of dropping that first cue.
                    final long nextGeneration = generation;
                    voices.removeIf(voice -> voice.generation != nextGeneration);
                    if (output != null) output.flush(); currentGeneration = nextGeneration;
                }
                Cue cue = voices.isEmpty() ? queue.poll(50, TimeUnit.MILLISECONDS) : queue.poll();
                while (cue != null) {
                    if (!muted && !paused && cue.generation == generation) {
                        if (voices.size() >= 8) voices.remove(0);
                        short[] samples = bank.get(cue.sound);
                        if (samples == null) { samples = SoundBank.create(cue.sound); bank.put(cue.sound, samples); }
                        voices.add(new Voice(samples, cue.generation));
                    }
                    cue = queue.poll();
                }
                if (closed || muted || paused || currentGeneration != generation) continue;
                if (voices.isEmpty()) continue;
                if (output == null) output = factory.open();
                if (closed || muted || paused || currentGeneration != generation) continue;
                Arrays.fill(pcm, (byte) 0);
                int count = 0;
                for (int i = 0; i < pcm.length / 2; i++) {
                    int sample = 0;
                    for (Voice voice : voices) if (voice.position < voice.samples.length) sample += voice.samples[voice.position++];
                    sample = Math.max(-32768, Math.min(32767, sample));
                    pcm[2 * i] = (byte) sample; pcm[2 * i + 1] = (byte) (sample >> 8);
                    count = i + 1;
                    boolean finished = true;
                    for (Voice voice : voices) if (voice.position < voice.samples.length) { finished = false; break; }
                    if (finished) break;
                }
                for (Iterator<Voice> it = voices.iterator(); it.hasNext();) {
                    Voice voice = it.next(); if (voice.position >= voice.samples.length) it.remove();
                }
                int offset = 0;
                while (offset < count * 2 && !closed && !muted && !paused && currentGeneration == generation) {
                    int written = output.write(pcm, offset, count * 2 - offset);
                    if (written < 0 || written > count * 2 - offset || (written & 1) != 0) throw new IllegalStateException("Invalid PCM write");
                    if (written == 0) Thread.sleep(4);
                    offset += written;
                }
            }
        } catch (Exception e) {
            if (!closed) { unavailable = true; Log.w("Quanhuang sound unavailable: " + e.getClass().getSimpleName()); }
        } finally {
            if (output != null) {
                try { output.flush(); } finally { output.close(); }
            }
        }
    }
    private static Output openDevice() throws Exception {
        AudioFormat format = new AudioFormat(SoundBank.RATE, 16, 1, true, false);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
        try { line.open(format, 2204); line.start(); }
        catch (Exception e) { line.close(); throw e; }
        return new Output() {
            @Override public int write(byte[] data, int offset, int count) {
                int available = Math.min(count, line.available()) & ~1;
                return available == 0 ? 0 : line.write(data, offset, available);
            }
            @Override public void flush() { line.flush(); }
            @Override public void close() { line.stop(); line.close(); }
        };
    }
}
