package de.stegmann.kameravorleser;

import org.junit.Test;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CloudSpeechTest {
    @Test public void fishIsPrimaryAndDoesNotSpendGoogleQuotaOnSuccess() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        byte[] backup = CloudFallback.playOrSynthesize(() -> started.set(true), () -> {
            fail("Google nur als Backup");
            return null;
        }, started::get, () -> false);
        assertTrue(started.get());
        assertNull(backup);
    }

    @Test public void errorsBeforePlaybackAndMissingFishUseGoogleExactlyOnce() throws Exception {
        AtomicInteger google = new AtomicInteger();
        CloudFallback.Provider backup = () -> { google.incrementAndGet(); return new byte[]{2}; };
        assertArrayEquals(new byte[]{2}, CloudFallback.playOrSynthesize(
                () -> { throw new IOException("HTTP 429"); }, backup, () -> false, () -> false));
        assertEquals(1, google.get());
        CloudFallback.playOrSynthesize(null, backup, () -> false, () -> false);
        assertEquals(2, google.get());
    }

    @Test public void playbackFailureAfterStartNeverSendsTheWholeDialogueToBackup() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        IOException brokenStream = new IOException("PCM-Verbindung unterbrochen");
        try {
            CloudFallback.playOrSynthesize(() -> {
                started.set(true);
                throw brokenStream;
            }, () -> { fail("Bereits gestarteten Dialog nicht doppelt lesen"); return null; },
                    started::get, () -> false);
            fail("Teilausgabe darf keinen erfolgreichen Abschluss melden");
        } catch (IOException expected) { assertSame(brokenStream, expected); }
    }

    @Test public void stoppingDuringPrimaryFailureNeverSendsTextToBackup() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger google = new AtomicInteger();
        try {
            CloudFallback.playOrSynthesize(() -> {
                cancelled.set(true);
                throw new IOException("disconnect");
            }, () -> { google.incrementAndGet(); return new byte[]{2}; }, () -> false, cancelled::get);
            fail("Abbruch erwartet");
        } catch (InterruptedIOException expected) { assertEquals(0, google.get()); }
    }

    @Test public void latePrimarySuccessAfterStopIsDiscarded() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        try {
            CloudFallback.playOrSynthesize(() -> cancelled.set(true),
                    () -> { fail("Kein Backup nach Stop"); return null; }, () -> true, cancelled::get);
            fail("Keine Ausgabe nach Stop");
        } catch (InterruptedIOException expected) { /* Abgebrochene Ausgabe darf nie onDone erzeugen. */ }
    }

    @Test public void failedBackupPropagatesAndNoProviderIsAConfigurationError() throws Exception {
        try {
            CloudFallback.playOrSynthesize(() -> { throw new IOException("fish"); },
                    () -> { throw new IOException("google"); }, () -> false, () -> false);
            fail("Fehler erwartet");
        } catch (IOException expected) { assertEquals("google", expected.getMessage()); }
        try {
            CloudFallback.playOrSynthesize(null, null, () -> false, () -> false);
            fail("Konfiguration fehlt");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("STIMME")); }
    }

    @Test public void requestsPreserveGermanAndEscapeJsonWithoutReadingFishDirectionsOnGoogle() {
        CloudSpeechRequest request = new CloudSpeechRequest("Scho\u0308n! \"Öl\"\\Äpfel\nGrüße", "voice-a",
                "de-DE-Chirp3-HD-Kore", "fröhlich", 1.1f);
        assertTrue(request.fishBody().contains("[fröhlich] Schön!"));
        assertTrue(request.fishBody().contains("\"format\":\"pcm\""));
        assertTrue(request.fishBody().contains("\"sample_rate\":24000"));
        assertTrue(request.fishBody().contains("\"latency\":\"balanced\""));
        assertTrue(request.fishBody().contains("\"chunk_length\":150"));
        assertTrue(request.fishBody().contains("\\\"Öl\\\""));
        assertTrue(request.fishBody().contains("\\\\Äpfel\\nGrüße"));
        assertTrue(request.googleBody().contains("Schön!"));
        assertFalse(request.googleBody().contains("fröhlich"));
        assertFalse(request.googleBody().contains("pitch"));
        assertTrue(request.googleBody().contains("\"languageCode\":\"de-DE\""));
        assertTrue(request.googleBody().contains("Chirp3-HD-Kore"));
    }

    @Test public void googleByteLimitDoesNotSilentlyTruncateGerman() {
        new CloudSpeechRequest("ä".repeat(2500), "a", "g", "", 1f);
        try {
            new CloudSpeechRequest("ä".repeat(2501), "a", "g", "", 1f);
            fail("UTF-8-Limit muss vor der Übertragung greifen");
        } catch (IllegalArgumentException expected) { /* Umlaute benötigen zwei Bytes. */ }
    }

    @Test public void voicePoolsRemainGermanAndNamesKeepStableDistinctAssignments() {
        assertEquals(4, CloudVoiceProfiles.parse(CloudVoiceProfiles.GOOGLE_DEFAULTS, false).size());
        assertEquals(15, CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_DEFAULTS, true).size());
        VoiceAssignments assignments = new VoiceAssignments();
        java.util.List<String> pool = CloudVoiceProfiles.parse("fish-a,fish-b,fish-a", true);
        assertEquals("fish-a", assignments.choose("Pikachu", pool));
        assertEquals("fish-b", assignments.choose("Bisasam", pool));
        assertEquals("fish-a", assignments.choose("PIKACHU", pool));
        try { CloudVoiceProfiles.parse("en-US-Chirp3-HD-Kore", false); fail("Deutsch erzwingen"); }
        catch (IllegalArgumentException expected) { /* Kein falscher Sprachraum. */ }
    }

    @Test public void pokopiaCharacterVoicesMapToPreferredSlotsWithoutCollisions() {
        java.util.List<String> fiveVoices = CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS, true);
        assertEquals(5, fiveVoices.size());
        VoiceAssignments assignments = new VoiceAssignments();
        assertEquals(fiveVoices.get(0), assignments.choose("Erzähler", fiveVoices, 0));
        assertEquals(fiveVoices.get(1), assignments.choose("Schlappchu", fiveVoices, 1));
        assertEquals(fiveVoices.get(2), assignments.choose("Evoli", fiveVoices, 2));
        assertEquals(fiveVoices.get(3), assignments.choose("Professor Tangoloss", fiveVoices, 3));
        assertEquals(fiveVoices.get(4), assignments.choose("Scherox", fiveVoices, 4));

        // Gleiche Figur behält ihre Stimme stabil
        assertEquals(fiveVoices.get(1), assignments.choose("SCHLAPPCHU", fiveVoices, 1));
        assertEquals(fiveVoices.get(0), assignments.choose("erzähler", fiveVoices, 0));

        // Weitere Figur mit belegtem Wunsch-Slot weicht auf noch freie oder seltenste Stimme aus
        String extra = assignments.choose("Machollo", fiveVoices, 4);
        assertNotNull(extra);
        assertTrue(fiveVoices.contains(extra));
    }
}
