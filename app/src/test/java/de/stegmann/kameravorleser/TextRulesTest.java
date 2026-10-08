package de.stegmann.kameravorleser;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextRulesTest {
    @Test public void actionLabelsAndNotificationsStaySilent() {
        String[] actions = {"A Weiter", "B Zurück", "X Untersuchen", "Ja", "Nein", "Menü",
                "Optionen", "Automatisch", "Protokoll", "Abbrechen", "Schließen", "Sprechen"};
        for (String text : actions) assertTrue(text, TextRules.isActionLabel(text));
        String[] notices = {"Du hast einen Tisch hergestellt!", "Neue Kreation", "Gegenstand erhalten",
                "Möbelstück gefunden", "Rezept erhalten", "Wird gespeichert"};
        for (String text : notices) assertTrue(text, TextRules.isNonStoryNotification(TextRules.normalize(text)));
    }
    @Test public void ordinarySyntheticDialogueIsNotAnActionOrNotification() {
        String[] stories = {"Hallo!", "Komm bitte mit.", "Ich brauche deine Hilfe.", "Wo bist du?",
                "Das ist ein schöner Tag.", "Hier können wir etwas bauen.", "Schau dir das einmal an!",
                "Danke für deine Hilfe.", "Wir haben noch viel zu tun.", "Was hast du gefunden?",
                "Ich will nach Hause gehen.", "Du kannst mir vertrauen.", "Der Weg ist frei.",
                "Bitte warte hier auf mich.", "Gut gemacht!", "Sieh mal, dort ist ein Haus!"};
        for (String text : stories) {
            assertFalse(text, TextRules.isActionLabel(text));
            assertFalse(text, TextRules.isNonStoryNotification(TextRules.normalize(text)));
            assertTrue(text, TextRules.looksLikeGermanStory(text));
        }
    }
    @Test public void normalizationAndSimilarityPreserveThresholds() {
        assertEquals("hallo pokémon 42", TextRules.normalize(" HALLO, Pokémon! 42 "));
        assertEquals(3, TextRules.levenshtein("kitten", "sitting"));
        assertFalse(TextRules.similarEnough("hallo", "hallo"));
        assertTrue(TextRules.similarEnough("hallo mein freund", "hallo mein freumd"));
        assertFalse(TextRules.similarEnough("hallo", "hallo mein freund"));
        assertTrue(TextRules.similarStoryText("hallo mein freund", "hallo mein freumd"));
        assertFalse(TextRules.similarStoryText("hallo", "hallo mein freund"));
        assertFalse(TextRules.looksLikeGermanStory("12345"));
        assertFalse(TextRules.looksLikeGermanStory("Brrr"));
    }
}
