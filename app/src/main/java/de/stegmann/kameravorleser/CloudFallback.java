package de.stegmann.kameravorleser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Reihenfolge Fish -> Google. Abbruch ist kein Anbieterfehler und löst kein Backup aus. */
final class CloudFallback {
    interface Provider { byte[] synthesize() throws IOException; }
    static final class Audio {
        final byte[] bytes;
        final String provider;
        Audio(byte[] bytes, String provider) { this.bytes = bytes; this.provider = provider; }
    }

    static Audio synthesize(Provider fish, Provider google, BooleanSupplier cancelled) throws IOException {
        check(cancelled);
        if (fish != null) {
            try {
                byte[] audio = fish.synthesize();
                check(cancelled);
                return new Audio(audio, "Fish Audio");
            } catch (IOException error) {
                check(cancelled);
                if (google == null) throw error;
            }
        }
        check(cancelled);
        if (google == null) throw new IOException("Fish Audio / Google-Zugang unter STIMME einrichten");
        byte[] audio = google.synthesize();
        check(cancelled);
        return new Audio(audio, "Google Chirp · Backup");
    }

    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Abgebrochen");
        }
    }
}
