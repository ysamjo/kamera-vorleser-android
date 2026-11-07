package de.stegmann.kameravorleser;

import java.util.Locale;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Textfilter und Ähnlichkeitsschwellen für erkannte Dialoge. */
final class TextRules {
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final float SIMILARITY_MIN_LENGTH_RATIO = 0.85f; // Story-Text muss mindestens 85% der Länge haben
    private static final float SIMILARITY_MAX_LEVENSHTEIN_RATIO = 0.12f; // Max 12% Levenshtein-Distanz (tolerant gegen Screen-Rauschen)
    private static final float DUPLICATE_MIN_LENGTH_RATIO = 0.80f; // Duplikat-Erkennung: 80% Mindestlänge
    private static final float DUPLICATE_MAX_LEVENSHTEIN_RATIO = 0.10f; // Max 10% für Duplikate

    static boolean isActionLabel(String text) {
        String value = normalize(text);
        return value.matches("(a|b|x|y)? ?(weiter|zurück|bestätigen|abbrechen|schließen|überspringen|auswählen|nächstes|untersuchen|sprechen|reden|öffnen|nehmen|verwenden|interagieren)")
                || value.matches("(ja|nein|menü|optionen|automatisch|protokoll)");
    }

    static boolean isNonStoryNotification(String value) {
        return value.matches(".*du hast .* hergestellt.*")
                || value.matches(".*neue kreation.*")
                || value.matches(".*(gegenstand|möbelstück|rezept) (erhalten|gefunden).*?")
                || value.matches(".*wird gespeichert.*");
    }

    static boolean hasDialogueText(String normalized) {
        int letters = 0;
        for (int index = 0; index < normalized.length();) {
            int codePoint = normalized.codePointAt(index);
            if (Character.isLetter(codePoint) && ++letters == 2) return true;
            index += Character.charCount(codePoint);
        }
        return false;
    }

    static boolean similarStoryText(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        int minLength = Math.min(left.length(), right.length());
        if (maxLength == 0 || minLength < maxLength * SIMILARITY_MIN_LENGTH_RATIO) return false;
        return levenshtein(left, right) <= Math.max(1, (int) (maxLength * SIMILARITY_MAX_LEVENSHTEIN_RATIO));
    }

    static boolean similarEnough(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength < 8 || Math.min(left.length(), right.length()) < maxLength * DUPLICATE_MIN_LENGTH_RATIO) return false;
        return levenshtein(left, right) <= Math.max(1, (int) (maxLength * DUPLICATE_MAX_LEVENSHTEIN_RATIO));
    }

    static int levenshtein(String left, String right) {
        int[] costs = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) costs[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            costs[0] = i;
            int diagonal = i - 1;
            for (int j = 1; j <= right.length(); j++) {
                int old = costs[j];
                costs[j] = Math.min(
                        Math.min(costs[j] + 1, costs[j - 1] + 1),
                        diagonal + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1)
                );
                diagonal = old;
            }
        }
        return costs[right.length()];
    }

    static String normalize(String value) {
        String lower = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.GERMAN);
        return NON_WORD.matcher(lower).replaceAll(" ").trim();
    }
}
