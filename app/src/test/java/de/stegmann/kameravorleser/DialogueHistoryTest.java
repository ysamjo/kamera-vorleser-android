package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueHistoryTest {
    @Test public void scrollingReadsOnlyNewWordsAndKeepsUmlauts() {
        DialogueHistory history = new DialogueHistory(500);
        history.remember("Pikachu", "Komm mit! Wir gehen zum großen Baum.");
        assertTrue(history.wasSpoken("Pikachu", "zum großen Baum"));
        assertTrue(history.wasSpoken("Pikachu", "Wir gehen zum"));
        assertEquals("Dort blühen Äpfelbäume!", history.unreadText("Pikachu",
                "zum großen Baum. Dort blu\u0308hen A\u0308pfelbäume!"));
        assertEquals("zum großen Baum. Komm mit!", history.unreadText("Evoli",
                "zum großen Baum. Komm mit!"));
        assertEquals("Baumstamm", history.unreadText("Pikachu", "Baumstamm"));
    }

    @Test public void scrollingWaitsForCompletionAndDoesNotPersistUnheardText() {
        DialogueHistory history = new DialogueHistory(500);
        String key = DialogueHistory.key("Pikachu", "Wir gehen zum großen Baum.");
        history.begin(key);
        String scrolling = "zum großen Baum. Dort warten wir.";
        assertTrue(history.awaitsOverlappingSpeech("Pikachu", scrolling));
        assertFalse(history.awaitsOverlappingSpeech("Evoli", scrolling));
        assertEquals(scrolling, history.unreadText("Pikachu", scrolling));
        history.finish(key, false);
        assertFalse(history.awaitsOverlappingSpeech("Pikachu", scrolling));
        assertEquals(scrolling, history.unreadText("Pikachu", scrolling));
        history.begin(key);
        history.finish(key, true);
        assertEquals("Dort warten wir.", history.unreadText("Pikachu", scrolling));
    }

    @Test public void scrollMemorySurvivesRestartAndRepeatStillReadsFullVisibleText() {
        DialogueHistory history = new DialogueHistory(500);
        history.remember("Evoli", "Wir gehen zum großen Baum.");
        DialogueHistory restarted = new DialogueHistory(500);
        restarted.restore(history.snapshot());
        assertEquals("", restarted.unreadText("Evoli", "zum großen Baum."));
        restarted.allowRepeat("Evoli", "zum großen Baum.");
        assertEquals("zum großen Baum.", restarted.unreadText("Evoli", "zum großen Baum."));
        String key = DialogueHistory.key("Evoli", "zum großen Baum.");
        restarted.begin(key);
        restarted.finish(key, true);
        assertEquals("", restarted.unreadText("Evoli", "zum großen Baum."));
    }

    @Test public void shortCommonOverlapAndNegationRemainAudible() {
        DialogueHistory history = new DialogueHistory(500);
        history.remember("Evoli", "Ich helfe dir.");
        assertEquals("dir gehört dieser Baum.", history.unreadText("Evoli", "dir gehört dieser Baum."));
        assertEquals("nicht.", history.unreadText("Evoli", "Ich helfe dir nicht."));
        history.remember("Evoli", "Hallo!");
        assertEquals("Wie geht es?", history.unreadText("Evoli", "Hallo!Wie geht es?"));
    }

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
