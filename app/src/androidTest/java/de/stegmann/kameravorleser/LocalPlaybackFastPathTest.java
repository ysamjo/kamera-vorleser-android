package de.stegmann.kameravorleser;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LocalPlaybackFastPathTest {

    @Test(timeout = 15000)
    public void playsOpusStoryPackFileWithoutDeletingIt() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testCtx = InstrumentationRegistry.getInstrumentation().getContext();
        File root = new File(target.getCacheDir(), "fastpath-opus-" + SystemClock.elapsedRealtime());
        File audioDir = new File(root, "audio");
        assertTrue(audioDir.mkdirs());
        try {
            File oggFile = new File(audioDir, "dlg_0001.ogg");
            try (InputStream in = testCtx.getResources().openRawResource(
                    de.stegmann.kameravorleser.test.R.raw.test_opus_sample);
                 FileOutputStream out = new FileOutputStream(oggFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }

            File indexFile = new File(root, "index.jsonl");
            try (FileOutputStream out = new FileOutputStream(indexFile)) {
                String line = "{\"id\":\"dlg_0001\",\"speaker\":\"Erzähler\","
                        + "\"text\":\"Guten Morgen! Da hat aber jemand tief und fest geschlafen!\","
                        + "\"first_line\":\"Guten Morgen! Da hat aber jemand tief und fest\","
                        + "\"audio\":\"audio/dlg_0001.ogg\"}\n";
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }

            StoryAudioIndex index = StoryAudioIndex.load(root);
            assertEquals(1, index.size());
            StoryAudioIndex.Match early = index.matchEarlyPrefix(
                    "Erzähler", "Guten Morgen! Da hat aber jemand tief und fest", false);
            assertNotNull(early);
            assertTrue(early.earlyPrefix);
            StoryAudioIndex.Match full = index.matchFull(
                    "Erzähler", "Guten Morgen! Da hat aber jemand tief und fest geschlafen!", false);
            assertNotNull(full);

            long latencyMs = playLocallyAndAwait(target, full.audioFile, "Story-Pack · Test");
            Log.i("PokopiaTts", "DeviceTest Opus Startlatenz: " + latencyMs + " ms");
            assertTrue("Opus-Pack-Datei darf nach Wiedergabe nicht gelöscht werden", oggFile.isFile());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test(timeout = 15000)
    public void playsCachedWavFromPcmRecorderWithoutDeletingIt() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(target.getCacheDir(), "fastpath-wav-" + SystemClock.elapsedRealtime());
        assertTrue(root.mkdirs());
        try {
            SpeechAudioCache cache = new SpeechAudioCache(root, 1024L * 1024L);
            CloudSpeechRequest request = new CloudSpeechRequest(
                    "Lokaler Test für gespeicherten PCM-Stream.", "fish-test", "", "", 1.0f);
            SpeechAudioCache.PcmRecorder recorder = cache.openFishRecorder(request);
            assertNotNull(recorder);
            short[] silence = new short[CloudSpeechRequest.FISH_SAMPLE_RATE * 3 / 10];
            recorder.write(silence, silence.length);
            File wavFile = recorder.commit();
            assertNotNull(wavFile);
            assertEquals(wavFile, cache.lookup(request));

            long latencyMs = playLocallyAndAwait(target, wavFile, "Lokaler Cache · Test");
            Log.i("PokopiaTts", "DeviceTest WAV Startlatenz: " + latencyMs + " ms");
            assertTrue("Cache-WAV-Datei darf nach Wiedergabe nicht gelöscht werden", wavFile.isFile());
        } finally {
            deleteRecursively(root);
        }
    }

    private static long playLocallyAndAwait(Context context, File file, String sourceLabel) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean failed = new AtomicBoolean();
        AtomicBoolean partial = new AtomicBoolean();
        AtomicLong handedOffAt = new AtomicLong();
        AtomicLong startedAt = new AtomicLong(-1L);
        AtomicReference<CloudSpeechPlayer> playerRef = new AtomicReference<>();

        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                CloudSpeechPlayer player = new CloudSpeechPlayer(context, new CloudSpeechPlayer.Listener() {
                    @Override public void status(String id, String status) {
                        if (status.startsWith("Lese vor") && startedAt.get() < 0) {
                            startedAt.set(SystemClock.elapsedRealtime());
                        }
                    }

                    @Override public void finished(String id, boolean isFailed, boolean isPartial) {
                        failed.set(isFailed);
                        partial.set(isPartial);
                        finished.countDown();
                    }
                });
                playerRef.set(player);
                handedOffAt.set(SystemClock.elapsedRealtime());
                assertTrue("Lokale Wiedergabe muss angenommen werden: " + file.getName(),
                        player.speakLocalFile("test-1", file, sourceLabel, 0f));
            });

            assertTrue("Wiedergabe von " + file.getName() + " muss innerhalb von 10s abschließen",
                    finished.await(10, TimeUnit.SECONDS));
            assertFalse("Wiedergabe darf nicht fehlschlagen: " + file.getName(), failed.get());
            assertFalse("Wiedergabe darf nicht als teilgespielt enden", partial.get());
            assertTrue("Start-Status muss gemeldet worden sein", startedAt.get() >= handedOffAt.get());
            return startedAt.get() - handedOffAt.get();
        } finally {
            CloudSpeechPlayer player = playerRef.get();
            if (player != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(player::close);
            }
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        file.delete();
    }
}
