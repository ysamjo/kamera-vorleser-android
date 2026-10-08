package de.stegmann.kameravorleser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reine Geometrie- und Textlogik; keine Android- oder UI-Abhängigkeit. */
final class StoryTextAssembler {
    static final class Line {
        final String text;
        final int left, top, right, bottom;

        Line(String text, int left, int top, int right, int bottom) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int centerY() { return (top + bottom) / 2; }
        int height() { return bottom - top; }
    }

    static String assemble(List<Line> lines) {
        if (lines == null) return "";
        List<Line> valid = new ArrayList<>();
        for (Line line : lines) {
            if (line != null && line.text != null && !line.text.trim().isEmpty()
                    && line.right > line.left && line.bottom > line.top) valid.add(line);
        }
        valid.sort(Comparator.comparingInt((Line l) -> l.top)
                .thenComparingInt(l -> l.left).thenComparingInt(l -> l.bottom)
                .thenComparingInt(l -> l.right).thenComparing(l -> l.text));
        List<List<Line>> rows = new ArrayList<>();
        for (Line line : valid) {
            List<Line> target = null;
            for (List<Line> row : rows) {
                Line anchor = row.get(0);
                int averageHeight = Math.max(1, (line.height() + anchor.height()) / 2);
                if (Math.abs(line.centerY() - anchor.centerY()) < averageHeight * 0.5f) {
                    target = row;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                rows.add(target);
            }
            target.add(line);
        }
        StringBuilder result = new StringBuilder();
        for (List<Line> row : rows) {
            row.sort(Comparator.comparingInt((Line l) -> l.left)
                    .thenComparingInt(l -> l.top).thenComparing(l -> l.text));
            StringBuilder rowText = new StringBuilder();
            for (Line line : row) {
                if (rowText.length() > 0) rowText.append(' ');
                rowText.append(line.text.trim());
            }
            if (result.length() > 0) {
                // Silbentrennung nur zwischen visuellen Zeilen zusammenziehen.
                if (result.charAt(result.length() - 1) == '-'
                        && (result.length() < 2 || result.charAt(result.length() - 2) != ' ')) {
                    result.setLength(result.length() - 1);
                } else result.append(' ');
            }
            result.append(rowText);
        }
        return result.toString();
    }
}
