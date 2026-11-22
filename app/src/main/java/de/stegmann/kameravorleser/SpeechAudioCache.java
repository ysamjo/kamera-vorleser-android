package de.stegmann.kameravorleser;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Selbstlernender lokaler Audio-Cache für Fish-PCM (.wav) und Google-Backup (.mp3).
 * Speichert erst nach vollständigem Download/Stream und bereinigt per LRU ab maxBytes.
 */
final class SpeechAudioCache {
    static final long DEFAULT_MAX_BYTES = 150L * 1024L * 1024L;
    private static final int WAV_HEADER_BYTES = 44;

    private final File directory;
    private final long maxBytes;

    SpeechAudioCache(File directory, long maxBytes) {
        this.directory = directory;
        this.maxBytes = Math.max(1024L, maxBytes);
    }

    static String keyFor(String provider, String voice, String direction, float speed, String text) {
        String safeProvider = provider == null ? "" : provider;
        String safeVoice = voice == null ? "" : voice;
        String safeDirection = direction == null ? "" : direction;
        String normalizedText = TextRules.normalize(text == null ? "" : text);
        String payload = safeProvider + "|" + safeVoice + "|" + safeDirection + "|"
                + String.format(Locale.ROOT, "%.2f", speed) + "|" + normalizedText;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", impossible);
        }
    }

    synchronized File lookup(CloudSpeechRequest request) {
        if (request == null || directory == null || !directory.isDirectory()) return null;
        if (!request.fishVoice.isEmpty()) {
            File wav = new File(directory, keyFor("fish", request.fishVoice,
                    request.direction, request.speed, request.text) + ".wav");
            if (wav.isFile() && wav.length() > WAV_HEADER_BYTES) {
                wav.setLastModified(System.currentTimeMillis());
                return wav;
            }
        }
        if (!request.googleVoice.isEmpty()) {
            File mp3 = new File(directory, keyFor("google", request.googleVoice,
                    "", request.speed, request.text) + ".mp3");
            if (mp3.isFile() && mp3.length() > WAV_HEADER_BYTES) {
                mp3.setLastModified(System.currentTimeMillis());
                return mp3;
            }
        }
        return null;
    }

    synchronized File putGoogleMp3(CloudSpeechRequest request, byte[] mp3Bytes) {
        if (request == null || request.googleVoice.isEmpty() || mp3Bytes == null
                || mp3Bytes.length <= WAV_HEADER_BYTES || directory == null) {
            return null;
        }
        if (!ensureDirectory()) return null;
        String key = keyFor("google", request.googleVoice, "", request.speed, request.text);
        File target = new File(directory, key + ".mp3");
        File temp = null;
        try {
            temp = File.createTempFile("tmp-g-", ".mp3.part", directory);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(mp3Bytes);
            }
            if (!replaceFile(temp, target)) {
                temp.delete();
                return null;
            }
            pruneIfNeeded();
            return target;
        } catch (IOException ignored) {
            if (temp != null) temp.delete();
            return null;
        }
    }

    PcmRecorder openFishRecorder(CloudSpeechRequest request) {
        if (request == null || request.fishVoice.isEmpty() || directory == null) return null;
        if (!ensureDirectory()) return null;
        String key = keyFor("fish", request.fishVoice, request.direction, request.speed, request.text);
        File target = new File(directory, key + ".wav");
        File temp = null;
        RandomAccessFile raf = null;
        try {
            temp = File.createTempFile("tmp-f-", ".wav.part", directory);
            raf = new RandomAccessFile(temp, "rw");
            raf.write(wavHeader(CloudSpeechRequest.FISH_SAMPLE_RATE, 1, 16, 0L));
            return new PcmRecorder(this, temp, target, raf);
        } catch (IOException ignored) {
            if (raf != null) {
                try { raf.close(); } catch (IOException closeIgnored) { /* Aufräumen */ }
            }
            if (temp != null) temp.delete();
            return null;
        }
    }

    synchronized File finishRecorderCommit(File temp, File target) {
        if (!replaceFile(temp, target)) {
            temp.delete();
            return null;
        }
        pruneIfNeeded();
        return target;
    }

    synchronized void pruneIfNeeded() {
        if (directory == null || !directory.isDirectory()) return;
        File[] raw = directory.listFiles();
        if (raw == null || raw.length == 0) return;
        List<File> files = new ArrayList<>();
        long total = 0L;
        for (File file : raw) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (!name.endsWith(".wav") && !name.endsWith(".mp3")) continue;
            files.add(file);
            total += file.length();
        }
        if (total <= maxBytes) return;
        files.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        long targetBytes = (long) (maxBytes * 0.85);
        for (File file : files) {
            if (total <= targetBytes) break;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }

    private boolean ensureDirectory() {
        return directory.isDirectory() || directory.mkdirs();
    }

    private static boolean replaceFile(File temp, File target) {
        if (target.exists() && !target.delete()) return false;
        if (temp.renameTo(target)) return true;
        return false;
    }

    static byte[] wavHeader(int sampleRate, int channels, int bitsPerSample, long dataBytes) {
        long clampedData = Math.max(0L, Math.min(0xFFFFFFFFL - 36L, dataBytes));
        int blockAlign = channels * (bitsPerSample / 8);
        int byteRate = sampleRate * blockAlign;
        int riffSize = (int) (36L + clampedData);
        int dataSize = (int) clampedData;

        byte[] header = new byte[WAV_HEADER_BYTES];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        writeLeInt(header, 4, riffSize);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        writeLeInt(header, 16, 16);
        writeLeShort(header, 20, 1);
        writeLeShort(header, 22, channels);
        writeLeInt(header, 24, sampleRate);
        writeLeInt(header, 28, byteRate);
        writeLeShort(header, 32, blockAlign);
        writeLeShort(header, 34, bitsPerSample);
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        writeLeInt(header, 40, dataSize);
        return header;
    }

    private static void writeLeInt(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xff);
        buf[offset + 1] = (byte) ((value >> 8) & 0xff);
        buf[offset + 2] = (byte) ((value >> 16) & 0xff);
        buf[offset + 3] = (byte) ((value >> 24) & 0xff);
    }

    private static void writeLeShort(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xff);
        buf[offset + 1] = (byte) ((value >> 8) & 0xff);
    }

    /** Best-Effort-PCM-Mitschnitt beim Fish-Streaming ohne Auswirkung auf die Live-Wiedergabe. */
    static final class PcmRecorder implements Closeable {
        private final SpeechAudioCache cache;
        private final File temp;
        private final File target;
        private final RandomAccessFile raf;
        private byte[] scratch = new byte[4096];
        private long dataBytes;
        private boolean failed;
        private boolean closed;
        private boolean committed;

        PcmRecorder(SpeechAudioCache cache, File temp, File target, RandomAccessFile raf) {
            this.cache = cache;
            this.temp = temp;
            this.target = target;
            this.raf = raf;
        }

        void write(short[] samples, int count) {
            if (closed || failed || samples == null || count <= 0) return;
            int needed = count * 2;
            if (scratch.length < needed) scratch = new byte[needed];
            for (int i = 0; i < count; i++) {
                short s = samples[i];
                scratch[2 * i] = (byte) (s & 0xff);
                scratch[2 * i + 1] = (byte) ((s >> 8) & 0xff);
            }
            try {
                raf.write(scratch, 0, needed);
                dataBytes += needed;
            } catch (IOException ignored) {
                abort();
            }
        }

        File commit() {
            if (closed || failed || dataBytes <= 0L) {
                abort();
                return null;
            }
            try {
                raf.seek(0L);
                raf.write(wavHeader(CloudSpeechRequest.FISH_SAMPLE_RATE, 1, 16, dataBytes));
                raf.close();
                closed = true;
                committed = true;
                return cache.finishRecorderCommit(temp, target);
            } catch (IOException ignored) {
                abort();
                return null;
            }
        }

        void abort() {
            failed = true;
            close();
        }

        @Override public void close() {
            if (!closed) {
                closed = true;
                try {
                    raf.close();
                } catch (IOException ignored) { /* Aufräumen */ }
            }
            if (!committed) temp.delete();
        }
    }
}
