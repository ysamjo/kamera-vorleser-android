package de.stegmann.kameravorleser;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Queue und MediaPlayer gehören dem Main Thread. HTTP, PCM und Dateien dem Worker. */
final class CloudSpeechPlayer {
    interface Listener {
        void status(String id, String status);
        void finished(String id, boolean failed, boolean partiallyPlayed);
    }
    private static final class Pending {
        final String id, fishKey, googleKey;
        final CloudSpeechRequest request;
        final float volume;
        final long queuedAt = SystemClock.elapsedRealtime();
        final CloudSpeechClient.Job job = new CloudSpeechClient.Job();
        Future<?> work;
        PcmSpeechOutput fishOutput;
        Pending(String id, CloudSpeechRequest request, String fishKey, String googleKey, float volume) {
            this.id = id; this.request = request; this.fishKey = fishKey; this.googleKey = googleKey;
            this.volume = volume;
        }
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService aborter = Executors.newSingleThreadExecutor();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final File directory;
    private final Listener listener;
    private Pending active;
    private MediaPlayer player;
    private File audioFile;
    private boolean closed;

    CloudSpeechPlayer(Context context, Listener listener) {
        directory = new File(context.getCacheDir(), "cloud-tts");
        this.listener = listener;
        // Ein Prozessabbruch kann temporäre MP3-Dateien hinterlassen; kein Langzeit-Audioarchiv.
        File[] old = directory.listFiles();
        if (old != null) for (File file : old) file.delete();
    }

    boolean speak(String id, CloudSpeechRequest request, String fishKey, String googleKey, float volume) {
        if (closed || pending.size() + (active == null ? 0 : 1) >= 2) return false;
        pending.add(new Pending(id, request, fishKey, googleKey, volume));
        next();
        return true;
    }

    private void next() {
        if (active != null || closed || pending.isEmpty()) return;
        Pending item = pending.remove();
        active = item;
        listener.status(item.id, "Erzeuge Sprache · Fish Audio → Google-Backup");
        item.fishOutput = new PcmSpeechOutput(item.volume, () -> item.job.cancelled,
                () -> main.post(() -> {
                    if (active == item && !item.job.cancelled) playbackStarted(item, "Fish Audio");
                }));
        item.work = worker.submit(() -> {
            File file = null;
            long startedAt = SystemClock.elapsedRealtime();
            try {
                byte[] audio;
                CloudSpeechClient client = new CloudSpeechClient();
                try (PcmSpeechOutput output = item.fishOutput) {
                    audio = client.playOrSynthesize(item.request, item.fishKey, item.googleKey, item.job, () -> {
                        client.streamFish(item.request, item.fishKey, item.job, output);
                        Log.d("PokopiaTts", "Fish Audio · Synthese und Download "
                                + (SystemClock.elapsedRealtime() - startedAt) + " ms");
                        output.awaitPlaybackEnd();
                    }, output::started);
                }
                if (item.job.cancelled) return;
                if (audio == null) {
                    main.post(() -> {
                        if (active == item && !item.job.cancelled) finish(item, false);
                    });
                    return;
                }
                Log.d("PokopiaTts", CloudSpeechClient.GOOGLE_BACKUP + " · Synthese und Download "
                        + (SystemClock.elapsedRealtime() - startedAt) + " ms");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Audioordner nicht verfügbar");
                file = File.createTempFile("speech-", ".mp3", directory);
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(audio); }
                if (item.job.cancelled) { file.delete(); return; }
                File ready = file;
                main.post(() -> play(item, ready, CloudSpeechClient.GOOGLE_BACKUP));
            } catch (Exception error) {
                if (file != null) file.delete();
                main.post(() -> {
                    if (active == item && !item.job.cancelled) {
                        Log.d("PokopiaTts", "Cloud-Ausgabe fehlgeschlagen nach "
                                + (SystemClock.elapsedRealtime() - startedAt) + " ms");
                        finish(item, true);
                    }
                });
            }
        });
    }

    private void play(Pending item, File file, String provider) {
        if (closed || active != item || item.job.cancelled) { file.delete(); return; }
        audioFile = file;
        MediaPlayer media = new MediaPlayer();
        player = media;
        try {
            media.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            media.setDataSource(file.getAbsolutePath());
            media.setVolume(item.volume, item.volume);
            media.setOnPreparedListener(p -> {
                if (active == item && player == p && !item.job.cancelled) {
                    try {
                        p.start();
                        playbackStarted(item, provider);
                    } catch (RuntimeException error) { finish(item, true); }
                }
            });
            media.setOnCompletionListener(p -> { if (active == item) finish(item, false); });
            media.setOnErrorListener((p, what, extra) -> { if (active == item) finish(item, true); return true; });
            media.prepareAsync();
        } catch (Exception error) { finish(item, true); }
    }

    private void playbackStarted(Pending item, String provider) {
        Log.d("PokopiaTts", provider + " · Übergabe bis Wiedergabe "
                + (SystemClock.elapsedRealtime() - item.queuedAt) + " ms");
        listener.status(item.id, "Lese vor · " + provider);
    }

    private void finish(Pending item, boolean failed) {
        release();
        active = null;
        listener.finished(item.id, failed, failed && item.fishOutput.started());
        next();
    }

    void stop() {
        pending.clear();
        Pending old = active;
        active = null; // Zuerst entwerten, dann Netzwerk und Wiedergabe stoppen.
        if (old != null) {
            old.job.cancel(aborter);
            old.fishOutput.stop();
            if (old.work != null) old.work.cancel(true);
        }
        release();
    }

    private void release() {
        if (player != null) {
            player.setOnPreparedListener(null);
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.release();
            player = null;
        }
        if (audioFile != null) { audioFile.delete(); audioFile = null; }
    }

    void close() {
        closed = true;
        stop();
        worker.shutdownNow();
        aborter.shutdown();
    }
}
