package de.stegmann.kameravorleser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lokaler Story-Pack-Index mit exaktem, unscharfem und eindeutigem Zeile-1-Matching.
 * Reines Java ohne Android-Abhängigkeiten; vollständig per JVM-Unit-Tests prüfbar.
 */
final class StoryAudioIndex {
    private static final String SEPARATOR = "\u001f";
    private static final int MIN_EARLY_PREFIX_CHARS = 25;
    private static final int MIN_EARLY_PREFIX_WORDS = 4;

    static final class Entry {
        final String id;
        final String speaker;
        final String speakerNorm;
        final String text;
        final String textNorm;
        final String firstLine;
        final String firstLineNorm;
        final String audioPath;

        Entry(String id, String speaker, String text, String firstLine, String audioPath) {
            this.id = id == null ? "" : id;
            this.speaker = speaker == null || speaker.trim().isEmpty() ? "Erzähler" : speaker.trim();
            this.speakerNorm = TextRules.normalize(this.speaker);
            this.text = text == null ? "" : text;
            this.textNorm = TextRules.normalize(this.text);
            this.firstLine = firstLine == null ? "" : firstLine;
            this.firstLineNorm = TextRules.normalize(this.firstLine);
            this.audioPath = audioPath == null ? "" : audioPath;
        }
    }

    static final class Match {
        final Entry entry;
        final File audioFile;
        final boolean earlyPrefix;

        Match(Entry entry, File audioFile, boolean earlyPrefix) {
            this.entry = entry;
            this.audioFile = audioFile;
            this.earlyPrefix = earlyPrefix;
        }
    }

    /** Verlangt zwei aufeinanderfolgende gleiche Zeilenanfänge vor einem Frühstart. */
    static final class PrefixStability {
        private static final long MAX_GAP_MS = 2000L;
        private String key = "";
        private int hits;
        private long seenAt;

        boolean accept(String firstLineText, long now) {
            String next = firstLineText == null ? "" : TextRules.normalize(firstLineText);
            if (next.isEmpty()) {
                reset();
                return false;
            }
            if (!next.equals(key) || now - seenAt > MAX_GAP_MS) {
                key = next;
                hits = 1;
            } else {
                hits++;
            }
            seenAt = now;
            return hits >= 2;
        }

        void reset() {
            key = "";
            hits = 0;
            seenAt = 0L;
        }
    }

    private final File packDir;
    private final List<Entry> allEntries;
    private final Map<String, Entry> exactBySpeakerAndText = new HashMap<>();
    private final Map<String, List<Entry>> exactByText = new HashMap<>();
    private final Map<String, List<Entry>> bySpeakerAndFirstLine = new HashMap<>();
    private final Map<String, List<Entry>> byFirstLine = new HashMap<>();
    private final Map<String, List<Entry>> entriesBySpeaker = new HashMap<>();

    private StoryAudioIndex(File packDir, List<Entry> entries) {
        this.packDir = packDir;
        this.allEntries = Collections.unmodifiableList(new ArrayList<>(entries));
        for (Entry entry : this.allEntries) {
            if (entry.textNorm.isEmpty()) continue;
            exactBySpeakerAndText.putIfAbsent(entry.speakerNorm + SEPARATOR + entry.textNorm, entry);
            addToList(exactByText, entry.textNorm, entry);
            addToList(entriesBySpeaker, entry.speakerNorm, entry);
            if (!entry.firstLineNorm.isEmpty()) {
                addToList(bySpeakerAndFirstLine, entry.speakerNorm + SEPARATOR + entry.firstLineNorm, entry);
                addToList(byFirstLine, entry.firstLineNorm, entry);
            }
        }
    }

    static StoryAudioIndex empty() {
        return new StoryAudioIndex(null, Collections.emptyList());
    }

    static StoryAudioIndex fromEntries(File packDir, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return empty();
        return new StoryAudioIndex(packDir, entries);
    }

