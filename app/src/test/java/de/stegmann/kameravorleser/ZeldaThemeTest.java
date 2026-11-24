package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ZeldaThemeTest {
    @Test public void normalizedZeldaNameSelectsZeldaTheme() {
        assertEquals(ZeldaTheme.Style.ZELDA, ZeldaTheme.forSpeaker("Zelda"));
        assertEquals(ZeldaTheme.Style.ZELDA, ZeldaTheme.forSpeaker("  ZELDA: "));
    }

    @Test public void themeUsesStableSpeakerInsteadOfSingleLabelOutlier() {
        SpeakerLabels.Tracker tracker = new SpeakerLabels.Tracker("Erzähler");
        String text = "Hyrule braucht deine Hilfe.";
        assertEquals(ZeldaTheme.Style.NORMAL,
                ZeldaTheme.forSpeaker(tracker.observe(text, "Pikachu")));
        assertEquals(ZeldaTheme.Style.NORMAL,
                ZeldaTheme.forSpeaker(tracker.observe(text, "Zelda")));
        assertEquals(ZeldaTheme.Style.ZELDA,
                ZeldaTheme.forSpeaker(tracker.observe(text, "Zelda")));
    }

    @Test public void missingOrDifferentSpeakerKeepsNormalTheme() {
        assertEquals(ZeldaTheme.Style.NORMAL, ZeldaTheme.forSpeaker(null));
        assertEquals(ZeldaTheme.Style.NORMAL, ZeldaTheme.forSpeaker(""));
        assertEquals(ZeldaTheme.Style.NORMAL, ZeldaTheme.forSpeaker("Erzähler"));
        assertEquals(ZeldaTheme.Style.NORMAL, ZeldaTheme.forSpeaker("Pikachu"));
        assertEquals(ZeldaTheme.Style.NORMAL, ZeldaTheme.forSpeaker("Zeldas"));
    }
}
