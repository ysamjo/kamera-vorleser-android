package de.stegmann.kameravorleser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class SpeechAudioCacheTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private static int readLeInt(byte[] buf, int offset) {
        return (buf[offset] & 0xff)
                | ((buf[offset + 1] & 0xff) << 8)
                | ((buf[offset + 2] & 0xff) << 16)
                | ((buf[offset + 3] & 0xff) << 24);
    }

    private static int readLeShort(byte[] buf, int offset) {
        return (buf[offset] & 0xff) | ((buf[offset + 1] & 0xff) << 8);
    }

    @Test
    public void keysAreDeterministicAndSeparatedByVoiceDirectionSpeedAndNormalizedText() {
        String a = SpeechAudioCache.keyFor("fish", "voice-a", "warm", 1.0f, "Hallo Welt!");
        String same = SpeechAudioCache.keyFor("fish", "voice-a", "warm", 1.0f, "  hallo   welt ");
        String diffVoice = SpeechAudioCache.keyFor("fish", "voice-b", "warm", 1.0f, "Hallo Welt!");
        String diffDir = SpeechAudioCache.keyFor("fish", "voice-a", "ruhig", 1.0f, "Hallo Welt!");
        String diffSpeed = SpeechAudioCache.keyFor("fish", "voice-a", "warm", 1.25f, "Hallo Welt!");

        assertEquals(64, a.length());
        assertEquals(a, same);
        assertNotEquals(a, diffVoice);
        assertNotEquals(a, diffDir);
        assertNotEquals(a, diffSpeed);
    }

    @Test
    public void putsGoogleMp3AndPrefersFishWavOnLookup() throws IOException {
        File dir = temp.newFolder("cache");
        SpeechAudioCache cache = new SpeechAudioCache(dir, 1024 * 1024);
        CloudSpeechRequest req = new CloudSpeechRequest(
                "Hallo Pokopia!", "fish-voice-1", "google-voice-1", "herzlich", 1.0f);

        assertNull(cache.lookup(req));

        byte[] mp3 = new byte[128];
        mp3[0] = 'I'; mp3[1] = 'D'; mp3[2] = '3';
        File storedMp3 = cache.putGoogleMp3(req, mp3);
        assertNotNull(storedMp3);
        assertTrue(storedMp3.getName().endsWith(".mp3"));
        assertEquals(storedMp3, cache.lookup(req));

        // Sobald auch Fish-WAV vorliegt, wird WAV bevorzugt
        SpeechAudioCache.PcmRecorder recorder = cache.openFishRecorder(req);
        assertNotNull(recorder);
        recorder.write(new short[]{100, -200, 300, -400}, 4);
        File storedWav = recorder.commit();
        assertNotNull(storedWav);
        assertTrue(storedWav.getName().endsWith(".wav"));
        assertEquals(storedWav, cache.lookup(req));
    }

    @Test
    public void pcmRecorderWritesValid24KhzMonoWavHeader() throws IOException {
        File dir = temp.newFolder("wav");
        SpeechAudioCache cache = new SpeechAudioCache(dir, 1024 * 1024);
        CloudSpeechRequest req = new CloudSpeechRequest(
                "Testaufnahme für Header", "fish-1", "", "", 1.0f);

        SpeechAudioCache.PcmRecorder recorder = cache.openFishRecorder(req);
        assertNotNull(recorder);
        short[] chunk1 = {10, 20, 30};
        short[] chunk2 = {-10, -20};
        recorder.write(chunk1, chunk1.length);
        recorder.write(chunk2, chunk2.length);
        File wav = recorder.commit();
        assertNotNull(wav);

        long expectedDataBytes = (chunk1.length + chunk2.length) * 2L;
        assertEquals(44L + expectedDataBytes, wav.length());

        byte[] header = new byte[44];
        try (FileInputStream in = new FileInputStream(wav)) {
            assertEquals(44, in.read(header));
        }
        assertEquals("RIFF", new String(header, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(36 + (int) expectedDataBytes, readLeInt(header, 4));
        assertEquals("WAVE", new String(header, 8, 4, StandardCharsets.US_ASCII));
        assertEquals("fmt ", new String(header, 12, 4, StandardCharsets.US_ASCII));
        assertEquals(16, readLeInt(header, 16));
        assertEquals(1, readLeShort(header, 20));
        assertEquals(1, readLeShort(header, 22));
        assertEquals(CloudSpeechRequest.FISH_SAMPLE_RATE, readLeInt(header, 24));
        assertEquals(CloudSpeechRequest.FISH_SAMPLE_RATE * 2, readLeInt(header, 28));
        assertEquals(2, readLeShort(header, 32));
        assertEquals(16, readLeShort(header, 34));
        assertEquals("data", new String(header, 36, 4, StandardCharsets.US_ASCII));
        assertEquals((int) expectedDataBytes, readLeInt(header, 40));
    }

    @Test
    public void abortedPcmRecorderCleansUpAndLeavesNoCachedAudio() throws IOException {
        File dir = temp.newFolder("abort");
        SpeechAudioCache cache = new SpeechAudioCache(dir, 1024 * 1024);
        CloudSpeechRequest req = new CloudSpeechRequest(
                "Abgebrochener Stream", "fish-1", "", "", 1.0f);

        try (SpeechAudioCache.PcmRecorder recorder = cache.openFishRecorder(req)) {
            assertNotNull(recorder);
            recorder.write(new short[]{1, 2, 3, 4}, 4);
            // Kein commit() -> close() verwirft die temporäre Datei
        }

        assertNull(cache.lookup(req));
        File[] remaining = dir.listFiles();
        assertNotNull(remaining);
        assertEquals(0, remaining.length);
    }

    @Test
    public void lruEvictionDeletesOldestFilesFirstWhenExceedingMaxBytes() throws IOException {
        File dir = temp.newFolder("lru");
        // Max 1024 Bytes -> Eviction räumt ab >1024 auf <= 870 Bytes (85%) auf
        SpeechAudioCache cache = new SpeechAudioCache(dir, 1024L);

        byte[] payload = new byte[400];
        CloudSpeechRequest r1 = new CloudSpeechRequest("Satz eins", "", "g1", "", 1.0f);
        CloudSpeechRequest r2 = new CloudSpeechRequest("Satz zwei", "", "g1", "", 1.0f);
        CloudSpeechRequest r3 = new CloudSpeechRequest("Satz drei", "", "g1", "", 1.0f);

        File f1 = cache.putGoogleMp3(r1, payload);
        File f2 = cache.putGoogleMp3(r2, payload);
        assertNotNull(f1);
        assertNotNull(f2);
        assertTrue(f1.setLastModified(1000L));
        assertTrue(f2.setLastModified(2000L));

        // 3 * 400 = 1200 > 1024 -> älteste Datei (f1) wird entfernt -> 800 <= 870
        File f3 = cache.putGoogleMp3(r3, payload);
        assertNotNull(f3);
        assertFalse(f1.exists());
        assertTrue(f2.exists());
        assertTrue(f3.exists());
    }
}
