package de.stegmann.kameravorleser;

import org.json.JSONException;
import org.json.JSONObject;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Ein HTTP-Job pro Ausgabe auf dem Worker; Stop darf die Verbindung sofort trennen. */
final class CloudSpeechClient {
    static final class Job {
        volatile boolean cancelled;
        private HttpURLConnection connection;
        synchronized void attach(HttpURLConnection value) throws IOException {
            if (cancelled) { value.disconnect(); throw new InterruptedIOException("Abgebrochen"); }
            connection = value;
        }
        synchronized void detach() { connection = null; }
        synchronized void cancel() {
            cancelled = true;
            if (connection != null) connection.disconnect();
        }
    }

    CloudFallback.Audio synthesize(CloudSpeechRequest request, String fishKey, String googleKey, Job job) throws IOException {
        return CloudFallback.synthesize(
                fishKey.isEmpty() || request.fishVoice.isEmpty() ? null : () -> audio(
                        post("https://api.fish.audio/v1/tts", "Authorization", "Bearer " + fishKey,
                                request.fishBody(), true, job)),
                googleKey.isEmpty() ? null : () -> {
                    boolean bearer = googleKey.startsWith("Bearer ");
                    byte[] json = post("https://texttospeech.googleapis.com/v1/text:synthesize",
                            bearer ? "Authorization" : "X-Goog-Api-Key", googleKey,
                            request.googleBody(), false, job);
                    try {
                        return audio(Base64.decode(new JSONObject(new String(json, StandardCharsets.UTF_8))
                                .getString("audioContent"), Base64.DEFAULT));
                    } catch (JSONException | IllegalArgumentException error) {
                        throw new IOException("Google Chirp: ungültige Audioantwort");
                    }
                }, () -> job.cancelled);
    }

    private byte[] post(String endpoint, String authHeader, String credential, String body,
                        boolean fish, Job job) throws IOException {
        if (credential.indexOf('\r') >= 0 || credential.indexOf('\n') >= 0) {
            throw new IOException("Zugangsdaten enthalten einen Zeilenumbruch");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            job.attach(connection);
            connection.setInstanceFollowRedirects(false); // Zugangsdaten nie an andere Hosts weiterleiten.
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty(authHeader, credential);
            if (fish) connection.setRequestProperty("model", "drama-3");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(payload); }
            int status = connection.getResponseCode();
            if (status != 200) {
                // Keine Anbieter-Fehlermeldungen mit Dialogen oder Zugangsdaten loggen/anzeigen.
                throw new IOException((fish ? "Fish Audio" : "Google Chirp") + ": HTTP " + status);
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (job.cancelled) throw new InterruptedIOException("Abgebrochen");
                    if (out.size() + n > 24 * 1024 * 1024) throw new IOException("Audioantwort zu groß");
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
            job.detach();
        }
    }

    private byte[] audio(byte[] bytes) throws IOException {
        // Verhindert Wiedergabe von HTML/JSON-Fehlerseiten als vermeintlichem MP3.
        boolean id3 = bytes.length > 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
        boolean frame = bytes.length > 2 && (bytes[0] & 255) == 255 && (bytes[1] & 224) == 224;
        if ((!id3 && !frame) || bytes.length > 16 * 1024 * 1024) throw new IOException("Ungültige MP3-Antwort");
        return bytes;
    }
}
