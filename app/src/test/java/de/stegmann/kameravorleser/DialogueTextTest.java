package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueTextTest {
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
}
