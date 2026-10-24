package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueHistoryTest {
    @Test public void repeatedOcrAndPunctuationVariationsAreSilent() {
        DialogueHistory history = new DialogueHistory(100);
        history.remember("Pikachu", "Hallo, mein Freund!");
        assertTrue(history.wasSpoken("Pikachu", "Hallo mein Freund"));
        assertTrue(history.wasSpoken("Pikachu", "Hallo mein Freumd"));
        assertFalse(history.wasSpoken("Pikachu", "Die Welt ist riesig"));
    }

    @Test public void identicalLinesFromDifferentPokemonCanBeRead() {
        DialogueHistory history = new DialogueHistory(100);
        history.remember("Bisasam", "Ich bin hier!");
        assertTrue(history.wasSpoken("Bisasam", "Ich bin hier!"));
        assertFalse(history.wasSpoken("Glumanda", "Ich bin hier!"));
    }

    @Test public void historySurvivesRestoreAndEvictsOnlyOldest() {
        DialogueHistory original = new DialogueHistory(2);
        original.remember("Evoli", "Guten Morgen!");
        original.remember("Pikachu", "Komm mit!");
        DialogueHistory restored = new DialogueHistory(2);
        restored.restore(original.snapshot());
        assertTrue(restored.wasSpoken("Evoli", "Guten Morgen!"));
        restored.remember("Bisasam", "Ich helfe dir.");
        assertFalse(restored.wasSpoken("Evoli", "Guten Morgen!"));
        assertTrue(restored.wasSpoken("Pikachu", "Komm mit!"));
        assertTrue(restored.wasSpoken("Bisasam", "Ich helfe dir."));
    }

    @Test public void cancelledOrManuallyRepeatedDialogueIsAllowedAgain() {
        DialogueHistory history = new DialogueHistory(100);
        String key = DialogueHistory.key("Schiggy", "Hallo!");
        history.remember("Schiggy", "Hallo!");
        assertTrue(history.forgetKey(key));
        assertFalse(history.wasSpoken("Schiggy", "Hallo!"));
        history.remember("Schiggy", "Hallo!");
        assertTrue(history.forget("Schiggy", "Hallo!"));
        assertFalse(history.wasSpoken("Schiggy", "Hallo!"));
    }

    @Test public void shorterAlreadyHeardFragmentsAreNotRepeated() {
        DialogueHistory history = new DialogueHistory(100);
        history.remember("Pikachu", "Wir gehen jetzt zum großen Baum.");
        assertTrue(history.wasSpoken("Pikachu", "Wir gehen jetzt"));
        assertFalse(history.wasSpoken("Pikachu", "Wir gehen jetzt zum großen Baum und weiter."));
    }

    @Test public void unfinishedSpeechIsNotPersistedAndCanRetryAfterRestart() {
        DialogueHistory history = new DialogueHistory(500);
        String key = DialogueHistory.key("Evoli", "Schön, dass du zurück bist!");
        history.begin(key);
        assertTrue(history.wasSpoken("Evoli", "Schön, dass du zurück bist!"));
        assertTrue(history.snapshot().isEmpty());
        DialogueHistory restarted = new DialogueHistory(500);
        restarted.restore(history.snapshot());
        assertFalse(restarted.wasSpoken("Evoli", "Schön, dass du zurück bist!"));
        assertFalse(history.finish(key, false));
        assertFalse(history.wasSpoken("Evoli", "Schön, dass du zurück bist!"));
        history.begin(key);
        assertTrue(history.finish(key, true));
        restarted.restore(history.snapshot());
        assertTrue(restarted.wasSpoken("Evoli", "Schön, dass du zurück bist!"));
        assertFalse(history.finish(key, true));
    }

    @Test public void repeatBypassesFuzzyHistoryWithoutDeletingCompletedDialogues() {
        DialogueHistory history = new DialogueHistory(500);
        history.remember("Pikachu", "Hallo, mein Freund!");
        history.remember("Evoli", "Komm doch mit!");
        history.allowRepeat("Pikachu", "Hallo mein Freumd");
        assertFalse(history.wasSpoken("Pikachu", "Hallo mein Freund"));
        assertTrue(history.wasSpoken("Evoli", "Komm doch mit!"));
        String key = DialogueHistory.key("Pikachu", "Hallo mein Freund");
        history.begin(key);
        assertTrue(history.wasSpoken("Pikachu", "Hallo mein Freund"));
        history.finish(key, false);
        assertFalse(history.wasSpoken("Pikachu", "Hallo mein Freund"));
        DialogueHistory restarted = new DialogueHistory(500);
        restarted.restore(history.snapshot());
        assertTrue(restarted.wasSpoken("Pikachu", "Hallo mein Freund"));
        history.begin(key);
        history.finish(key, true);
        assertTrue(history.wasSpoken("Pikachu", "Hallo mein Freund"));
    }

    @Test public void shortExtensionsRemainNewAndDecomposedUmlautsMatch() {
        DialogueHistory history = new DialogueHistory(500);
        history.remember("Evoli", "Wir gehen jetzt zum großen Baum");
        assertFalse(history.wasSpoken("Evoli", "Wir gehen jetzt zum großen Baum dort"));
        history.remember("Flabébé", "Schön, dass du zurück bist!");
        assertTrue(history.wasSpoken("Flabe\u0301be\u0301", "Scho\u0308n, dass du zuru\u0308ck bist!"));
    }

    @Test public void longCompletedDialogueSurvivesRestore() {
        DialogueHistory history = new DialogueHistory(500);
        String text = "Wir gehen weiter. ".repeat(100);
        history.remember("Evoli", text);
        DialogueHistory restarted = new DialogueHistory(500);
        restarted.restore(history.snapshot());
        assertTrue(restarted.wasSpoken("Evoli", text));
    }

    @Test public void insertedNegationIsNotDismissedAsOcrNoise() {
        DialogueHistory history = new DialogueHistory(500);
        history.remember("Pikachu", "Ich werde dir heute bei der Arbeit helfen");
        assertFalse(history.wasSpoken("Pikachu", "Ich werde dir heute nicht bei der Arbeit helfen"));
    }
}
