package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueTextTest {
    @Test public void scrollingKeepsSpeakerOnlyWithSharedWords() {
        assertTrue(DialogueText.sameDialogue("Wir gehen zum großen Baum.", "zum großen Baum. Komm mit!"));
        assertTrue(DialogueText.sameDialogue("Wir gehen zum großen Baum.", "zum großen Baum."));
        assertFalse(DialogueText.sameDialogue("Ich helfe dir.", "dir gehört der Baum."));
        assertFalse(DialogueText.sameDialogue("Wir gehen zum großen Baum.", "Schön, dass du da bist!"));
    }

    @Test public void removesVisibleNameLabelWithoutSpeakingIt() {
        assertEquals("Hallo, wie geht es dir?", DialogueText.withoutSpeaker("Pikachu: Hallo, wie geht es dir?", "Pikachu"));
        assertEquals("Guten Morgen!", DialogueText.withoutSpeaker("Bisasam – Guten Morgen!", "Bisasam"));
        assertEquals("Wir helfen dir.", DialogueText.withoutSpeaker("Pikachu Wir helfen dir.", "Pikachu"));
        assertEquals("", DialogueText.withoutSpeaker("Pikachu:", "Pikachu"));
    }

    @Test public void leavesNarrationAndPartialNamesUntouched() {
        assertEquals("Pikachus Welt", DialogueText.withoutSpeaker("Pikachus Welt", "Pikachu"));
        assertEquals("Pikachu hilft uns.", DialogueText.withoutSpeaker("Pikachu hilft uns.", "Erzähler"));
        assertEquals("Hallo", DialogueText.withoutSpeaker("Hallo", "Pikachu"));
        assertEquals("Schön, dass du da bist.", DialogueText.withoutSpeaker("Schön, dass du da bist.", "Glumanda"));
    }

    @Test public void preservesUmlauts() {
        assertEquals("Schön, dass du zurück bist!", DialogueText.withoutSpeaker("Evoli: Schön, dass du zurück bist!", "Evoli"));
    }

    @Test public void normalizesNameAndTextBeforeRemovingLabel() {
        assertEquals("Schön!", DialogueText.withoutSpeaker("Flabe\u0301be\u0301: Scho\u0308n!", "Flabébé"));
    }

    @Test public void missingLabelDoesNotCarrySpeakerIntoDifferentDialogue() {
        assertTrue(DialogueText.sameDialogue("Schön, dass du hier bist!", "Scho\u0308n, dass du hier bist!"));
        assertTrue(DialogueText.sameDialogue("Wir gehen jetzt", "Wir gehen jetzt nach Hause."));
        assertFalse(DialogueText.sameDialogue("Ich helfe dir beim Bauen.", "Willkommen auf der Insel!"));
    }

    @Test public void extensionsPreserveWordsAfterPunctuationAndInsideLastWord() {
        assertEquals("Wie geht es?", DialogueText.extensionSuffix("Hallo!Wie geht es?", "Hallo!"));
        assertEquals("Komm mit!", DialogueText.extensionSuffix("Schön, dass du da bist. Komm mit!", "Schön, dass du da bist."));
        assertEquals("Ich sehe den Baumstamm.", DialogueText.extensionSuffix("Ich sehe den Baumstamm.", "Ich sehe den Baum."));
        assertEquals("Anderer Satz.", DialogueText.extensionSuffix("Anderer Satz.", "Hallo!"));
    }
}
