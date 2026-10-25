package de.stegmann.kameravorleser;

/** Main-Thread-Zustand: Ein Sprecherwechsel nutzt nach Möglichkeit eine andere Grundstimme. */
final class VoiceSelection {
    private String previous = "";
    private int index = -1;

    int select(String speaker, int preferred, int count) {
        if (count < 1) return -1;
        String key = TextRules.normalize(speaker);
        if (key.equals(previous) && index >= 0 && index < count) return index;
        int next = Math.floorMod(preferred, count);
        if (!previous.isEmpty() && next == index && count > 1) next = (next + 1) % count;
        previous = key;
        index = next;
        return index;
    }

    void reset() { previous = ""; index = -1; }
}
