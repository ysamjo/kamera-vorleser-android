package de.stegmann.kameravorleser;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Entfernt ausschließlich das zur Dialogbox gehörende Namensschild. */
final class DialogueText {
    private DialogueText() {}

    /** Wortgrenzen erhalten Umlaute und verhindern Treffer mitten in Wörtern. */
    static String unreadAfter(String full, String previous) {
        String visible = Normalizer.normalize(full, Normalizer.Form.NFC);
        String normalized = TextRules.normalize(visible);
        String heard = TextRules.normalize(previous);
        if (normalized.isEmpty()) return "";
        if (heard.isEmpty()) return visible;
        if (normalized.equals(heard)) return "";
        String[] words = normalized.split(" ");
        int covered = 0;
        if (normalized.startsWith(heard + " ")) {
            covered = heard.split(" ").length;
        } else {
            // KMP: längste Wortüberlappung ohne quadratische Teilstring-Erzeugung.
            int[] fallback = new int[words.length];
            for (int i = 1, matched = 0; i < words.length; i++) {
                while (matched > 0 && !words[i].equals(words[matched])) matched = fallback[matched - 1];
                if (words[i].equals(words[matched])) matched++;
                fallback[i] = matched;
            }
            int matched = 0;
            for (String word : heard.split(" ")) {
                while (matched > 0 && (matched == words.length || !word.equals(words[matched]))) {
                    matched = fallback[matched - 1];
                }
                if (word.equals(words[matched])) matched++;
                if (matched == words.length && matched >= 3) return "";
            }
            // Einzelne häufige Wörter sind kein Beleg für ein Scrollfenster.
            if (matched >= 3) covered = matched;
        }
        if (covered == 0) return visible;
        if (covered == words.length) return "";
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(visible);
        for (int i = 0; i < covered; i++) if (!matcher.find()) return visible;
        return visible.substring(matcher.end()).replaceFirst("^[\\s.,!?…:;]+", "").trim();
    }

    static boolean sameDialogue(String previous, String current) {
        String left = TextRules.normalize(previous);
        String right = TextRules.normalize(current);
        return !left.isEmpty() && (left.equals(right) || TextRules.similarStoryText(left, right)
                || right.startsWith(left + " ") || left.startsWith(right + " ")
                || containsWords(left, right) || overlapWords(left, right) >= 3);
    }

    static boolean containsWords(String full, String fragment) {
        return !fragment.isEmpty() && (" " + full + " ").contains(" " + fragment + " ");
    }

    /** Nur exakte Wortfolgen: kurze gemeinsame Wörter sind kein Scroll-Beleg. */
    static int overlapWords(String previous, String current) {
        if (previous.isEmpty() || current.isEmpty()) return 0;
        String[] left = previous.split(" ");
        String[] right = current.split(" ");
        for (int size = Math.min(left.length, right.length); size > 0; size--) {
            boolean matches = true;
            for (int i = 0; i < size; i++) {
                if (!left[left.length - size + i].equals(right[i])) { matches = false; break; }
            }
            if (matches) return size;
        }
        return 0;
    }

    static String afterWords(String visible, int words) {
        String text = Normalizer.normalize(visible, Normalizer.Form.NFC);
        if (words == 0) return text;
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(text);
        for (int i = 0; i < words; i++) if (!matcher.find()) return text;
        return text.substring(matcher.end()).replaceFirst("^[\\s.,!?…:;]+", "").trim();
    }

    static String extensionSuffix(String full, String prefix) {
        String visible = Normalizer.normalize(full, Normalizer.Form.NFC);
        String normalizedPrefix = TextRules.normalize(prefix);
        if (normalizedPrefix.isEmpty()
                || !TextRules.normalize(visible).startsWith(normalizedPrefix + " ")) return visible;
        int words = normalizedPrefix.split(" ").length;
        Matcher matcher = Pattern.compile("[\\p{L}\\p{N}]+").matcher(visible);
        for (int i = 0; i < words; i++) if (!matcher.find()) return visible;
        String suffix = visible.substring(matcher.end()).replaceFirst("^[\\s.,!?…:;]+", "").trim();
        return suffix.isEmpty() ? visible : suffix;
    }

    static String withoutSpeaker(String text, String speaker) {
        if (text == null) return "";
        String visible = Normalizer.normalize(text, Normalizer.Form.NFC).strip();
        if (speaker == null || speaker.isBlank()
                || TextRules.normalize(speaker).equals(TextRules.normalize("Erzähler"))) {
            return visible;
        }
        String label = Normalizer.normalize(speaker, Normalizer.Form.NFC).strip();
        if (!visible.regionMatches(true, 0, label, 0, Math.min(label.length(), visible.length()))
                || visible.length() < label.length()) return visible;
        if (visible.length() == label.length()) return "";
        char separator = visible.charAt(label.length());
        if (!Character.isWhitespace(separator) && ":：–—-".indexOf(separator) < 0) {
            return visible;
        }
        String remaining = visible.substring(label.length()).replaceFirst("^[\\s:：–—-]+", "").trim();
        return remaining;
    }
}
