package de.stegmann.kameravorleser;

import java.util.ArrayList;
import java.util.List;

final class CloudVoiceProfiles {
    static final String GOOGLE_DEFAULTS = "de-DE-Chirp3-HD-Charon,de-DE-Chirp3-HD-Kore,de-DE-Chirp3-HD-Puck,de-DE-Chirp3-HD-Aoede";
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
