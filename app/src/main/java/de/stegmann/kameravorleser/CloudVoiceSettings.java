package de.stegmann.kameravorleser;

import android.content.SharedPreferences;

/** Aktualisiert nur den mitgelieferten Pool; eigene Listen und lokale TTS bleiben erhalten. */
final class CloudVoiceSettings {
    private CloudVoiceSettings() {}

    static void migrate(SharedPreferences prefs) {
        if (prefs.getBoolean("pokopiaVoicesV2", false)) return;
        SharedPreferences.Editor edit = prefs.edit();
        if (PokopiaVoiceCatalog.shouldUpgrade(prefs.getString("cloudFishVoices", null))) {
            edit.putString("cloudFishVoices", CloudVoiceProfiles.FISH_DEFAULTS)
                    .remove("cloudFishAssignments");
        }
        edit.putBoolean("pokopiaVoicesV1", true).putBoolean("pokopiaVoicesV2", true).apply();
    }
}
