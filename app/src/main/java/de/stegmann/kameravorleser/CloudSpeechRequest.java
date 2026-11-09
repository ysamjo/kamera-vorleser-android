package de.stegmann.kameravorleser;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/** Unveränderlicher Snapshot einer Ausgabe, ohne Android oder Zugangsdaten. */
final class CloudSpeechRequest {
    static final int FISH_SAMPLE_RATE = 24000;
    final String text, fishVoice, googleVoice, direction;
    final float speed;

    CloudSpeechRequest(String text, String fishVoice, String googleVoice, String direction, float speed) {
        this.text = Normalizer.normalize(text, Normalizer.Form.NFC);
        if (this.text.trim().isEmpty() || this.text.getBytes(StandardCharsets.UTF_8).length > 5000) {
            throw new IllegalArgumentException("Dialog leer oder über 5000 UTF-8-Bytes");
        }
        this.fishVoice = fishVoice;
        this.googleVoice = googleVoice;
        this.direction = direction;
        this.speed = Float.isFinite(speed) ? Math.max(.75f, Math.min(1.75f, speed)) : 1f;
    }

    String fishBody() {
        return "{\"text\":" + quote(direction.isEmpty() ? text : "[" + direction + "] " + text)
                + ",\"reference_id\":" + quote(fishVoice)
                + ",\"language\":\"de-DE\",\"format\":\"pcm\",\"sample_rate\":" + FISH_SAMPLE_RATE
                // balanced senkt die Zeit bis zum ersten Audioblock; kleinere Chunks
                // liefern bei längeren Dialogen früher hörbaren Ton.
                + ",\"latency\":\"balanced\",\"chunk_length\":150"
                + ",\"prosody\":{\"speed\":" + speed + "}}";
    }

    String googleBody() {
        // Fish-Regieanweisungen gehören niemals in den Google-Vorlesetext.
        return "{\"input\":{\"text\":" + quote(text) + "},\"voice\":{\"languageCode\":\"de-DE\",\"name\":"
                + quote(googleVoice) + "},\"audioConfig\":{\"audioEncoding\":\"MP3\",\"speakingRate\":" + speed + "}}";
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
