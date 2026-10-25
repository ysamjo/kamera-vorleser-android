package de.stegmann.kameravorleser;

import java.util.ArrayList;
import java.util.List;

final class CameraZoom {
    private CameraZoom() {}

    static List<Float> presets(float min, float max) {
        List<Float> result = new ArrayList<>();
        if (min < 1f) result.add(Math.max(.5f, min));
        if (min <= 1f && max >= 1f) result.add(1f);
        if (max > 1f) result.add(Math.min(2f, max));
        return result;
    }
}
