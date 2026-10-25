package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceSelectionTest {
    @Test public void differentSpeakersAlternateEvenWithSamePreferredProfile() {
        VoiceSelection voices = new VoiceSelection();
        assertEquals(0, voices.select("Pikachu", 0, 2));
        assertEquals(1, voices.select("Evoli", 0, 2));
        assertEquals(1, voices.select("Evoli", 0, 2));
        assertEquals(0, voices.select("Pikachu", 0, 2));
        assertEquals(1, voices.select("Bisasam", 0, 2));
    }

    @Test public void oneVoiceAndEngineSwitchAreSafe() {
        VoiceSelection voices = new VoiceSelection();
        assertEquals(-1, voices.select("Pikachu", 4, 0));
        assertEquals(0, voices.select("Pikachu", 4, 1));
        assertEquals(0, voices.select("Bisasam", 4, 1));
        voices.reset();
        assertEquals(2, voices.select("Bisasam", 2, 3));
        assertEquals(2, voices.select("BISASAM", 0, 3));
    }
}
