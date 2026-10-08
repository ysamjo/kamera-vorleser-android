package de.stegmann.kameravorleser;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import org.junit.Rule;
import org.junit.Before;
import org.junit.Assume;
import android.app.KeyguardManager;
import android.os.PowerManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LifecycleSmokeTest {
    @Rule public GrantPermissionRule camera = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Before public void requireUnlockedDevice() {
        Context context = ApplicationProvider.getApplicationContext();
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        Assume.assumeTrue("Gerät muss wach und entsperrt sein", power.isInteractive() && !keyguard.isKeyguardLocked());
    }

    @Test public void pauseResumeRepeatAndRecreateRemainUsable() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (int i = 0; i < 5; i++) {
                scenario.onActivity(activity -> {
                    activity.findViewById(R.id.pauseButton).performClick();
                    assertEquals("WEITER", ((TextView) activity.findViewById(R.id.pauseButton)).getText().toString());
                    activity.findViewById(R.id.repeatButton).performClick();
                    activity.findViewById(R.id.pauseButton).performClick();
                    assertEquals("PAUSE", ((TextView) activity.findViewById(R.id.pauseButton)).getText().toString());
                });
                scenario.moveToState(Lifecycle.State.CREATED);
                scenario.moveToState(Lifecycle.State.RESUMED);
            }
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertEquals("PAUSE", ((TextView) activity.findViewById(R.id.pauseButton)).getText().toString());
            });
        }
    }

    @Test public void slidersMatchPersistedValuesAndSaveUserChanges() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("KameraVorleserPrefs", Context.MODE_PRIVATE);
        boolean hadRate = prefs.contains("speechRate"), hadVolume = prefs.contains("volume");
        float oldRate = prefs.getFloat("speechRate", 1.15f), oldVolume = prefs.getFloat("volume", .8f);
        String oldSpeakerRates = prefs.getString("speakerRates", null);
        prefs.edit().putFloat("speechRate", 1.15f).putFloat("volume", .8f).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SeekBar rate = activity.findViewById(R.id.speedSeekBar);
                SeekBar volume = activity.findViewById(R.id.volumeSeekBar);
                assertEquals(40, rate.getProgress());
                assertEquals(80, volume.getProgress());
                assertTrue(rate.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null));
                assertTrue(volume.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null));
                assertEquals(.75f + rate.getProgress() / 100f, prefs.getFloat("speechRate", 0), .001f);
                assertEquals(volume.getProgress() / 100f, prefs.getFloat("volume", 0), .001f);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(Math.round((prefs.getFloat("speechRate", 0) - .75f) * 100),
                        ((SeekBar) activity.findViewById(R.id.speedSeekBar)).getProgress());
                assertEquals(Math.round(prefs.getFloat("volume", 0) * 100),
                        ((SeekBar) activity.findViewById(R.id.volumeSeekBar)).getProgress());
            });
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            if (hadRate) edit.putFloat("speechRate", oldRate); else edit.remove("speechRate");
            if (hadVolume) edit.putFloat("volume", oldVolume); else edit.remove("volume");
            if (oldSpeakerRates == null) edit.remove("speakerRates"); else edit.putString("speakerRates", oldSpeakerRates);
            edit.commit();
        }
    }
}
