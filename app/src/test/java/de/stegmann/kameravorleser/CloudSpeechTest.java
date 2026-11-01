package de.stegmann.kameravorleser;

import org.junit.Test;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CloudSpeechTest {
    @Test public void fishIsPrimaryAndDoesNotSpendGoogleQuotaOnSuccess() throws Exception {
        AtomicInteger google = new AtomicInteger();
        CloudFallback.Audio audio = CloudFallback.synthesize(() -> new byte[]{1}, () -> {
            google.incrementAndGet(); return new byte[]{2};
        }, () -> false);
        assertEquals("Fish Audio", audio.provider);
        assertArrayEquals(new byte[]{1}, audio.bytes);
        assertEquals(0, google.get());
    }

    @Test public void errorsAndMissingFishUseGoogleExactlyOnce() throws Exception {
        AtomicInteger google = new AtomicInteger();
        CloudFallback.Provider backup = () -> { google.incrementAndGet(); return new byte[]{2}; };
        assertTrue(CloudFallback.synthesize(() -> { throw new IOException("HTTP 429"); }, backup,
                () -> false).provider.contains("Google Chirp"));
        assertEquals(1, google.get());
        CloudFallback.synthesize(null, backup, () -> false);
        assertEquals(2, google.get());
    }

    @Test public void stoppingDuringPrimaryFailureNeverSendsTextToBackup() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger google = new AtomicInteger();
        try {
            CloudFallback.synthesize(() -> { cancelled.set(true); throw new IOException("disconnect"); },
                    () -> { google.incrementAndGet(); return new byte[]{2}; }, cancelled::get);
            fail("Abbruch erwartet");
        } catch (InterruptedIOException expected) { assertEquals(0, google.get()); }
    }

    @Test public void latePrimarySuccessAfterStopIsDiscarded() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        try {
            CloudFallback.synthesize(() -> { cancelled.set(true); return new byte[]{1}; },
                    () -> { fail("Kein Backup nach Stop"); return null; }, cancelled::get);
            fail("Keine Ausgabe nach Stop");
        } catch (InterruptedIOException expected) { /* Abgebrochene Ausgabe darf nie onDone erzeugen. */ }
    }

    @Test public void failedBackupPropagatesAndNoProviderIsAConfigurationError() throws Exception {
        try {
            CloudFallback.synthesize(() -> { throw new IOException("fish"); },
                    () -> { throw new IOException("google"); }, () -> false);
            fail("Fehler erwartet");
        } catch (IOException expected) { assertEquals("google", expected.getMessage()); }
        try {
            CloudFallback.synthesize(null, null, () -> false);
            fail("Konfiguration fehlt");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("STIMME")); }
    }

    @Test public void requestsPreserveGermanAndEscapeJsonWithoutReadingFishDirectionsOnGoogle() {
        CloudSpeechRequest request = new CloudSpeechRequest("Scho\u0308n! \"Öl\"\\Äpfel\nGrüße", "voice-a",
                "de-DE-Chirp3-HD-Kore", "fröhlich", 1.1f);
        assertTrue(request.fishBody().contains("[fröhlich] Schön!"));
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
        VoiceAssignments assignments = new VoiceAssignments();
        java.util.List<String> pool = CloudVoiceProfiles.parse("fish-a,fish-b,fish-a", true);
        assertEquals("fish-a", assignments.choose("Pikachu", pool));
        assertEquals("fish-b", assignments.choose("Bisasam", pool));
        assertEquals("fish-a", assignments.choose("PIKACHU", pool));
        try { CloudVoiceProfiles.parse("en-US-Chirp3-HD-Kore", false); fail("Deutsch erzwingen"); }
        catch (IllegalArgumentException expected) { /* Kein falscher Sprachraum. */ }
    }
}
