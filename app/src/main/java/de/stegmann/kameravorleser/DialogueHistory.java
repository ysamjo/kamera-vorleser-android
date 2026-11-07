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
    private final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private String repeatKey;
    private final int maxEntries;

    DialogueHistory(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries");
        this.maxEntries = maxEntries;
    }

    static String key(String speaker, String text) {
        return TextRules.normalize(speaker) + SEPARATOR + TextRules.normalize(text);
    }

    boolean wasSpoken(String speaker, String text) {
        String candidate = key(speaker, text);
        if (TextRules.normalize(text).isEmpty()) return false;
        for (String entry : pending) if (matches(entry, candidate)) return true;
        if (repeatKey != null && matches(repeatKey, candidate)) return false;
        for (String entry : entries) if (matches(entry, candidate)) return true;
        return false;
    }

    static boolean matches(String entry, String candidate) {
        int delimiter = entry.indexOf(SEPARATOR);
        int other = candidate.indexOf(SEPARATOR);
        if (delimiter < 0 || other < 0
                || !entry.substring(0, delimiter).equals(candidate.substring(0, other))) return false;
        return matchesText(entry.substring(delimiter + 1), candidate.substring(other + 1));
    }

    private static boolean matchesText(String previous, String text) {
        return !text.isEmpty() && (previous.equals(text)
                || (!text.startsWith(previous + " ") && text.split(" ").length == previous.split(" ").length
                    && TextRules.similarEnough(text, previous))
                || DialogueText.containsWords(previous, text)
                || DialogueText.heardPrefixWords(previous, text) == text.split(" ").length);
    }

    private static String textPart(String entry) {
        int delimiter = entry.indexOf(SEPARATOR);
        return delimiter < 0 ? "" : entry.substring(delimiter + 1);
    }

    /**
     * Gleicher Text unabhängig vom Sprecher. Wird nur verwendet, solange das
     * Namensschild fehlt: Ein erneutes Vorlesen derselben Zeile wäre sonst ein
     * Fehler, weil der Sprecher nicht sicher bestimmt werden kann.
     */
    boolean wasSpokenAnywhere(String text) {
        String normalized = TextRules.normalize(text);
        if (normalized.isEmpty()) return false;
        for (String entry : pending) if (matchesText(textPart(entry), normalized)) return true;
        if (repeatKey != null && matchesText(textPart(repeatKey), normalized)) return false;
        for (String entry : entries) if (matchesText(textPart(entry), normalized)) return true;
        return false;
    }

    /** Schneidet nur bereits vollständig gehörten Text ab; Neu lesen bleibt vollständig. */
    String unreadText(String speaker, String visible) {
        String candidate = key(speaker, visible);
        if (repeatKey != null && matches(repeatKey, candidate)) return visible;
        String prefix = TextRules.normalize(speaker) + SEPARATOR;
        String unread = visible;
        // Mehrere abgeschlossene Fenster können nacheinander denselben Anfang
        // abdecken. Jeder Durchlauf entfernt Wörter, also bleibt die Schleife endlich.
        while (!TextRules.normalize(unread).isEmpty()) {
            String text = TextRules.normalize(unread);
            int heardWords = 0;
            for (String entry : entries) {
                if (!entry.startsWith(prefix)) continue;
                String previous = entry.substring(prefix.length());
                if (matches(entry, key(speaker, unread))) return "";
                int overlap = DialogueText.heardPrefixWords(previous, text);
                if (text.startsWith(previous + " ")) {
                    overlap = Math.max(overlap, previous.split(" ").length);
                }
                heardWords = Math.max(heardWords, overlap);
            }
            if (heardWords == 0) return unread;
            unread = DialogueText.afterWords(unread, heardWords);
        }
        return "";
    }

    boolean awaitsOverlappingSpeech(String speaker, String text) {
        String prefix = TextRules.normalize(speaker) + SEPARATOR;
        String normalized = TextRules.normalize(text);
        for (String entry : pending) {
            if (!entry.startsWith(prefix)) continue;
            String previous = entry.substring(prefix.length());
            if (normalized.startsWith(previous + " ")
                    || DialogueText.heardPrefixWords(previous, normalized) >= 3) return true;
        }
        return false;
    }

    /** Wie {@link #awaitsOverlappingSpeech}, aber ohne sicheren Sprecher. */
    boolean awaitsOverlappingSpeechAnywhere(String text) {
        String normalized = TextRules.normalize(text);
        if (normalized.isEmpty()) return false;
        for (String entry : pending) {
            String previous = textPart(entry);
            if (normalized.startsWith(previous + " ")
                    || DialogueText.heardPrefixWords(previous, normalized) >= 3) return true;
        }
        return false;
    }

    /** Wie {@link #unreadText}, aber über alle gespeicherten Sprecher hinweg. */
    String unreadTextAnywhere(String visible) {
        if (repeatKey != null && matchesText(textPart(repeatKey), TextRules.normalize(visible))) return visible;
        String unread = visible;
        while (!TextRules.normalize(unread).isEmpty()) {
            String text = TextRules.normalize(unread);
            int heardWords = 0;
            for (String entry : entries) {
                String previous = textPart(entry);
                if (matchesText(previous, text)) return "";
                int overlap = DialogueText.heardPrefixWords(previous, text);
                if (text.startsWith(previous + " ")) {
                    overlap = Math.max(overlap, previous.split(" ").length);
                }
                heardWords = Math.max(heardWords, overlap);
            }
            if (heardWords == 0) return unread;
            unread = DialogueText.afterWords(unread, heardWords);
        }
        return "";
    }

    void allowRepeat(String speaker, String text) {
        repeatKey = TextRules.normalize(text).isEmpty() ? null : key(speaker, text);
    }

    void begin(String key) { pending.add(key); }

    boolean finish(String key, boolean completed) {
        if (!pending.remove(key) || !completed) return false;
        int delimiter = key.indexOf(SEPARATOR);
        if (delimiter < 0) return false;
        remember(key.substring(0, delimiter), key.substring(delimiter + 1));
        if (repeatKey != null && matches(repeatKey, key)) repeatKey = null;
        return true;
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
        pending.clear();
        repeatKey = null;
        if (keys == null) return;
        for (String key : keys) {
            if (key == null || key.length() > 8192 || key.indexOf(SEPARATOR) < 0) continue;
            entries.remove(key);
            entries.add(key);
            while (entries.size() > maxEntries) entries.remove(entries.iterator().next());
        }
    }
}

