package de.stegmann.kameravorleser;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import com.google.android.material.button.MaterialButton;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ZeldaThemeUiTest {
    private static final int[] BACKGROUND_IDS = {
            R.id.topPanel, R.id.cameraShade, R.id.storyRegion, R.id.storyRegionLabel,
            R.id.controlPanel, R.id.dialogPanel, R.id.statusText, R.id.appEmblem,
            R.id.speakerText
    };
    private static final int[] TEXT_IDS = {
            R.id.appTitleText, R.id.appSubtitleText, R.id.storyZoneText,
            R.id.recognizedText, R.id.readNowHint, R.id.confidenceText, R.id.speakerText,
            R.id.statusText
    };
    private static final int[] BUTTON_IDS = {
            R.id.speechButton, R.id.zoomButton, R.id.pauseButton, R.id.repeatButton,
            R.id.soundsToggleButton
    };
    private static final String DIALOGUE = "Hyrule braucht deine Hilfe.";

    @Rule public GrantPermissionRule camera = GrantPermissionRule.grant(Manifest.permission.CAMERA);
    private SharedPreferences prefs;
    private Map<String, ?> preferencesBefore;

    @Before public void isolateSpeechAndNetwork() {
        Context context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences("KameraVorleserPrefs", Context.MODE_PRIVATE);
        preferencesBefore = new HashMap<>(prefs.getAll());
        prefs.edit().putBoolean("cloudSpeechConfiguredV1", true)
                .putBoolean("cloudSpeechEnabled", false)
                .putString("speechEngine", "de.stegmann.kameravorleser.test.missing")
                .commit();
    }

    @After public void restorePreferences() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (!preferencesBefore.containsKey(key)) editor.remove(key);
        }
        for (Map.Entry<String, ?> entry : preferencesBefore.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Set) {
                Set<String> restored = new HashSet<>();
                for (Object item : (Set<?>) value) restored.add((String) item);
                editor.putStringSet(entry.getKey(), restored);
            }
        }
        editor.commit();
    }

    @Test public void speakerLifecycleSwitchesThemeOnlyAfterConfirmationAndRestoresIt() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // OCR results are posted to the main thread and cannot interleave with these assertions.
            scenario.onActivity(activity -> {
                Appearance normal = Appearance.capture(activity);
                assertEquals("POKOPIA", normal.title);
                assertEquals("Pokopia Story-Vorleser", normal.iconDescription);

                activity.updateSpeakerFromOcr(DIALOGUE, "Pikachu");
                assertAppearanceEquals(normal, Appearance.capture(activity));
                activity.updateSpeakerFromOcr(DIALOGUE, "Zelda");
                assertAppearanceEquals(normal, Appearance.capture(activity));

                activity.updateSpeakerFromOcr(DIALOGUE, "Zelda");
                Appearance zelda = Appearance.capture(activity);
                assertAppearanceChanged(normal, zelda);
                assertEquals("HYRULE", zelda.title);
                assertEquals("Hyrule Story-Vorleser", zelda.iconDescription);
                assertEquals(activity.getColor(R.color.zelda_gold), zelda.titleColor);
                assertEquals(activity.getColor(R.color.zelda_ink), zelda.textColors[3]);
                assertEquals(activity.getColor(R.color.zelda_gold), zelda.buttonTints[0]);
                assertEquals(activity.getColor(R.color.zelda_gold), zelda.speedTints[0]);
                assertEquals(activity.getColor(R.color.zelda_gold), zelda.speedTints[1]);
                int[] chipPixels = render(activity.findViewById(R.id.speakerText).getBackground(), 240, 40);
                assertEquals(activity.getColor(R.color.zelda_green_dark),
                        chipPixels[(chipPixels.length / 2) + 120]);

                activity.updateSpeakerFromOcr(DIALOGUE, "Pikachu");
                assertAppearanceEquals(zelda, Appearance.capture(activity));
                activity.updateSpeakerFromOcr(DIALOGUE, "Pikachu");
                assertAppearanceEquals(normal, Appearance.capture(activity));

                activity.updateSpeakerFromOcr(DIALOGUE, "Zelda");
                activity.updateSpeakerFromOcr(DIALOGUE, "Zelda");
                assertAppearanceChanged(normal, Appearance.capture(activity));
                activity.updateSpeakerFromOcr("Ein neuer Dialog beginnt.", null);
                assertAppearanceEquals(normal, Appearance.capture(activity));

                activity.updateSpeakerFromOcr(DIALOGUE, "Zelda");
                activity.updateSpeakerFromOcr(DIALOGUE, "Zelda");
                assertAppearanceChanged(normal, Appearance.capture(activity));
                activity.findViewById(R.id.pauseButton).performClick();
                assertAppearanceEquals(normal, Appearance.capture(activity));
                activity.findViewById(R.id.pauseButton).performClick();
                assertAppearanceEquals(normal, Appearance.capture(activity));
            });
        }
    }

    private static void assertAppearanceChanged(Appearance normal, Appearance changed) {
        for (int id : BACKGROUND_IDS) {
            assertFalse("background " + id, Arrays.equals(normal.backgrounds.get(id), changed.backgrounds.get(id)));
        }
        assertFalse(Arrays.equals(normal.emblem, changed.emblem));
        assertFalse(Arrays.equals(normal.rippleMask, changed.rippleMask));
        assertFalse(Arrays.equals(normal.storyIcon, changed.storyIcon));
        assertFalse(Arrays.equals(normal.textColors, changed.textColors));
        assertFalse(Arrays.equals(normal.buttonTints, changed.buttonTints));
        assertFalse(Arrays.equals(normal.speedTints, changed.speedTints));
        assertFalse(Arrays.equals(normal.volumeTints, changed.volumeTints));
    }

    private static void assertAppearanceEquals(Appearance expected, Appearance actual) {
        for (int id : BACKGROUND_IDS) {
            assertArrayEquals("background " + id, expected.backgrounds.get(id), actual.backgrounds.get(id));
        }
        assertArrayEquals(expected.emblem, actual.emblem);
        assertArrayEquals(expected.rippleMask, actual.rippleMask);
        assertArrayEquals(expected.storyIcon, actual.storyIcon);
        assertArrayEquals(expected.textColors, actual.textColors);
        assertArrayEquals(expected.buttonTints, actual.buttonTints);
        assertArrayEquals(expected.speedTints, actual.speedTints);
        assertArrayEquals(expected.volumeTints, actual.volumeTints);
        assertEquals(expected.title, actual.title);
        assertEquals(expected.iconDescription, actual.iconDescription);
    }

    private static int[] render(Drawable drawable, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(bitmap));
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        return pixels;
    }

    private static int[] render(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int[] pixels = new int[view.getWidth() * view.getHeight()];
        bitmap.getPixels(pixels, 0, view.getWidth(), 0, 0, view.getWidth(), view.getHeight());
        bitmap.recycle();
        return pixels;
    }

    private static int[] renderRippleMask(Drawable foreground) {
        if (foreground instanceof RippleDrawable) {
            RippleDrawable ripple = (RippleDrawable) foreground;
            for (int i = 0; i < ripple.getNumberOfLayers(); i++) {
                if (ripple.getId(i) == android.R.id.mask) {
                    return render(ripple.getDrawable(i), 240, 120);
                }
            }
        }
        return render(foreground, 240, 120);
    }

    private static final class Appearance {
        final Map<Integer, int[]> backgrounds = new HashMap<>();
        final int[] emblem;
        final int[] rippleMask;
        final int[] storyIcon;
        final int[] textColors;
        final int[] buttonTints;
        final int[] speedTints;
        final int[] volumeTints;
        final String title;
        final int titleColor;
        final String iconDescription;

        Appearance(MainActivity activity) {
            for (int id : BACKGROUND_IDS) {
                backgrounds.put(id, render(activity.findViewById(id).getBackground(), 160, 80));
            }
            emblem = render(((ImageView) activity.findViewById(R.id.appIcon)).getDrawable(), 48, 48);
            rippleMask = renderRippleMask(activity.findViewById(R.id.dialogPanel).getForeground());
            storyIcon = render(activity.findViewById(R.id.storyZoneIcon));
            textColors = new int[TEXT_IDS.length];
            for (int i = 0; i < TEXT_IDS.length; i++) {
                textColors[i] = ((TextView) activity.findViewById(TEXT_IDS[i])).getCurrentTextColor();
            }
            buttonTints = new int[BUTTON_IDS.length];
            for (int i = 0; i < BUTTON_IDS.length; i++) {
                buttonTints[i] = ((MaterialButton) activity.findViewById(BUTTON_IDS[i]))
                        .getBackgroundTintList().getDefaultColor();
            }
            SeekBar speed = activity.findViewById(R.id.speedSeekBar);
            speedTints = new int[]{speed.getProgressTintList().getDefaultColor(),
                    speed.getThumbTintList().getDefaultColor()};
            SeekBar volume = activity.findViewById(R.id.volumeSeekBar);
            volumeTints = new int[]{volume.getProgressTintList().getDefaultColor(),
                    volume.getThumbTintList().getDefaultColor()};
            TextView titleView = activity.findViewById(R.id.appTitleText);
            title = titleView.getText().toString();
            titleColor = titleView.getCurrentTextColor();
            iconDescription = activity.findViewById(R.id.appIcon).getContentDescription().toString();
        }

        static Appearance capture(MainActivity activity) {
            return new Appearance(activity);
        }
    }
}
