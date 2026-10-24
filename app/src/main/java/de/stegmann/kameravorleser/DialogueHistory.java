package de.stegmann.kameravorleser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Sitzungshistorie der Dialoge, unabhängig von der Kamera und Android.
 * Der Schlüssel enthält den Sprecher: Derselbe Satz eines anderen Pokémon
 * ist ein neuer Dialog, aber OCR-Wiederholungen desselben Sprechers sind es nicht.
 */
final class DialogueHistory {
    private static final String SEPARATOR = "\u001f";
    private final LinkedHashSet<String> entries = new LinkedHashSet<>();
    private final int maxEntries;

    DialogueHistory(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries");
        this.maxEntries = maxEntries;
    }

    static String key(String speaker, String text) {
        return TextRules.normalize(speaker) + SEPARATOR + TextRules.normalize(text);
    }

    boolean wasSpoken(String speaker, String text) {
        String speakerKey = TextRules.normalize(speaker);
        String textKey = TextRules.normalize(text);
        if (textKey.isEmpty()) return false;
        if (entries.contains(key(speaker, text))) return true;
        for (String entry : entries) {
            int delimiter = entry.indexOf(SEPARATOR);
            if (delimiter < 0 || !entry.substring(0, delimiter).equals(speakerKey)) continue;
            String previous = entry.substring(delimiter + 1);
            if (TextRules.similarEnough(textKey, previous)
                    || (previous.startsWith(textKey) && previous.length() > textKey.length())) {
                return true;
            }
        }
        return false;
    }

    void remember(String speaker, String text) {
        String normalized = TextRules.normalize(text);
        if (normalized.isEmpty()) return;
        String dialogueKey = key(speaker, normalized);
        entries.remove(dialogueKey);
        entries.add(dialogueKey);
        while (entries.size() > maxEntries) entries.remove(entries.iterator().next());
    }

    boolean forget(String speaker, String text) {
        return entries.remove(key(speaker, text));
    }

    boolean forgetKey(String dialogueKey) {
        return entries.remove(dialogueKey);
    }

    void clear() { entries.clear(); }

    List<String> snapshot() { return new ArrayList<>(entries); }

    void restore(Collection<String> keys) {
        entries.clear();
        if (keys == null) return;
        for (String key : keys) {
            if (key == null || key.length() > 1200 || key.indexOf(SEPARATOR) < 0) continue;
            entries.remove(key);
            entries.add(key);
            while (entries.size() > maxEntries) entries.remove(entries.iterator().next());
        }
    }
}
