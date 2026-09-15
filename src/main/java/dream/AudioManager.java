package dream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays the game's embedded MP3s.
 *
 * <p>Two things make this work where the original did not. First, the MP3s ship
 * inside the jar and are read off the classpath instead of being pulled from a
 * CodeHS URL. Second, {@code javax.sound.sampled} cannot decode MP3 on its own,
 * so the mp3spi service provider on the classpath converts each file to raw PCM,
 * which the sound card actually understands.
 *
 * <p>Short effects are decoded once into memory and replayed from a small pool
 * of clips, so overlapping keystrokes do not cut each other off. Music streams
 * on a daemon thread so it never blocks the story.
 */
public final class AudioManager {

    public static final String STARTUP_WHIRR = "StartupWhirr";

    public static final String[] KEYBOARD_CLICKS = {
        "KeyboardClick1", "KeyboardClick2", "KeyboardClick3"
    };

    public static final String[] SCROLL_SOUNDS = {
        "ScrollSound1", "ScrollSound2", "ScrollSound3",
        "ScrollSound4", "ScrollSound5", "ScrollSound6"
    };

    /** How many simultaneous copies of one effect we allow. */
    private static final int VOICES_PER_SOUND = 4;

    private static final Map<String, Sample> SAMPLES = new ConcurrentHashMap<>();
    private static final Map<String, List<Clip>> VOICES = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private static volatile boolean audioAvailable = true;
    private static volatile boolean reportedFailure = false;
    private static volatile float sfxVolume = 0.7f;
    private static volatile float musicVolume = 0.5f;
    private static volatile MusicStream currentMusic = null;

    /** Rotates which voice gets reused when a sound's pool is fully busy. */
    private static int stealCounter = 0;

    private AudioManager() { }

    /** A decoded sound held in memory, ready to hand to a clip. */
    private static final class Sample {
        final AudioFormat format;
        final byte[] pcm;

        Sample(AudioFormat format, byte[] pcm) {
            this.format = format;
            this.pcm = pcm;
        }
    }

    // ---------------------------------------------------------------- loading

    /**
     * Decodes the given effects in the background so the first keystroke does
     * not pay the decoding cost.
     */
    public static void preloadAsync(String... names) {
        Thread loader = new Thread(() -> {
            for (String name : names) {
                load(name);
            }
            Log.info("Preloaded " + SAMPLES.size() + " sound effect(s).");
        }, "dream-audio-preload");
        loader.setDaemon(true);
        loader.start();
    }

