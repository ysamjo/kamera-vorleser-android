package de.stegmann.kameravorleser;

/** Entfernt ausschließlich das zur Dialogbox gehörende Namensschild. */
final class DialogueText {
    private DialogueText() {}

    static String withoutSpeaker(String text, String speaker) {
        if (text == null) return "";
        String visible = text.trim();
        if (speaker == null || speaker.isBlank()
                || TextRules.normalize(speaker).equals(TextRules.normalize("Erzähler"))) {
            return visible;
        }
        String label = speaker.trim();
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
