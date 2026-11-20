package de.stegmann.kameravorleser;

import org.junit.Test;
import static org.junit.Assert.*;

public class GameProfileTest {
    @Test public void pokopiaZoneMatchesHistoricStoryRegion() {
        GameProfile pokopia = GameProfile.POKOPIA;
        assertTrue(pokopia.inZone(.5f, .80f));
        assertTrue(pokopia.inZone(.21f, .74f));
        assertTrue(pokopia.inZone(.79f, .98f));
        assertFalse(pokopia.inZone(.5f, .73f));
        assertFalse(pokopia.inZone(.5f, .99f));
        assertFalse(pokopia.inZone(.20f, .8f));
        assertFalse(pokopia.inZone(.8f, .8f));
    }

    @Test public void genericZoneCoversTheUnteresDrittel() {
        GameProfile generic = GameProfile.GENERIC_LOWER_THIRD;
        assertTrue(generic.inZone(.5f, .60f));
        assertTrue(generic.inZone(.15f, .55f));
        assertTrue(generic.inZone(.85f, .60f));
        assertTrue(generic.inZone(.5f, .98f));
        assertFalse(generic.inZone(.5f, .54f));
        assertFalse(generic.inZone(.5f, .99f));
        assertFalse(generic.inZone(.14f, .60f));
        assertFalse(generic.inZone(.86f, .60f));
    }

    @Test public void genericZoneIncludesThePokopiaZone() {
        for (float cx = 0.21f; cx <= 0.79f + 0.001f; cx += 0.01f) {
            assertTrue(GameProfile.GENERIC_LOWER_THIRD.inZone(cx, 0.80f));
        }
        for (float cy = 0.74f; cy <= 0.98f + 0.001f; cy += 0.01f) {
            assertTrue(GameProfile.GENERIC_LOWER_THIRD.inZone(0.5f, cy));
        }
    }

    @Test public void grayWithoutSpeakerLabelOnlyRejectedForPokopia() {
        assertTrue(GameProfile.POKOPIA.grayNeedsSpeakerLabel());
        assertFalse(GameProfile.GENERIC_LOWER_THIRD.grayNeedsSpeakerLabel());
    }
}
