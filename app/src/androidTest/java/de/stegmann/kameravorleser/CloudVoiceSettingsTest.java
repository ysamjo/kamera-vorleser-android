package de.stegmann.kameravorleser;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Eigene Test-Preferences; keine Anbieter-Requests, Zugangsdaten oder App-Historie. */
@RunWith(AndroidJUnit4.class)
public class CloudVoiceSettingsTest {
    private SharedPreferences prefs;

    @Before public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences("CloudVoiceSettingsTest", Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
    }

    @After public void cleanup() { assertTrue(prefs.edit().clear().commit()); }

    @Test public void legacyDefaultsUpgradeOnceWithoutChangingOtherSettings() {
        prefs.edit().putBoolean("pokopiaVoicesV1", true)
                .putString("cloudFishVoices", CloudVoiceProfiles.FISH_LEGACY_DEFAULTS)
                .putString("cloudFishAssignments", "{\"bisasam\":\"old\"}")
                .putString("cloudGoogleAssignments", "backup")
                .putBoolean("cloudSpeechEnabled", false).putBoolean("characterEffects", false)
                .putString("speechEngine", "local-engine").putFloat("speechRate", 1.2f)
                .putString("completedDialogues", "history").commit();
        CloudVoiceSettings.migrate(prefs);
        assertEquals(CloudVoiceProfiles.FISH_DEFAULTS, prefs.getString("cloudFishVoices", ""));
        assertFalse(prefs.contains("cloudFishAssignments"));
        assertEquals("backup", prefs.getString("cloudGoogleAssignments", ""));
        assertFalse(prefs.getBoolean("cloudSpeechEnabled", true));
        assertFalse(prefs.getBoolean("characterEffects", true));
        assertEquals("local-engine", prefs.getString("speechEngine", ""));
        assertEquals(1.2f, prefs.getFloat("speechRate", 0f), .001f);
        assertEquals("history", prefs.getString("completedDialogues", ""));
        assertTrue(prefs.getBoolean("pokopiaVoicesV2", false));
        prefs.edit().putString("cloudFishVoices", "custom-after-upgrade").commit();
        CloudVoiceSettings.migrate(prefs);
        assertEquals("custom-after-upgrade", prefs.getString("cloudFishVoices", ""));
    }

    @Test public void customAndEmptyPoolsArePreservedEvenBeforeV1Migration() {
        for (String custom : new String[]{"custom-b,custom-a", ""}) {
            prefs.edit().clear().putString("cloudFishVoices", custom)
                    .putString("cloudFishAssignments", "saved").commit();
            CloudVoiceSettings.migrate(prefs);
            assertEquals(custom, prefs.getString("cloudFishVoices", null));
            assertEquals("saved", prefs.getString("cloudFishAssignments", ""));
            assertTrue(prefs.getBoolean("pokopiaVoicesV1", false));
            assertTrue(prefs.getBoolean("pokopiaVoicesV2", false));
        }
    }

    @Test public void newInstallGetsAllFifteenVoices() {
        CloudVoiceSettings.migrate(prefs);
        assertEquals(15, CloudVoiceProfiles.parse(prefs.getString("cloudFishVoices", ""), true).size());
    }
}
