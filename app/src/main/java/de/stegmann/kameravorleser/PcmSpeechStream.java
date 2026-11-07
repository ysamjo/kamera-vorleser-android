package de.stegmann.kameravorleser;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

/** Entkoppelt HTTP-Blockgrenzen von den 16-Bit-Samples und hält nur einen Audioblock im Speicher. */
final class PcmSpeechStream {
    // 24-kHz-PCM braucht ungefähr dreimal so viele Bytes wie MP3 mit 128 kbit/s.
    static final int MAX_BYTES = 48 * 1024 * 1024;
    static final int START_BYTES = CloudSpeechRequest.FISH_SAMPLE_RATE * 2 * 80 / 1000;
    private static final int BLOCK_BYTES = 4096;

    interface Sink { void write(short[] samples, int count) throws IOException; }

    static void transfer(InputStream input, int contentLength, Sink sink, BooleanSupplier cancelled)
            throws IOException {
        if (contentLength > MAX_BYTES || (contentLength >= 0 && (contentLength & 1) != 0)) {
            throw new IOException("Ungültige PCM-Länge");
        }
        byte[] bytes = new byte[BLOCK_BYTES];
        short[] samples = new short[BLOCK_BYTES / 2];
        int total = 0;
        int buffered = 0;
        boolean primed = false;
        while (true) {
            CloudFallback.check(cancelled);
            int read = input.read(bytes, buffered, bytes.length - buffered);
            CloudFallback.check(cancelled);
            boolean end = read < 0;
            if (end) {
                if (total == 0 || (buffered & 1) != 0 || (contentLength >= 0 && total != contentLength)) {
                    throw new IOException("Unvollständige PCM-Antwort");
                }
            } else {
                if (read == 0) throw new IOException("Leerer PCM-Block");
                total += read;
                if (total > MAX_BYTES || (contentLength >= 0 && total > contentLength)) {
                    throw new IOException("Audioantwort zu groß");
                }
                buffered += read;
                if (!primed && buffered < START_BYTES) continue;
            }
            if (buffered >= 2) {
                if (!primed) validatePrefix(bytes, buffered);
                int count = buffered / 2;
                for (int i = 0; i < count; i++) {
                    samples[i] = (short) ((bytes[2 * i] & 0xff) | (bytes[2 * i + 1] << 8));
                }
                sink.write(samples, count);
                primed = true;
            }
            if (end) return;
            if ((buffered & 1) != 0) bytes[0] = bytes[buffered - 1];
            buffered &= 1;
        }
    }

    private static void validatePrefix(byte[] bytes, int count) throws IOException {
        // Manche Gateways kennzeichnen jeden Inhalt als application/octet-stream.
        boolean wav = count >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'A'
                && bytes[10] == 'V' && bytes[11] == 'E';
        boolean mp3 = count >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
        if (wav || mp3) throw new IOException("Fish Audio: PCM-Antwort erwartet");
    }
}
