package de.stegmann.kameravorleser;

/** Zwei identische OCR-Befunde plus Ruhezeit; wachsende Wörter setzen die Uhr zurück. */
final class DialogueStability {
    static final long ANALYSIS_INTERVAL_MS = 120;
    private static final long ABSENCE_MS = 700;
    private static final long MAX_FRAME_GAP_MS = 2000;
    private String key = "";
    private int hits;
    private long changedAt;
    private long seenAt;

    boolean accept(String text, long now) {
        String next = TextRules.normalize(text);
        if (next.isEmpty()) { reset(); return false; }
        if (!next.equals(key) || now - seenAt > MAX_FRAME_GAP_MS) {
            key = next;
            hits = 1;
            changedAt = now;
        } else {
            hits++;
        }
        seenAt = now;
        boolean sentenceEnd = text.trim().matches("(?s).*[.!?…][\\\"'»“”)]*$");
        return hits >= 2 && now - changedAt >= (sentenceEnd ? 120 : 420);
    }

    boolean absent(long now) {
        if (now - seenAt <= ABSENCE_MS) return false;
        reset();
        return true;
    }

    void reset() { key = ""; hits = 0; changedAt = 0; seenAt = 0; }
}
