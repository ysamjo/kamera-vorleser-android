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
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/** Ein HTTP-Job pro Ausgabe auf dem Worker. Ein Abbruch kann den HTTP-Read entblocken. */
final class CloudSpeechClient {
    static final String GOOGLE_BACKUP = "Google Chirp · Backup";
    private static final String FISH_ENDPOINT = "https://api.fish.audio/v1/tts";
    private static final int MAX_JSON_BYTES = 24 * 1024 * 1024;

    static final class Job {
        volatile boolean cancelled;
        private HttpURLConnection connection;

        synchronized void attach(HttpURLConnection value) throws IOException {
            if (cancelled) throw new InterruptedIOException("Abgebrochen");
            connection = value;
        }

        synchronized void detach() { connection = null; }

        void cancel(Executor aborter) {
            HttpURLConnection active;
            synchronized (this) {
                cancelled = true;
                active = connection;
            }
            if (active != null) aborter.execute(active::disconnect);
        }
    }

    interface ConnectionFactory { HttpURLConnection open(String endpoint) throws IOException; }

    private interface Response<T> { T read(HttpURLConnection connection, InputStream body) throws IOException; }

    private static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(String provider, int status) {
            super(provider + ": HTTP " + status);
            this.status = status;
        }
    }

    private final ConnectionFactory connections;

    CloudSpeechClient() { this(endpoint -> (HttpURLConnection) new URL(endpoint).openConnection()); }

    CloudSpeechClient(ConnectionFactory connections) { this.connections = connections; }

    byte[] playOrSynthesize(CloudSpeechRequest request, String fishKey, String googleKey, Job job,
            CloudFallback.Playback fishPlayback, BooleanSupplier fishStarted) throws IOException {
        return CloudFallback.playOrSynthesize(
                fishKey.isEmpty() || request.fishVoice.isEmpty() ? null : fishPlayback,
                googleKey.isEmpty() ? null : () -> googleSynthesize(request, googleKey, job),
                fishStarted, () -> job.cancelled);
    }

    void streamFish(CloudSpeechRequest request, String fishKey, Job job, PcmSpeechStream.Sink sink)
            throws IOException {
        try {
            streamFishModel(request, fishKey, job, sink, "drama-3");
        } catch (HttpFailure error) {
            if (error.status != 402) throw error;
            CloudFallback.check(() -> job.cancelled);
            streamFishModel(request, fishKey, job, sink, "s2.1-pro-free");
        }
    }

    private void streamFishModel(CloudSpeechRequest request, String fishKey, Job job,
            PcmSpeechStream.Sink sink, String model) throws IOException {
        post(FISH_ENDPOINT, "Authorization", "Bearer " + fishKey, request.fishBody(), model, job,
                (connection, body) -> {
                    String type = connection.getContentType();
                    if (type != null) {
                        String mime = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                        if (!mime.equals("audio/pcm") && !mime.equals("audio/x-pcm")
                                && !mime.equals("audio/raw") && !mime.equals("application/octet-stream")) {
                            throw new IOException("Fish Audio: PCM-Antwort erwartet");
                        }
                    }
                    PcmSpeechStream.transfer(body, connection.getContentLength(), sink, () -> job.cancelled);
                    return null;
                });
    }

    private byte[] googleSynthesize(CloudSpeechRequest request, String googleKey, Job job) throws IOException {
        boolean bearer = googleKey.startsWith("Bearer ");
        byte[] json = post("https://texttospeech.googleapis.com/v1/text:synthesize",
                bearer ? "Authorization" : "X-Goog-Api-Key", googleKey, request.googleBody(), null, job,
                (connection, body) -> readGoogleResponse(body, job));
        try {
            return validateMp3(Base64.decode(new JSONObject(new String(json, StandardCharsets.UTF_8))
                    .getString("audioContent"), Base64.DEFAULT));
        } catch (JSONException | IllegalArgumentException error) {
            throw new IOException("Google Chirp: ungültige Audioantwort");
        }
    }

    private static byte[] readGoogleResponse(InputStream body, Job job) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = body.read(buffer)) != -1) {
                CloudFallback.check(() -> job.cancelled);
                if (out.size() + count > MAX_JSON_BYTES) throw new IOException("Audioantwort zu groß");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private <T> T post(String endpoint, String authHeader, String credential, String body,
            String model, Job job, Response<T> response) throws IOException {
        CloudFallback.check(() -> job.cancelled);
        if (credential.indexOf('\r') >= 0 || credential.indexOf('\n') >= 0) {
            throw new IOException("Zugangsdaten enthalten einen Zeilenumbruch");
        }
        HttpURLConnection connection = connections.open(endpoint);
        try {
            job.attach(connection);
            connection.setInstanceFollowRedirects(false); // Zugangsdaten nie an andere Hosts weiterleiten.
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty(authHeader, credential);
            if (model != null) connection.setRequestProperty("model", model);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            CloudFallback.check(() -> job.cancelled);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(payload); }
            int status = connection.getResponseCode();
            if (status != 200) {
                // Keine Anbieter-Fehlermeldungen mit Dialogen oder Zugangsdaten ausgeben.
                throw new HttpFailure(model != null ? "Fish Audio" : "Google Chirp", status);
            }
            try (InputStream input = connection.getInputStream()) {
                return response.read(connection, input);
            }
        } finally {
            try { connection.disconnect(); }
            finally { job.detach(); }
        }
    }

    private static byte[] validateMp3(byte[] bytes) throws IOException {
        boolean id3 = bytes.length > 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
        boolean frame = bytes.length > 2 && (bytes[0] & 255) == 255 && (bytes[1] & 224) == 224;
        if ((!id3 && !frame) || bytes.length > 16 * 1024 * 1024) throw new IOException("Ungültige MP3-Antwort");
        return bytes;
    }
}
