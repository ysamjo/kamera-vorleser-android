package de.stegmann.kameravorleser;

import java.util.ArrayList;
import java.util.List;
import static de.stegmann.kameravorleser.StoryTextAssembler.Line;

/** Namensschild oberhalb der Dialogzeile, auch innerhalb der Story-ROI. */
final class SpeakerLabels {
    private SpeakerLabels() {}

    static boolean hasDialogueWords(String text) {
        return text.matches(".*[.!?…].*") || TextRules.normalize(text).matches(
                ".*\\b(ich|du|wir|ihr|das|ist|sind|hallo|guten|danke|bitte|komm)\\b.*")
                || TextRules.normalize(text).length() > 28;
    }

    static String above(List<Line> lines, Line story, int width, int height) {
        Line best = null;
        for (Line line : lines) {
            String name = TextRules.normalize(line.text);
            int gap = story.top - line.bottom;
            if (name.length() < 2 || name.length() > 28 || name.split(" ").length > 3
                    || line.right - line.left > width * .50f
                    || TextRules.isActionLabel(line.text)
                    || hasDialogueWords(line.text)
                    || gap < 0 || gap > height * .14f
                    || Math.abs(line.left - story.left) > width * .25f) continue;
            if (best == null || line.bottom > best.bottom
                    || (line.bottom == best.bottom && line.left > best.left)) best = line;
        }
        if (best == null) return null;
        // OCR zerlegt ein Namensschild gelegentlich in zwei Spalten oder Zeilen.
        List<Line> label = new ArrayList<>();
        label.add(best);
        for (Line line : lines) {
            if (line == best || !TextRules.normalize(line.text).equals("professor")) continue;
            boolean sameRow = Math.abs(line.centerY() - best.centerY()) < best.height() * .5f
                    && line.right <= best.left && best.left - line.right < width * .08f;
            boolean precedingRow = line.bottom <= best.top && best.top - line.bottom < best.height()
                    && Math.abs(line.left - best.left) < width * .1f;
            if (sameRow || precedingRow) label.add(line);
        }
        return StoryTextAssembler.assemble(label).replaceFirst("[：:]+$", "").trim();
    }

    static String inlineName(String text) {
        // Ein zusammengeführtes Professor-Namensschild darf auch ohne erkannte
        // separate Box nicht in der Ausgabe landen. Nur den bekannten Präfix nehmen.
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
                .matches("(?isu)^\\s*professor\\s+tangoloss(?:\\s|[:：]).*") ? "Professor Tangoloss" : null;
    }

    static String canonicalName(String name) {
        String normalized = TextRules.normalize(name);
        if (normalized.equals("tangoloss") || normalized.equals("professor tangoloss")) return "Professor Tangoloss";
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC).trim().replaceFirst("[：:]+$", "");
    }
}
