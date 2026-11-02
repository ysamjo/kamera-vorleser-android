package de.stegmann.kameravorleser;

import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

final class CloudSpeechSettingsDialog {
    static AlertDialog create(AppCompatActivity activity, SharedPreferences prefs,
                              CloudCredentials credentials, Runnable saved) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad, pad, pad);
        TextView info = new TextView(activity);
        info.setText("Fish Audio Drama 3 liest zuerst. Bei Synthesefehlern übernimmt Google Chirp 3 HD, sofern eingerichtet. Nur Dialogtexte werden übertragen; Kamerabilder bleiben auf dem Gerät. Beide APIs können Kosten verursachen.\n\nLeere Schlüsselfelder behalten gespeicherte Zugänge. Google benötigt ein Cloud-TTS-Projekt mit aktivierter API und Abrechnung, keinen Gemini-Schlüssel.");
        form.addView(info);
        CheckBox enabled = new CheckBox(activity);
        enabled.setText("Fish Audio + Google-Backup verwenden");
        enabled.setChecked(prefs.getBoolean("cloudSpeechEnabled", true));
        form.addView(enabled);
        EditText fish = field(form, "Fish API-Schlüssel (leer = behalten)", "", true);
        EditText voices = field(form, "Fish Voice-IDs, mit Komma getrennt",
                prefs.getString("cloudFishVoices", CloudVoiceProfiles.FISH_DEFAULTS), false);
        TextView roles = new TextView(activity);
        roles.setText("Der Pokopia-Standard enthält 15 Stimmen: unter anderem eigene Rollen für Schlappchu, Pikachu, Evoli, Bisasam, Glumanda, Schiggy, den Professor, gemütliche Riesen, Geister und technische Pokémon. Verwandte Figuren teilen sich eine Rolle. Eine eigene Voice-Liste ersetzt diese Vorauswahl.");
        form.addView(roles);
        EditText google = field(form, "Google Cloud API-Key oder Bearer-Token (Backup)", "", true);
        EditText googleVoices = field(form, "Deutsche Chirp-Backup-Stimmen, mit Komma getrennt",
                prefs.getString("cloudGoogleVoices", CloudVoiceProfiles.GOOGLE_DEFAULTS), false);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Fish Audio → Google-Backup")
                .setView(scroll).setPositiveButton("Speichern", null)
                .setNeutralButton("Zugänge löschen", (d, which) -> { credentials.clear(); saved.run(); })
                .setNegativeButton("Abbrechen", null).create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    CloudVoiceProfiles.parse(voices.getText().toString(), true);
                    if (CloudVoiceProfiles.parse(googleVoices.getText().toString(), false).isEmpty()) {
                        throw new IllegalArgumentException("Mindestens eine deutsche Chirp-Stimme eintragen");
                    }
                    String[] old;
                    try { old = credentials.read(); }
                    catch (Exception error) { throw new IllegalArgumentException("Zugangsspeicher nicht lesbar. Zuerst Zugänge löschen."); }
                    String fishKey = fish.getText().toString().trim();
                    String googleKey = google.getText().toString().trim();
                    if (fishKey.isEmpty()) fishKey = old[0];
                    if (googleKey.isEmpty()) googleKey = old[1];
                    if (fishKey.indexOf('\n') >= 0 || googleKey.indexOf('\n') >= 0
                            || fishKey.indexOf('\r') >= 0 || googleKey.indexOf('\r') >= 0) {
                        throw new IllegalArgumentException("Schlüssel dürfen keine Zeilenumbrüche enthalten");
                    }
                    if (!fishKey.isEmpty() && voices.getText().toString().trim().isEmpty()) {
                        throw new IllegalArgumentException("Für Fish mindestens eine Voice-ID eintragen");
                    }
                    credentials.write(fishKey, googleKey);
                    prefs.edit().putBoolean("cloudSpeechEnabled", enabled.isChecked())
                            .putString("cloudFishVoices", voices.getText().toString().trim())
                            .putString("cloudGoogleVoices", googleVoices.getText().toString().trim()).apply();
                    saved.run();
                    dialog.dismiss();
                } catch (IllegalArgumentException error) { info.setText(error.getMessage()); }
                catch (Exception error) { info.setText("Zugänge konnten nicht sicher gespeichert werden"); }
            });
        });
        return dialog;
    }

    private static EditText field(LinearLayout form, String label, String text, boolean secret) {
        TextView title = new TextView(form.getContext());
        title.setText(label);
        form.addView(title);
        EditText input = new EditText(form.getContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (secret
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        input.setText(text);
        input.setSaveEnabled(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        form.addView(input);
        return input;
    }
}
