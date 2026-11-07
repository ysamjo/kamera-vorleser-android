package de.stegmann.kameravorleser;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.SystemClock;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Der Worker schreibt und gibt frei. Stop darf die Ausgabe auch während eines HTTP-Reads leeren. */
final class PcmSpeechOutput implements PcmSpeechStream.Sink, Closeable {
    private static final int STALL_TIMEOUT_MS = 5000;
    private final BooleanSupplier cancelled;
    private final Runnable onStarted;
    private final float volume;
    private AudioTrack track;
    private int framesWritten;
    private volatile boolean started;

    PcmSpeechOutput(float volume, BooleanSupplier cancelled, Runnable onStarted) {
        this.volume = volume;
        this.cancelled = cancelled;
        this.onStarted = onStarted;
    }

    boolean started() { return started; }

    @Override public void write(short[] samples, int count) throws IOException {
        long lastProgress = SystemClock.elapsedRealtime();
        int offset = 0;
        while (offset < count) {
            int written = writeAvailable(samples, offset, count - offset);
            if (written > 0) {
                offset += written;
                lastProgress = SystemClock.elapsedRealtime();
            } else {
                waitForProgress(lastProgress);
            }
        }
    }

    private synchronized int writeAvailable(short[] samples, int offset, int count) throws IOException {
        CloudFallback.check(cancelled);
        try {
            if (track == null) initializeTrack();
            int written = track.write(samples, offset, count, AudioTrack.WRITE_NON_BLOCKING);
            if (written < 0) throw new IOException("AudioTrack-Schreibfehler " + written);
            framesWritten += written;
            startIfBuffered(written);
            return written;
        } catch (RuntimeException error) {
            throw new IOException("PCM-Wiedergabe konnte nicht gestartet oder fortgesetzt werden", error);
        }
    }

    private void startIfBuffered(int written) throws IOException {
        if (started || written == 0) return;
        if (Build.VERSION.SDK_INT >= 31) {
            track.setStartThresholdInFrames(framesWritten);
        } else if (framesWritten < track.getBufferSizeInFrames()) {
            return;
        }
        CloudFallback.check(cancelled);
        track.play();
        started = true;
        onStarted.run();
    }

    private void initializeTrack() throws IOException {
        int rate = CloudSpeechRequest.FISH_SAMPLE_RATE;
        int minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) throw new IOException("PCM-Ausgabe ist nicht verfügbar");
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(minBuffer, PcmSpeechStream.START_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED || track.setVolume(volume) != AudioTrack.SUCCESS) {
            throw new IOException("AudioTrack konnte nicht eingerichtet werden");
        }
    }

    void awaitPlaybackEnd() throws IOException {
        int contentFrames = framesWritten;
        int missing = framesMissingForDrain();
        if (missing > 0) write(new short[missing], missing);
        long lastProgress = SystemClock.elapsedRealtime();
        int previous = -1;
        while (true) {
            int played = playedFrames();
            if (played >= contentFrames) return;
            if (played != previous) {
                previous = played;
                lastProgress = SystemClock.elapsedRealtime();
            }
            waitForProgress(lastProgress);
        }
    }

    private synchronized int framesMissingForDrain() throws IOException {
        CloudFallback.check(cancelled);
        if (track == null) throw new IOException("Keine PCM-Daten erhalten");
        // Nach einem Unterlauf kann auch der letzte kurze Audioblock auf den Startpuffer warten.
        if (Build.VERSION.SDK_INT >= 31 && started) {
            track.setStartThresholdInFrames(1);
            return 0;
        }
        int queued = Math.max(0, framesWritten - track.getPlaybackHeadPosition());
        return Math.max(0, track.getBufferSizeInFrames() - queued);
    }

    synchronized int playedFrames() throws IOException {
        CloudFallback.check(cancelled);
        return track.getPlaybackHeadPosition();
    }

    private void waitForProgress(long lastProgress) throws IOException {
        CloudFallback.check(cancelled);
        if (SystemClock.elapsedRealtime() - lastProgress >= STALL_TIMEOUT_MS) {
            throw new IOException("PCM-Wiedergabe kommt nicht voran");
        }
        try { Thread.sleep(10); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Abgebrochen");
        }
    }

    synchronized void stop() {
        if (track == null) return;
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
            track.flush();
        } catch (IllegalStateException ignored) {
            // Ein bereits ungültiger Track wird beim Verlassen des Workers freigegeben.
        }
    }

    @Override public synchronized void close() {
        if (track == null) return;
        stop();
        track.release();
        track = null;
    }
}
