package de.stegmann.kameravorleser;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class CameraZoomTest {
    @Test public void offersHalfZoomOnlyWhenSupportedAndRespectsLimits() {
        assertEquals(Arrays.asList(.5f, 1f, 2f), CameraZoom.presets(.5f, 8f));
        assertEquals(Arrays.asList(.6f, 1f, 2f), CameraZoom.presets(.6f, 8f));
        assertEquals(Arrays.asList(1f, 2f), CameraZoom.presets(1f, 8f));
        assertEquals(Arrays.asList(1f), CameraZoom.presets(1f, 1f));
        assertEquals(Arrays.asList(.5f, 1f, 1.5f), CameraZoom.presets(.4f, 1.5f));
    }
}