    private static Sample load(String name) {
        Sample cached = SAMPLES.get(name);
        if (cached != null || !audioAvailable) {
            return cached;
        }

        try (InputStream raw = Assets.open("audio/" + name + ".mp3")) {
            if (raw == null) {
                Log.warn("Missing audio asset: " + name + ".mp3");
                return null;
            }

            try (AudioInputStream encoded = AudioSystem.getAudioInputStream(raw);
                 AudioInputStream decoded = toPcm(encoded)) {

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = decoded.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }

                AudioFormat format = decoded.getFormat();
                byte[] pcm = trimSilence(buffer.toByteArray(), format);

                Sample sample = new Sample(format, pcm);
                SAMPLES.put(name, sample);
                return sample;
            }
        } catch (Exception e) {
            reportFailureOnce("Could not decode " + name + ".mp3", e);
            return null;
        }
    }

    /** Anything quieter than this counts as silence, as a fraction of full scale. */
    private static final double SILENCE_THRESHOLD = 0.005;

    /** Kept either side of the audible part so nothing clicks or cuts off. */
    private static final double HEAD_PADDING_SECONDS = 0.02;
    private static final double TAIL_PADDING_SECONDS = 0.04;

    /**
     * Strips leading and trailing silence from a decoded effect.
     *
     * <p>The source MP3s are heavily padded: the keyboard clicks are a 150ms tap
     * followed by over a second of nothing, and some scroll sounds start a third
     * of the way in. Left alone that makes scroll clicks feel late and keeps
     * voices in the pool busy playing silence, so rapid typing runs out of them.
     */
    private static byte[] trimSilence(byte[] pcm, AudioFormat format) {
        int frameSize = format.getFrameSize();
        if (format.getSampleSizeInBits() != 16 || frameSize <= 0
                || pcm.length < frameSize * 2) {
            return pcm;
        }

        int frames = pcm.length / frameSize;
        int threshold = (int) (SILENCE_THRESHOLD * Short.MAX_VALUE);

        int firstAudible = -1;
        int lastAudible = -1;
        for (int frame = 0; frame < frames; frame++) {
            if (peakAmplitude(pcm, frame * frameSize, frameSize) > threshold) {
                if (firstAudible < 0) {
                    firstAudible = frame;
                }
                lastAudible = frame;
            }
        }

        if (firstAudible < 0) {
            return pcm; // Entirely silent: leave it be rather than empty it.
        }

        float rate = format.getSampleRate();
        int headPad = (int) (HEAD_PADDING_SECONDS * rate);
        int tailPad = (int) (TAIL_PADDING_SECONDS * rate);

        int start = Math.max(0, firstAudible - headPad);
        int end = Math.min(frames, lastAudible + tailPad + 1);

        int trimmedLength = (end - start) * frameSize;
        if (trimmedLength <= 0 || trimmedLength >= pcm.length) {
            return pcm;
        }

        byte[] trimmed = new byte[trimmedLength];
        System.arraycopy(pcm, start * frameSize, trimmed, 0, trimmedLength);
        return trimmed;
    }

    /** Largest absolute sample across the channels of one frame. */
    private static int peakAmplitude(byte[] pcm, int offset, int frameSize) {
        int peak = 0;
        for (int i = 0; i + 1 < frameSize; i += 2) {
            int index = offset + i;
            if (index + 1 >= pcm.length) {
                break;
            }
            // 16-bit signed, little endian.
            short sample = (short) ((pcm[index + 1] << 8) | (pcm[index] & 0xFF));
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    /**
     * Wraps an encoded stream in a decoder that yields 16-bit signed PCM, which
     * is the format every sound card accepts. This is the step the original
     * player was missing: handing a raw MP3 stream to a SourceDataLine always
     * fails with UnsupportedAudioFileException.
     */
    private static AudioInputStream toPcm(AudioInputStream encoded) {
        AudioFormat source = encoded.getFormat();
        if (source.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
            return encoded;
        }

        int channels = source.getChannels() > 0 ? source.getChannels() : 2;
        float rate = source.getSampleRate() > 0 ? source.getSampleRate() : 44100f;

        AudioFormat target = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            rate,
            16,
            channels,
            channels * 2,
            rate,
            false);

        return AudioSystem.getAudioInputStream(target, encoded);
    }

    // -------------------------------------------------------------- playback

    /** Plays one effect. Returns immediately; overlapping calls are fine. */
    public static void playSfx(String name) {
        if (!audioAvailable || sfxVolume <= 0f) {
            return;
        }

        Sample sample = load(name);
        if (sample == null) {
            return;
        }

        try {
            Clip clip = acquireVoice(name, sample);
            if (clip == null) {
                return;
            }
            clip.setFramePosition(0);
            applyVolume(clip, sfxVolume);
            clip.start();
        } catch (Exception e) {
            reportFailureOnce("Could not play " + name, e);
        }
    }

    /** Plays one of the supplied effects at random, for a bit of variation. */
    public static void playRandomSfx(String[] names) {
        if (names.length > 0) {
            playSfx(names[RANDOM.nextInt(names.length)]);
        }
    }

    /**
     * Finds a clip for this sound that isn't currently sounding, creating one up
     * to the per-sound voice limit. Keeps rapid typing from stuttering.
     */
    private static Clip acquireVoice(String name, Sample sample) throws Exception {
        List<Clip> pool = VOICES.computeIfAbsent(name,
            key -> Collections.synchronizedList(new ArrayList<Clip>()));

        synchronized (pool) {
            for (Clip clip : pool) {
                if (!clip.isRunning()) {
                    return clip;
                }
            }

            if (pool.size() >= VOICES_PER_SOUND) {
                // All voices busy: cycle through them so we never repeatedly cut
                // the same one short. Input stays responsive either way.
                int victim = Math.floorMod(stealCounter++, pool.size());
                Clip clip = pool.get(victim);
                clip.stop();
                return clip;
            }

            Clip clip = AudioSystem.getClip();
            clip.open(sample.format, sample.pcm, 0, sample.pcm.length);
            pool.add(clip);
            return clip;
        }
    }

    /**
     * Streams a track on a background thread. Streaming rather than preloading
     * keeps the long startup whirr out of memory and off the story thread, which
     * is what froze the original for the whole length of the track.
     */
    public static void playMusic(String name, boolean loop) {
        stopMusic();
        if (!audioAvailable || musicVolume <= 0f) {
            return;
        }

        MusicStream stream = new MusicStream(name, loop);
        currentMusic = stream;
        stream.start();
    }

    public static void stopMusic() {
        MusicStream music = currentMusic;
        currentMusic = null;
        if (music != null) {
            music.shutdown();
        }
    }

    /** Pumps a decoded MP3 to the sound card until it ends or is stopped. */
    private static final class MusicStream extends Thread {
        private final String name;
        private final boolean loop;
        private volatile boolean running = true;
        private volatile SourceDataLine line;

        MusicStream(String name, boolean loop) {
            super("dream-music-" + name);
            this.name = name;
            this.loop = loop;
            setDaemon(true);
        }

        void shutdown() {
            running = false;
            SourceDataLine open = line;
            if (open != null) {
                try {
                    open.stop();
                    open.close();
                } catch (Exception ignored) {
                    // Already closing.
                }
            }
        }

        @Override
        public void run() {
            do {
                if (!playOnce()) {
                    return;
                }
            } while (running && loop && currentMusic == this);
        }

        private boolean playOnce() {
            try (InputStream raw = Assets.open("audio/" + name + ".mp3")) {
                if (raw == null) {
                    Log.warn("Missing music asset: " + name + ".mp3");
                    return false;
                }

                try (AudioInputStream encoded = AudioSystem.getAudioInputStream(raw);
                     AudioInputStream decoded = toPcm(encoded)) {

                    AudioFormat format = decoded.getFormat();
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                    SourceDataLine out = (SourceDataLine) AudioSystem.getLine(info);
                    line = out;

                    out.open(format);
                    applyVolume(out, musicVolume);
                    out.start();

                    byte[] buffer = new byte[8192];
                    int read;
                    while (running && currentMusic == this
                            && (read = decoded.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }

                    if (running) {
                        out.drain();
                    }
                    out.stop();
                    out.close();
                    return running;
                }
            } catch (Exception e) {
                reportFailureOnce("Music playback failed for " + name, e);
                return false;
            }
        }
    }

    // --------------------------------------------------------------- volume

    public static void setSfxVolume(float value) {
        sfxVolume = clamp(value);
    }

    public static void setMusicVolume(float value) {
        musicVolume = clamp(value);
    }

    public static float getSfxVolume() {
        return sfxVolume;
    }

    public static float getMusicVolume() {
        return musicVolume;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Mixer gain is measured in decibels, so a linear 0..1 slider has to be
     * converted logarithmically or most of the range sounds identical.
     */
    private static void applyVolume(Line line, float volume) {
        try {
            if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                return;
            }
            FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float decibels = volume <= 0.0001f
                ? gain.getMinimum()
                : (float) (Math.log10(volume) * 20.0);
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
        } catch (Exception ignored) {
            // Some mixers expose no gain control; default volume is fine.
        }
    }

    // --------------------------------------------------------------- status

    public static boolean isAvailable() {
        return audioAvailable;
    }

    /**
     * Audio is a nicety, never a hard requirement. The first failure explains
     * itself and everything after that degrades quietly to silence.
     */
    private static void reportFailureOnce(String message, Throwable cause) {
        if (!reportedFailure) {
            reportedFailure = true;
            audioAvailable = false;
            Log.error(message + " - continuing without sound.", cause);
        }
    }

    /** Releases every open line. Called on shutdown. */
    public static void dispose() {
        stopMusic();
        for (List<Clip> pool : VOICES.values()) {
            synchronized (pool) {
                for (Clip clip : pool) {
                    try {
                        clip.stop();
                        clip.close();
                    } catch (Exception ignored) {
                        // Already gone.
                    }
                }
                pool.clear();
            }
        }
        VOICES.clear();
        SAMPLES.clear();
    }
}
