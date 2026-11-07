package de.stegmann.kameravorleser;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import static de.stegmann.kameravorleser.StoryTextAssembler.Line;

public class SpeakerLabelsTest {
    @Test public void fluctuatingNamesKeepTheSpeakerWhileTheTextBecomesStable() {
        SpeakerLabels.Tracker speakers = new SpeakerLabels.Tracker("Erzähler");
        DialogueStability stability = new DialogueStability();
        String text = "Achtung! Einsturzgefährdete Felsformation!";
        assertEquals("Bisasam", speakers.observe(text, "Bisasam"));
        assertFalse(stability.accept(text, 1000));
        assertEquals("Bisasam", speakers.observe(text, "Bisasarn"));
        assertTrue(stability.accept(text, 1120));
        assertTrue(speakers.waitForName());
        assertEquals("Bisasam", speakers.observe(text, null));
        assertEquals("Bisasam", speakers.observe(text, "Bisasarn"));
        assertFalse(speakers.waitForName());
        assertEquals("Bisasam", speakers.observe(text, "Bisasam"));
    }

    @Test public void confirmedNameAndNewDialogueCanChangeTheSpeaker() {
        SpeakerLabels.Tracker speakers = new SpeakerLabels.Tracker("Erzähler");
        assertEquals("Bisasam", speakers.observe("Oh!", "Bisasam"));
        assertEquals("Bisasam", speakers.observe("Oh!", "Glumanda"));
        assertEquals("Glumanda", speakers.observe("Oh!", "Glumanda"));
        assertEquals("Schiggy", speakers.observe("Ich habe Wasser gefunden.", "Schiggy"));
        assertEquals("Erzähler", speakers.observe("Da hinten leuchtet ein Kristall.", null));
    }

    @Test public void lateNameGetsOneFrameBeforeSpeechAndCannotRestartTheSameText() {
        SpeakerLabels.Tracker speakers = new SpeakerLabels.Tracker("Erzähler");
        assertEquals("Erzähler", speakers.observe("Oh!", null));
        assertEquals("Erzähler", speakers.observe("Oh!", "Pikachu"));
        assertTrue(speakers.waitForName());
        assertEquals("Pikachu", speakers.observe("Oh!", "Pikachu"));
        assertFalse(speakers.waitForName());
        speakers.markSent();
        assertEquals("Pikachu", speakers.observe("Oh!", "Glumanda"));
        assertEquals("Pikachu", speakers.observe("Oh!", "Glumanda"));
        speakers.reset();
        assertEquals("Glumanda", speakers.observe("Oh!", "Glumanda"));
    }

    private final Line story = new Line("Schön, dass du hier bist!", 220, 800, 800, 840);

    @Test public void acceptsNameInsideStoryRoiAndWiderThanOldLimit() {
        Line label = new Line("Professor Tangoloss", 220, 750, 650, 780);
        assertEquals("Professor Tangoloss", SpeakerLabels.above(Arrays.asList(label, story), story, 1000, 1000));
    }

    @Test public void combinesSplitProfessorLabel() {
        Line title = new Line("Professor", 220, 710, 370, 735);
        Line name = new Line("Tangoloss", 220, 745, 420, 770);
        assertEquals("Professor Tangoloss", SpeakerLabels.above(Arrays.asList(name, title, story), story, 1000, 1000));
        Line sameRow = new Line("Tangoloss", 390, 710, 550, 735);
        assertEquals("Professor Tangoloss", SpeakerLabels.above(Arrays.asList(title, sameRow, story), story, 1000, 1000));
    }

    @Test public void rejectsActionSentenceAndTextBelowDialogue() {
        assertNull(SpeakerLabels.above(Arrays.asList(new Line("A Weiter", 220, 750, 350, 780),
                new Line("Ich helfe dir", 220, 750, 450, 780),
                new Line("Evoli", 220, 850, 300, 880)), story, 1000, 1000));
    }

    @Test public void shortDialogueIsNotMistakenForLongerProfessorLabel() {
        assertTrue(SpeakerLabels.hasDialogueWords("Hallo!"));
        assertFalse(SpeakerLabels.hasDialogueWords("Professor Tangoloss"));
        assertEquals("Hallo!", DialogueText.withoutSpeaker("Professor Tangoloss: Hallo!", "Professor Tangoloss"));
    }

    @Test public void combinedProfessorPrefixAndSplitTitleHaveSameIdentity() {
        assertEquals("Professor Tangoloss", SpeakerLabels.inlineName("Professor Tangoloss: Schön, dass du da bist!"));
        assertEquals("Professor Tangoloss", SpeakerLabels.canonicalName("Tangoloss:"));
        assertNull(SpeakerLabels.inlineName("Ich habe Professor Tangoloss gesehen."));
    }
}
