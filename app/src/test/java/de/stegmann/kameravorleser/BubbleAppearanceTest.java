package de.stegmann.kameravorleser;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class BubbleAppearanceTest {
    private int[] panel(int color) {
        int[] pixels = new int[45];
        Arrays.fill(pixels, color);
        // Dunkle Schrift im Inneren der Blase darf die Flächenerkennung nicht verhindern.
        for (int col = 2; col < 7; col++) pixels[18 + col] = 0x202028;
        return pixels;
    }

    @Test public void blueAndGraySpeechBackgroundsSurviveText() {
        assertEquals(BubbleAppearance.Surface.BLUE, BubbleAppearance.classify(panel(0x407ac4)));
        assertEquals(BubbleAppearance.Surface.GRAY, BubbleAppearance.classify(panel(0xb0b4ba)));
    }

    @Test public void sceneryAndBlackOrWhiteScreensAreRejected() {
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(panel(0x45944a)));
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(panel(0x202028)));
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(panel(0xffffff)));
        int[] varying = panel(0x707070);
        for (int i = 0; i < 15; i++) varying[i] = 0xd0d0d0;
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(varying));
    }

    @Test public void coloredObjectMustSurroundTextOnBothSides() {
        int[] pixels = panel(0x407ac4);
        for (int row = 0; row < 5; row++) {
            pixels[row * 9] = 0x45944a;
            pixels[row * 9 + 1] = 0x45944a;
        }
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(pixels));
    }

    @Test public void uniformSceneryNeedsAVisibleUpperPanelBoundary() {
        int[] above = new int[9];
        Arrays.fill(above, 0x407ac4);
        assertFalse(BubbleAppearance.hasUpperBoundary(panel(0x407ac4), above));
        Arrays.fill(above, 0x45944a);
        assertTrue(BubbleAppearance.hasUpperBoundary(panel(0x407ac4), above));
        assertTrue(BubbleAppearance.hasUpperBoundary(panel(0xb0b4ba), above));
        Arrays.fill(above, -1);
        assertFalse(BubbleAppearance.hasUpperBoundary(panel(0xb0b4ba), above));
    }

    @Test public void grayAntialiasedGlyphEdgesDoNotVetoTheBackground() {
        int[] pixels = panel(0xb0b4ba);
        pixels[20] = 0x656565;
        pixels[21] = 0x707070;
        assertEquals(BubbleAppearance.Surface.GRAY, BubbleAppearance.classify(pixels));
    }

    @Test public void descriptionHeadingIsNotAnAnimalName() {
        assertFalse(BubbleAppearance.isCharacterLabel("Lagerbestand"));
        assertFalse(BubbleAppearance.isCharacterLabel("Inventar"));
        for (String action : new String[]{"Weiter", "Zurück", "A Weiter", "Ja", "Nein"}) {
            assertFalse(action, BubbleAppearance.isCharacterLabel(action));
        }
        assertFalse(BubbleAppearance.isCharacterLabel("Hier findest du"));
        assertFalse(BubbleAppearance.isCharacterLabel(null));
        assertTrue(BubbleAppearance.isCharacterLabel("Professor Tangoloss"));
        assertTrue(BubbleAppearance.isCharacterLabel("Schlappchu"));
    }

    @Test public void coloredTextOutsideBottomBubbleRegionIsIgnored() {
        assertTrue(BubbleAppearance.inDialogueRegion(.5f, .80f));
        assertFalse(BubbleAppearance.inDialogueRegion(.5f, .40f));
        assertFalse(BubbleAppearance.inDialogueRegion(.10f, .80f));
        assertFalse(BubbleAppearance.inDialogueRegion(.90f, .80f));
        assertFalse(BubbleAppearance.inDialogueRegion(.50f, .99f));
    }

    @Test public void clippedFrameAndColorOnlyAboveTextAreRejected() {
        int[] clipped = panel(0xb0b4ba);
        for (int i = 0; i < 18; i++) clipped[i] = -1;
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(clipped));
        int[] above = panel(0x407ac4);
        for (int i = 36; i < 45; i++) above[i] = 0x45944a;
        assertEquals(BubbleAppearance.Surface.NONE, BubbleAppearance.classify(above));
    }
}
