package de.stegmann.kameravorleser;

/** Main-Thread-Zustand: Ein Sprecherwechsel nutzt nach Möglichkeit eine andere Grundstimme. */
final class VoiceSelection {
    private String previous = "";
    private int index = -1;

    /** Die manuell gewählte Stimme setzt die Mindestqualität, Automatik nimmt die beste. */
    int select(String speaker, int preferred, int[] qualities) {
        if (qualities.length == 0) return -1;
        int base = Math.floorMod(preferred, qualities.length);
        java.util.List<Integer> eligible = new java.util.ArrayList<>();
        for (int i = 0; i < qualities.length; i++) {
            if (qualities[i] >= qualities[base]) eligible.add(i);
        }
        int local = select(speaker, eligible.indexOf(base), eligible.size());
        return eligible.get(local);
    }

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
