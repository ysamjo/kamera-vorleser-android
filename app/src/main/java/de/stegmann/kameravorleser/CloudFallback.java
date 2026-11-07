package de.stegmann.kameravorleser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Ein einmal hörbar gestarteter Fish-Dialog wird bei einem Fehler nicht erneut gelesen. */
final class CloudFallback {
    interface Playback { void play() throws IOException; }
    interface Provider { byte[] synthesize() throws IOException; }

    /** Liefert Google-Audio oder null, wenn Fish bereits vollständig abgespielt wurde. */
    static byte[] playOrSynthesize(Playback fish, Provider google, BooleanSupplier fishStarted,
            BooleanSupplier cancelled) throws IOException {
        check(cancelled);
        if (fish != null) {
            try {
                fish.play();
                check(cancelled);
                return null;
            } catch (IOException error) {
                check(cancelled);
                if (fishStarted.getAsBoolean() || google == null) throw error;
            }
        }
        check(cancelled);
        if (google == null) throw new IOException("Fish Audio / Google-Zugang unter STIMME einrichten");
        byte[] audio = google.synthesize();
        check(cancelled);
        return audio;
    }

    static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Abgebrochen");
        }
    }
}
