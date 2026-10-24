package de.stegmann.kameravorleser;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class SpeakerVoiceTest {
    @Test public void characterPitchStaysNearNaturalVoice() {
        assertTrue(SpeakerVoice.pitch("tangoloss", .66f) > .90f);
        assertTrue(SpeakerVoice.pitch("pikachu", 1.30f) < 1.10f);
    }
    @Test public void identitiesAreStableAcrossOrderAndJavaHashCollisions() {
        String[] speakers = {"pikachu", "pichu", "evoli", "togepi", "onix", "stahlos", "Aa", "BB"};
        Set<String> identities = new HashSet<>();
        for (String speaker : speakers) {
            float pitch = SpeakerVoice.pitch(speaker, .66f);
            float rate = SpeakerVoice.rate(speaker, .86f, 1.15f);
            assertEquals(pitch, SpeakerVoice.pitch(speaker, .66f), 0f);
            assertEquals(rate, SpeakerVoice.rate(speaker, .86f, 1.15f), 0f);
            assertTrue(identities.add(pitch + ":" + rate));
        }
        assertEquals("Aa".hashCode(), "BB".hashCode());
    }

    @Test public void voiceRangesPreserveIdentityAtProfileAndSliderExtremes() {
        for (float base : new float[]{.66f, .68f, .82f, 1f, 1.28f, 1.30f}) {
            for (float global : new float[]{.75f, 1.15f, 1.75f}) {
                Set<String> identities = new HashSet<>();
                for (int i = 0; i < 1100; i++) {
                    String name = "pokemon " + i;
                    float pitch = SpeakerVoice.pitch(name, base);
                    float rate = SpeakerVoice.rate(name, 1.16f, global);
                    assertTrue(pitch >= .82f && pitch <= 1.25f);
                    assertTrue(rate >= .6f && rate <= 2f);
                    assertTrue("Profilkollision: " + name, identities.add(pitch + ":" + rate));
                }
            }
        }
    }

    @Test public void speedSliderIsReversibleAndDoesNotFlattenProfiles() {
        float normal = SpeakerVoice.rate("pikachu", 1.12f, 1.15f);
        assertTrue(SpeakerVoice.rate("pikachu", 1.12f, .75f) < normal);
        assertTrue(SpeakerVoice.rate("pikachu", 1.12f, 1.75f) > normal);
        assertEquals(normal, SpeakerVoice.rate("pikachu", 1.12f, 1.15f), 0f);
    }
}
