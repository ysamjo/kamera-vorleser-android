package de.stegmann.kameravorleser;

/** Zwei identische OCR-Befunde plus Ruhezeit; wachsende Wörter setzen die Uhr zurück. */
final class DialogueStability {
    static final long ANALYSIS_INTERVAL_MS = 120;
    private String key = "";
    private int hits;
    private long changedAt;
    private long seenAt;

    boolean accept(String text, long now) {
        String next = TextRules.normalize(text);
        if (next.isEmpty()) { reset(); return false; }
        if (!next.equals(key) || now - seenAt > 700) {
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

    void absent(long now) {
        if (now - seenAt > 700) reset();
    }

    void reset() { key = ""; hits = 0; changedAt = 0; seenAt = 0; }
}
