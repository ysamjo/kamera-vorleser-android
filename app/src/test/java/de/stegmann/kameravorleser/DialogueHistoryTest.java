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
}
