package de.stegmann.kameravorleser;

import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

/** Gemessene Farbstichproben echter YouTube-Frames; keine synthetischen Wunschfarben. */
public class BubbleReferenceTest {
    private static final String[] CASES = {"gray-night-first", "gray-night-second",
            "gray-day-first", "gray-day-second", "blue-long", "blue-short", "blue-second",
            "pokedex-description", "settings-description", "appearance-controls", "side-response", "scenery"};

    @Test public void actualDialogueFramesPassAndMenusRemainSilent() throws Exception {
        checkSamples(1f);
    }

    @Test public void moderatelyDarkerReferenceSamplesStillPass() throws Exception {
        checkSamples(.8f);
    }

    @Test public void onixPortraitOutlineSeparatesSpeechFromTaskAndControls() throws Exception {
        Properties fixture = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/dialogue/portrait-card.properties")) {
            assertNotNull(input);
            fixture.load(input);
        }
        for (float exposure : new float[] {1f, .8f}) {
            for (String name : new String[] {"first", "second", "task", "control"}) {
                String[] coordinates = fixture.getProperty(name + ".box").split(" ");
                int left = Integer.parseInt(coordinates[0]);
                int top = Integer.parseInt(coordinates[1]);
                int right = Integer.parseInt(coordinates[2]);
                int bottom = Integer.parseInt(coordinates[3]);
                int start = Integer.parseInt(fixture.getProperty(name + ".start"));
                String[] rows = fixture.getProperty(name + ".luma").split(" ");
                BubbleAppearance.Luma luma = (x, y) -> {
                    for (int column = 1; column <= 7; column++) {
                        if (x == left + (right - left) * column / 8) {
                            int offset = (column - 1) * 2;
                            return Math.round(Integer.parseInt(
                                    rows[y - start].substring(offset, offset + 2), 16) * exposure);
                        }
                    }
                    throw new AssertionError("Unerwartete Messposition " + x);
                };
                boolean outlined = BubbleAppearance.hasCompactOutline(
                        left, top, right, bottom, 1280, 574, luma);
                boolean isDialogue = name.equals("first") || name.equals("second");
                assertEquals(name, isDialogue, outlined);
                if (isDialogue) {
                    int[] panel = pixels(fixture.getProperty(name + ".panel"), exposure);
                    int[] above = pixels(fixture.getProperty(name + ".above"), exposure);
                    assertFalse(name, BubbleAppearance.hasUpperBoundary(panel, above));
                    assertEquals(name, BubbleAppearance.Surface.OUTLINED,
                            BubbleAppearance.classifyDialogue(panel, above, () -> outlined));
                }
            }
        }
    }

    private void checkSamples(float exposure) throws Exception {
        for (String name : CASES) {
            Properties fixture = new Properties();
            try (InputStream input = getClass().getResourceAsStream("/dialogue/" + name + ".properties")) {
                assertNotNull(name, input);
                fixture.load(input);
            }
            String[] box = fixture.getProperty("box").split(" ");
            String[] size = fixture.getProperty("size").split(" ");
            float x = (Integer.parseInt(box[0]) + Integer.parseInt(box[2])) / (2f * Integer.parseInt(size[0]));
            float y = (Integer.parseInt(box[1]) + Integer.parseInt(box[3])) / (2f * Integer.parseInt(size[1]));
            int[] panel = pixels(fixture.getProperty("panel"), exposure);
            int[] above = pixels(fixture.getProperty("above"), exposure);
            BubbleAppearance.Surface surface = BubbleAppearance.inDialogueRegion(x, y)
                    && BubbleAppearance.hasUpperBoundary(panel, above)
                    ? BubbleAppearance.classify(panel) : BubbleAppearance.Surface.NONE;
            assertEquals(name + " " + fixture.getProperty("source"),
                    fixture.getProperty("expected"), surface.name());
        }
    }

    @Test public void shortLineSamplingStaysInsideLeftBubbleEdge() {
        BubbleAppearance.SampleRegion region = BubbleAppearance.sampleRegion(
                432, 909, 617, 947, 1920, 1080);
        assertEquals(403, region.x(0));
        assertEquals(716, region.x(8));
        assertEquals(890, region.y(0));
        assertEquals(734, region.y(5));
    }

    private static int[] pixels(String value, float exposure) {
        String[] words = value.split(" ");
        int[] result = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals("-1")) { result[i] = -1; continue; }
            int pixel = Integer.parseInt(words[i], 16);
            int r = Math.round(((pixel >> 16) & 255) * exposure);
            int g = Math.round(((pixel >> 8) & 255) * exposure);
            int b = Math.round((pixel & 255) * exposure);
            result[i] = (r << 16) | (g << 8) | b;
        }
        return result;
    }
}
