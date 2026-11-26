package de.stegmann.kameravorleser;

/** Story-Erkennungszonen pro Spielkontext. Wirkt nur auf `isInStoryRegion`-Ergebnisse. */
final class GameProfile {
    /** Bestätigter Pokopia-Stand: Literale wie `BubbleAppearance.inDialogueRegion`. */
    static final GameProfile POKOPIA = new GameProfile(0.21f, 0.79f, 0.74f, 0.98f, true);
    /** Weitere Spiele mit Sprechblasen im unteren Drittel, z. B. Zelda Ocarina of Time. */
    static final GameProfile GENERIC_LOWER_THIRD = new GameProfile(0.15f, 0.85f, 0.55f, 0.98f, false);

    private final float minCenterX, maxCenterX, minCenterY, maxCenterY;
    private final boolean grayNeedsSpeakerLabel;

    private GameProfile(float minCenterX, float maxCenterX, float minCenterY, float maxCenterY,
            boolean grayNeedsSpeakerLabel) {
        this.minCenterX = minCenterX;
        this.maxCenterX = maxCenterX;
        this.minCenterY = minCenterY;
        this.maxCenterY = maxCenterY;
        this.grayNeedsSpeakerLabel = grayNeedsSpeakerLabel;
    }

    boolean inZone(float centerX, float centerY) {
        return centerX >= minCenterX && centerX <= maxCenterX
                && centerY >= minCenterY && centerY <= maxCenterY;
    }

    /** Overlap-/HUD-Regeln in MainActivity greifen weiterhin, auch im generischen Profil. */
    boolean grayNeedsSpeakerLabel() {
        return grayNeedsSpeakerLabel;
    }
}
