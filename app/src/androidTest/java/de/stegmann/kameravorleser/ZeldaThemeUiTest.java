package de.stegmann.kameravorleser;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import android.Manifest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ZeldaThemeUiTest {
    @Rule public GrantPermissionRule camera = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    @Test public void confirmedZeldaChangesAppearanceAndOtherSpeakerRestoresIt() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ViewGroup topPanel = activity.findViewById(R.id.topPanel);
                ViewGroup title = (ViewGroup) topPanel.getChildAt(1);
                ImageView emblem = (ImageView) ((ViewGroup) topPanel.getChildAt(0)).getChildAt(0);
                int[] normalTopPixels = render(topPanel.getBackground(), 160, 80);
                int[] normalCardPixels = render(activity.findViewById(R.id.dialogPanel).getBackground(), 240, 120);
                int[] normalEmblemPixels = render(emblem.getDrawable(), 48, 48);
                int normalTitleColor = ((TextView) title.getChildAt(0)).getCurrentTextColor();
                String normalTitleText = ((TextView) title.getChildAt(0)).getText().toString();
                int normalDialogueColor = ((TextView) activity.findViewById(R.id.recognizedText)).getCurrentTextColor();
                int normalAccent = activity.findViewById(R.id.speechButton).getBackgroundTintList()
                        .getDefaultColor();

                activity.applySpeakerTheme("Zelda");

                assertFalse(java.util.Arrays.equals(normalTopPixels,
                        render(topPanel.getBackground(), 160, 80)));
                assertFalse(java.util.Arrays.equals(normalCardPixels,
                        render(activity.findViewById(R.id.dialogPanel).getBackground(), 240, 120)));
                assertFalse(java.util.Arrays.equals(normalEmblemPixels,
                        render(emblem.getDrawable(), 48, 48)));
                assertEquals("HYRULE", ((TextView) title.getChildAt(0)).getText().toString());
                assertEquals(activity.getColor(R.color.zelda_gold),
                        ((TextView) title.getChildAt(0)).getCurrentTextColor());
                assertEquals(activity.getColor(R.color.zelda_ink),
                        ((TextView) activity.findViewById(R.id.recognizedText)).getCurrentTextColor());
                assertEquals(activity.getColor(R.color.zelda_gold),
                        activity.findViewById(R.id.speechButton).getBackgroundTintList().getDefaultColor());
                assertEquals(activity.getColor(R.color.zelda_gold),
                        ((SeekBar) activity.findViewById(R.id.speedSeekBar)).getProgressTintList().getDefaultColor());

                activity.applySpeakerTheme("Pikachu");

                assertArrayEquals(normalTopPixels, render(topPanel.getBackground(), 160, 80));
                assertArrayEquals(normalCardPixels,
                        render(activity.findViewById(R.id.dialogPanel).getBackground(), 240, 120));
                assertArrayEquals(normalEmblemPixels, render(emblem.getDrawable(), 48, 48));
                assertEquals(normalTitleText, ((TextView) title.getChildAt(0)).getText().toString());
                assertEquals(normalTitleColor, ((TextView) title.getChildAt(0)).getCurrentTextColor());
                assertEquals(normalDialogueColor,
                        ((TextView) activity.findViewById(R.id.recognizedText)).getCurrentTextColor());
                assertEquals(normalAccent,
                        activity.findViewById(R.id.speechButton).getBackgroundTintList().getDefaultColor());
            });
        }
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
}
