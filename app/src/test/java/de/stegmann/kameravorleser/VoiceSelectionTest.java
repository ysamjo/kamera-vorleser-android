package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceSelectionTest {
    @Test public void alternatingNeverFallsBackToLowerQuality() {
        VoiceSelection voices = new VoiceSelection();
        int[] qualities = {400, 100, 400, 200};
        assertEquals(0, voices.select("Pikachu", 0, qualities));
        assertEquals(2, voices.select("Bisasam", 0, qualities));
        assertEquals(0, voices.select("Evoli", 0, qualities));
        voices.reset();
        assertEquals(0, voices.select("Pikachu", 0, new int[]{400, 100}));
        assertEquals(0, voices.select("Bisasam", 0, new int[]{400, 100}));
        voices.reset();
        assertEquals(3, voices.select("Evoli", 3, qualities));
        assertEquals(0, voices.select("Pikachu", 3, qualities));
    }

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
