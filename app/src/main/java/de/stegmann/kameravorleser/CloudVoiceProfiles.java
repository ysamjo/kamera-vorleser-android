package de.stegmann.kameravorleser;

import java.util.ArrayList;
import java.util.List;

final class CloudVoiceProfiles {
    static final String GOOGLE_DEFAULTS = "de-DE-Chirp3-HD-Charon,de-DE-Chirp3-HD-Kore,de-DE-Chirp3-HD-Puck,de-DE-Chirp3-HD-Aoede";
    static final String FISH_DEFAULTS = "c079932b47564fcfad0a9f6874a49df7,77e3908a672a4b43b30786e52655d1bc,88b18e0d81474a0ca08e2ea6f9df5ff4,3130ae7c3d844e2998978289c7c11c98,9b1041732d1b4c2686d695c8bba3b149";
    static List<String> parse(String raw, boolean fish) {
        List<String> result = new ArrayList<>();
        if (raw.trim().isEmpty()) return result;
        for (String value : raw.split(",", -1)) {
            String id = value.trim();
            if (!id.matches(fish ? "[A-Za-z0-9_-]{1,128}" : "de-DE-Chirp3-HD-[A-Za-z]+")) {
                throw new IllegalArgumentException(fish ? "Fish Voice-IDs ohne URL, mit Komma trennen" : "Google-Stimmen müssen de-DE-Chirp3-HD-… heißen");
            }
            if (!result.contains(id)) result.add(id);
        }
        return result;
    }

    static String direction(float pitch, float factor) {
        if (pitch <= .85f) return "tiefe, ruhige und warme Stimme, deutlich sprechen";
        if (pitch >= 1.1f) return "helle, lebhafte und freundliche Stimme, deutlich sprechen";
        if (factor >= 1.1f) return "lebhaft und energiegeladen, deutlich sprechen";
        return "warm und entspannt, natürlich und deutlich sprechen";
    }
}
