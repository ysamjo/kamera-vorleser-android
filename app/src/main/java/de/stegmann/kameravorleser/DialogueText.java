package de.stegmann.kameravorleser;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Entfernt ausschließlich das zur Dialogbox gehörende Namensschild. */
final class DialogueText {
    private DialogueText() {}

    static boolean sameDialogue(String previous, String current) {
        String left = TextRules.normalize(previous);
        String right = TextRules.normalize(current);
        return !left.isEmpty() && (left.equals(right) || TextRules.similarStoryText(left, right)
                || right.startsWith(left + " ") || left.startsWith(right + " "));
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