    static StoryAudioIndex load(File packDir) {
        if (packDir == null || !packDir.isDirectory()) return empty();
        File indexFile = new File(packDir, "index.jsonl");
        if (!indexFile.isFile()) return empty();

        List<Entry> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(indexFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                Map<String, String> fields = parseFlatJsonObject(trimmed);
                String text = fields.get("text");
                String audioPath = fields.containsKey("audio") ? fields.get("audio") : fields.get("audio_file");
                if (text == null || TextRules.normalize(text).isEmpty() || !isSafeRelativePath(audioPath)) {
                    continue;
                }
                File audioFile = new File(packDir, audioPath);
                if (!audioFile.isFile()) continue;
                loaded.add(new Entry(
                        fields.get("id"),
                        fields.get("speaker"),
                        text,
                        fields.get("first_line"),
                        audioPath
                ));
            }
        } catch (IOException ignored) {
            return empty();
        }
        return loaded.isEmpty() ? empty() : new StoryAudioIndex(packDir, loaded);
    }

    int size() {
        return allEntries.size();
    }

    Match matchFull(String speaker, String visibleText, boolean speakerUncertain) {
        if (packDir == null || allEntries.isEmpty() || visibleText == null) return null;
        String textNorm = TextRules.normalize(visibleText);
        if (textNorm.isEmpty()) return null;
        String speakerNorm = TextRules.normalize(speaker == null ? "" : speaker);

        Entry exact = exactBySpeakerAndText.get(speakerNorm + SEPARATOR + textNorm);
        if (exact != null) return matchFor(exact, false);

        if (speakerUncertain) {
            List<Entry> byText = exactByText.get(textNorm);
            if (byText != null && byText.size() == 1) {
                return matchFor(byText.get(0), false);
            }
        }

        List<Entry> candidates = speakerUncertain ? allEntries : entriesBySpeaker.get(speakerNorm);
        if (candidates == null || candidates.isEmpty()) return null;

        Entry best = null;
        int bestDistance = Integer.MAX_VALUE;
        int matchCount = 0;
        for (Entry candidate : candidates) {
            if (!TextRules.similarEnough(candidate.textNorm, textNorm)) continue;
            matchCount++;
            int distance = TextRules.levenshtein(candidate.textNorm, textNorm);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best == null || (speakerUncertain && matchCount != 1)) return null;
        return matchFor(best, false);
    }

    Match matchEarlyPrefix(String speaker, String firstLineText, boolean speakerUncertain) {
        if (packDir == null || allEntries.isEmpty() || firstLineText == null) return null;
        String prefixNorm = TextRules.normalize(firstLineText);
        if (!isQualifyingPrefix(prefixNorm)) return null;

        if (!speakerUncertain) {
            String speakerNorm = TextRules.normalize(speaker == null ? "" : speaker);
            List<Entry> exact = bySpeakerAndFirstLine.get(speakerNorm + SEPARATOR + prefixNorm);
            if (exact != null) {
                return exact.size() == 1 ? matchFor(exact.get(0), true) : null;
            }
            return uniqueFuzzyPrefix(entriesBySpeaker.get(speakerNorm), prefixNorm);
        }

        List<Entry> exact = byFirstLine.get(prefixNorm);
        if (exact != null) {
            return exact.size() == 1 ? matchFor(exact.get(0), true) : null;
        }
        return uniqueFuzzyPrefix(allEntries, prefixNorm);
    }

    private Match uniqueFuzzyPrefix(List<Entry> candidates, String prefixNorm) {
        if (candidates == null || candidates.isEmpty()) return null;
        Entry unique = null;
        for (Entry candidate : candidates) {
            if (candidate.firstLineNorm.isEmpty()) continue;
            if (!TextRules.similarEnough(candidate.firstLineNorm, prefixNorm)) continue;
            if (unique != null) return null;
            unique = candidate;
        }
        return unique == null ? null : matchFor(unique, true);
    }

    private Match matchFor(Entry entry, boolean earlyPrefix) {
        return new Match(entry, new File(packDir, entry.audioPath), earlyPrefix);
    }

    private static boolean isQualifyingPrefix(String prefixNorm) {
        if (prefixNorm.isEmpty()) return false;
        if (prefixNorm.length() >= MIN_EARLY_PREFIX_CHARS) return true;
        return prefixNorm.split(" ").length >= MIN_EARLY_PREFIX_WORDS;
    }

    private static boolean isSafeRelativePath(String path) {
        if (path == null || path.trim().isEmpty()) return false;
        if (path.startsWith("/") || path.startsWith("\\") || path.contains(":")) return false;
        for (String segment : path.split("[/\\\\]")) {
            if (segment.equals("..")) return false;
        }
        return true;
    }

    private static void addToList(Map<String, List<Entry>> map, String key, Entry entry) {
        List<Entry> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        list.add(entry);
    }

    /** Schlanker reiner Java-JSON-Objekt-Parser für flache JSONL-Zeilen (ohne org.json). */
    static Map<String, String> parseFlatJsonObject(String line) {
        Map<String, String> map = new HashMap<>();
        if (line == null) return map;
        int len = line.length();
        int i = skipWs(line, 0, len);
        if (i >= len || line.charAt(i) != '{') return map;
        i = skipWs(line, i + 1, len);
        while (i < len) {
            char c = line.charAt(i);
            if (c == '}') break;
            if (c == ',') {
                i = skipWs(line, i + 1, len);
                continue;
            }
            if (c != '"') break;
            StringBuilder keyBuf = new StringBuilder();
            i = readJsonString(line, i, keyBuf);
            if (i < 0) break;
            i = skipWs(line, i, len);
            if (i >= len || line.charAt(i) != ':') break;
            i = skipWs(line, i + 1, len);
            if (i >= len) break;
            char vc = line.charAt(i);
            if (vc == '"') {
                StringBuilder valBuf = new StringBuilder();
                i = readJsonString(line, i, valBuf);
                if (i < 0) break;
                map.put(keyBuf.toString(), valBuf.toString());
            } else {
                i = skipJsonValue(line, i, len);
            }
            i = skipWs(line, i, len);
        }
        return map;
    }

    private static int skipWs(String s, int idx, int len) {
        while (idx < len && Character.isWhitespace(s.charAt(idx))) idx++;
        return idx;
    }

    private static int readJsonString(String s, int startQuote, StringBuilder out) {
        int len = s.length();
        int i = startQuote + 1;
        while (i < len) {
            char c = s.charAt(i++);
            if (c == '"') return i;
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i >= len) return -1;
            char esc = s.charAt(i++);
            switch (esc) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (i + 4 > len) return -1;
                    try {
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ignored) {
                        return -1;
                    }
                    i += 4;
                    break;
                default:
                    out.append(esc);
            }
        }
        return -1;
    }

    private static int skipJsonValue(String s, int idx, int len) {
        char start = s.charAt(idx);
        if (start == '{' || start == '[') {
            int depth = 0;
            int i = idx;
            while (i < len) {
                char c = s.charAt(i);
                if (c == '"') {
                    int next = readJsonString(s, i, new StringBuilder());
                    if (next < 0) return len;
                    i = next;
                    continue;
                }
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') {
                    depth--;
                    if (depth == 0) return i + 1;
                }
                i++;
            }
            return len;
        }
        int i = idx;
        while (i < len) {
            char c = s.charAt(i);
            if (c == ',' || c == '}') return i;
            i++;
        }
        return len;
    }
}
