package de.stegmann.kameravorleser;

import java.util.function.BooleanSupplier;

/** Konservative Flächenprüfung um eine OCR-Zeile, ohne Android-Abhängigkeiten. */
final class BubbleAppearance {
    // GRAY umfasst auch das nahezu weiße Innere der tatsächlichen Tierdialogbox.
    enum Surface { NONE, BLUE, GRAY, OUTLINED }
    static final int ROWS = 5;
    static final int COLUMNS = 9;

    private BubbleAppearance() {}

    static final class SampleRegion {
        final int left, right, top, bottom, aboveY;
        SampleRegion(int left, int right, int top, int bottom, int aboveY) {
            this.left = left; this.right = right; this.top = top;
            this.bottom = bottom; this.aboveY = aboveY;
        }
        int x(int column) { return left + Math.max(1, right - left) * column / (COLUMNS - 1); }
        int y(int row) { return row == ROWS ? aboveY
                : top + Math.max(1, bottom - top) * row / (ROWS - 1); }
    }

    static SampleRegion sampleRegion(int left, int top, int right, int bottom,
            int width, int height) {
        int lineHeight = bottom - top;
        int centerX = (left + right) / 2, centerY = (top + bottom) / 2;
        int halfWidth = Math.max((right - left) / 2 + lineHeight, (int) (width * .10f));
        int halfHeight = Math.max(4, lineHeight);
        // Kurze erste Zeilen beginnen ebenfalls am linken Innenrand der Blase.
        // Eine symmetrische Mindestbreite würde hier links in die Spielwelt greifen.
        return new SampleRegion(Math.max(centerX - halfWidth, Math.round(width * .21f)),
                Math.min(centerX + halfWidth, Math.round(width * .79f)),
                centerY - halfHeight, centerY + halfHeight, Math.round(height * .68f));
    }

    static Surface classify(int[] pixels) {
        if (pixels.length != ROWS * COLUMNS) return Surface.NONE;
        if (isPanel(pixels, true)) return Surface.BLUE;
        if (isPanel(pixels, false)) return Surface.GRAY;
        return Surface.NONE;
    }

    static Surface classifyDialogue(int[] pixels, int[] above, BooleanSupplier hasOutline) {
        Surface surface = classify(pixels);
        if (surface == Surface.NONE || hasUpperBoundary(pixels, above)) return surface;
        return hasOutline.getAsBoolean() ? Surface.OUTLINED : Surface.NONE;
    }

    /** Eine bis oberhalb der Dialogzone fortgesetzte Farbfläche ist kein unterer Kasten. */
    static boolean hasUpperBoundary(int[] panel, int[] above) {
        int[][] channels = new int[3][panel.length];
        int count = 0;
        for (int pixel : panel) {
            if (pixel < 0) continue;
            channels[0][count] = (pixel >> 16) & 255;
            channels[1][count] = (pixel >> 8) & 255;
            channels[2][count++] = pixel & 255;
        }
        if (count < 29) return false;
        for (int[] channel : channels) java.util.Arrays.sort(channel, 0, count);
        boolean bluePanel = channels[2][count / 2] > 70
                && channels[2][count / 2] > channels[0][count / 2] + 24
                && channels[2][count / 2] > channels[1][count / 2] + 8;
        int minimumContrast = bluePanel ? 50 : 100;
        int different = 0;
        for (int pixel : above) {
            if (pixel < 0) continue;
            int distance = Math.abs(((pixel >> 16) & 255) - channels[0][count / 2])
                    + Math.abs(((pixel >> 8) & 255) - channels[1][count / 2])
                    + Math.abs((pixel & 255) - channels[2][count / 2]);
            // Dunkelblauer Kasten vor blaugrünem Hintergrund (Intro) hat
            // weniger Kontrast als ein weißer Tierdialog vor der Spielwelt.
            if (distance >= minimumContrast) different++;
        }
        return different >= 5;
    }

    static boolean inDialogueRegion(float centerX, float centerY) {
        return centerX >= .21f && centerX <= .79f && centerY >= .74f && centerY <= .98f;
    }

    interface Luma {
        int at(int x, int y);
    }

    /**
     * Kompakte Porträtblase auf einer Karte mit ähnlicher Hintergrundfarbe.
     * Der dunkle Rand bildet zwei schmale Striche ober- und unterhalb der Zeile.
     */
    static boolean hasCompactOutline(int left, int top, int right, int bottom,
            int frameWidth, int frameHeight, Luma luma) {
        int width = right - left;
        int height = bottom - top;
        if (height < 4 || width < frameWidth * .10f || width > frameWidth * .48f
                || width < 7 * height || left < 0 || right > frameWidth) return false;

        int upper = findStroke(left, width, top, height, -1, frameHeight, luma);
        if (upper < 0) return false;
        int lower = findStroke(left, width, bottom, height, 1, frameHeight, luma);
        return lower >= 0 && lower - upper >= 2 * height && lower - upper <= 5 * height;
    }

    private static int findStroke(int left, int width, int start, int height, int direction,
            int frameHeight, Luma luma) {
        int spacing = Math.max(2, height / 4);
        for (int distance = spacing; distance <= 4 * height; distance++) {
            int y = start + distance * direction;
            if (y < spacing || y >= frameHeight - spacing) break;
            int votes = 0;
            for (int column = 1; column <= 7; column++) {
                int x = left + width * column / 8;
                int center = luma.at(x, y);
                int surrounding = Math.min(luma.at(x, y - spacing), luma.at(x, y + spacing));
                if (center >= 0 && surrounding - center >= 14) votes++;
            }
            if (votes >= 5) return y;
        }
        return -1;
    }

    static boolean isCharacterLabel(String name) {
        if (name == null || TextRules.isActionLabel(name) || SpeakerLabels.hasDialogueWords(name)) return false;
        String key = TextRules.normalize(name);
        return !key.isEmpty() && !key.matches(
                "(lagerbestand|inventar|beschreibung|informationen|aufgaben|aufgabe|ziel|ziele|material|materialien|herstellen|rezept|rezepte|karte|hilfe|einstellungen|status|gegenstände|sammlung|menü)");
    }

    private static boolean isPanel(int[] pixels, boolean blue) {
        int count = 0, left = 0, right = 0, top = 0, bottom = 0;
        int[] brightnessValues = new int[pixels.length];
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int pixel = pixels[row * COLUMNS + col];
                if (pixel < 0) continue; // außerhalb des Kamerabilds
                int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
                boolean matches = blue ? b > 70 && b > r + 24 && b > g + 8
                        : Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 30
                          && Math.min(r, Math.min(g, b)) >= 65;
                if (!matches) continue;
                count++;
                if (col < 2) left++;
                if (col > 6) right++;
                if (row == 0) top++;
                if (row == ROWS - 1) bottom++;
                int brightness = (r + g + b) / 3;
                brightnessValues[count - 1] = brightness;
            }
        }
        // Eine Fläche muss die Schrift auch seitlich und darüber/darunter umschließen.
        // Einzelne blaue Objekte oder graue Schrift sind kein Dialogkasten.
        java.util.Arrays.sort(brightnessValues, 0, count);
        int inliers = 0;
        if (count > 0) {
            int median = brightnessValues[count / 2];
            for (int i = 0; i < count; i++) {
                if (Math.abs(brightnessValues[i] - median) <= 35) inliers++;
            }
        }
        return count >= 29 && left >= 6 && right >= 6 && top >= 5 && bottom >= 5
                && (blue || inliers >= count * 0.80f);
    }
}
