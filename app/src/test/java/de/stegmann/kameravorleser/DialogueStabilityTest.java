package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueStabilityTest {
    @Test public void completeSentenceStartsAfterTwoFramesAt120Milliseconds() {
        DialogueStability stability = new DialogueStability();
        assertFalse(stability.accept("Hallo, mein Freund!", 1000));
        assertFalse(stability.accept("Hallo, mein Freund!", 1119));
        assertTrue(stability.accept("Hallo, mein Freund!", 1120));
    }

    @Test public void unfinishedTypingWaitsAndGrowthResetsQuietTime() {
        DialogueStability stability = new DialogueStability();
        assertFalse(stability.accept("Wir gehen", 1000));
        assertFalse(stability.accept("Wir gehen", 1120));
        assertFalse(stability.accept("Wir gehen zum", 1360));
        assertFalse(stability.accept("Wir gehen zum", 1480));
        assertFalse(stability.accept("Wir gehen zum Baum.", 1600));
        assertTrue(stability.accept("Wir gehen zum Baum.", 1720));
    }

    @Test public void missingPunctuationDoesNotBlockForever() {
        DialogueStability stability = new DialogueStability();
        assertFalse(stability.accept("Komm mit", 1000));
        assertFalse(stability.accept("Komm mit", 1419));
        assertTrue(stability.accept("Komm mit", 1420));
    }

    @Test public void ocrChangesAndNegationMustBeConfirmedAgain() {
        DialogueStability stability = new DialogueStability();
        assertFalse(stability.accept("Ich helfe dir!", 1000));
        assertFalse(stability.accept("Ich helfe nicht!", 1120));
        assertTrue(stability.accept("Ich helfe nicht!", 1240));
        assertFalse(stability.accept("Ich helfe nichl!", 1360));
    }

    @Test public void absenceResetAndLongFrameGapRequireFreshConfirmation() {
        DialogueStability stability = new DialogueStability();
        stability.accept("Hallo!", 1000);
        stability.absent(1800);
        assertFalse(stability.accept("Hallo!", 1900));
        assertTrue(stability.accept("Hallo!", 2020));
        stability.reset();
        assertFalse(stability.accept("Hallo!", 2140));
        assertFalse(stability.accept("Hallo!", 4200));
    }

    @Test public void slowPositiveFramesStillConfirmTheSameText() {
        DialogueStability stability = new DialogueStability();
        assertFalse(stability.accept("Achtung! Einsturzgefährdete Felsformation!", 1000));
        assertTrue(stability.accept("Achtung! Einsturzgefährdete Felsformation!", 1850));
        assertFalse(stability.accept("Achtung! Einsturzgefährdete Felsformation! Weiter geht es.", 2700));
    }
}
