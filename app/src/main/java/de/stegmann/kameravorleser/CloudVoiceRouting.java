package de.stegmann.kameravorleser;

import java.util.List;

/** Kuratierte Rollen sind unabhängig davon, welche Figur die Kamera zuerst erkennt. */
final class CloudVoiceRouting {
    private CloudVoiceRouting() {}

    static String choose(String speaker, List<String> voices, VoiceAssignments assignments,
                         int fallbackSlot) {
        if (PokopiaVoiceCatalog.isDefaultPool(voices)) {
            String curated = PokopiaVoiceCatalog.voiceFor(speaker);
            if (curated != null) {
                assignments.put(speaker, curated);
                return curated;
            }
        }
        return assignments.choose(speaker, voices, fallbackSlot);
    }

    static String direction(String speaker, List<String> voices, float pitch, float rateFactor) {
        String curated = PokopiaVoiceCatalog.isDefaultPool(voices)
                ? PokopiaVoiceCatalog.directionFor(speaker) : null;
        return curated != null ? curated : CloudVoiceProfiles.direction(pitch, rateFactor);
    }
}
