package de.stegmann.kameravorleser;

/** Stabile Namensvariation ohne gespeicherte oder beim Begrenzen verlorene Raten. */
final class SpeakerVoice {
    private SpeakerVoice() {}

    private static float identity(String speaker, long seed) {
        long hash = seed;
        for (int i = 0; i < speaker.length(); i++) hash = (hash ^ speaker.charAt(i)) * 0x100000001b3L;
        return (hash & 0xffffffL) / (float) 0xffffffL - 0.5f;
    }

    static float pitch(String speaker, float base, float emotion) {
        float pitch = Math.max(.85f, Math.min(1.21f, 1f + (base - 1f) * .75f));
        return Math.max(.82f, Math.min(1.25f,
                Math.max(.85f, Math.min(1.22f, pitch * emotion)) + identity(speaker, 0xcbf29ce484222325L) * .05f));
    }

    static float rate(String speaker, float factor, float global, float emotion) {
        float rate = (1f + (factor - 1f) * .85f) * global * emotion;
        return Math.max(.62f, Math.min(1.98f, rate))
                + identity(speaker, 0x84222325cbf29ce4L) * .04f;
    }
}
