package de.stegmann.kameravorleser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import static org.junit.Assert.*;

public class StoryAudioIndexTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) assertTrue(parent.mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void loadsJsonlWithEscapesUmlautsAndFiltersUnsafeOrMissingPaths() throws IOException {
        File pack = temp.newFolder("pack");
        writeFile(new File(pack, "audio/dlg_1.ogg"), "ogg1");
        writeFile(new File(pack, "audio/dlg_2.ogg"), "ogg2");
        writeFile(new File(temp.getRoot(), "outside.ogg"), "secret");

        String nfdText = Normalizer.normalize(
                "Schön, dass du \"endlich\" hier bist!\nWir bauen alles auf.", Normalizer.Form.NFD);
        String jsonl = ""
                + "{\"id\":\"dlg_1\",\"speaker\":\"Professor Tangoloss\","
                + "\"text\":\"Schön, dass du \\\"endlich\\\" hier bist!\\nWir bauen alles auf.\","
                + "\"first_line\":\"Schön, dass du \\\"endlich\\\" hier bist!\","
                + "\"audio\":\"audio/dlg_1.ogg\",\"confidence\":0.96}\n"
                + "{\"id\":\"dlg_2\",\"speaker\":\"Erzähler\","
                + "\"text\":\"Willkommen in Pokopia!\",\"first_line\":\"Willkommen in Pokopia!\","
                + "\"audio_file\":\"audio/dlg_2.ogg\"}\n"
                + "{\"id\":\"missing\",\"speaker\":\"Pikachu\",\"text\":\"Fehlende Datei\",\"first_line\":\"Fehlende Datei\",\"audio\":\"audio/missing.ogg\"}\n"
                + "{\"id\":\"traversal\",\"speaker\":\"Pikachu\",\"text\":\"Pfad-Ausbruch\",\"first_line\":\"Pfad-Ausbruch\",\"audio\":\"../outside.ogg\"}\n"
                + "{\"id\":\"abs\",\"speaker\":\"Pikachu\",\"text\":\"Absolut\",\"first_line\":\"Absolut\",\"audio\":\"/etc/passwd\"}\n";
        writeFile(new File(pack, "index.jsonl"), jsonl);

        StoryAudioIndex index = StoryAudioIndex.load(pack);
        assertEquals(2, index.size());

        StoryAudioIndex.Match match = index.matchFull("Professor Tangoloss", nfdText, false);
        assertNotNull(match);
        assertFalse(match.earlyPrefix);
        assertEquals("dlg_1", match.entry.id);
        assertEquals(new File(pack, "audio/dlg_1.ogg"), match.audioFile);

        assertNull(index.matchFull("Pikachu", "Pfad-Ausbruch", false));
        assertNull(index.matchFull("Pikachu", "Fehlende Datei", false));
    }

    @Test
    public void matchesFullWithAndWithoutSpeakerAndToleratesMinorOcrNoise() throws IOException {
        File pack = temp.newFolder("pack-full");
        writeFile(new File(pack, "audio/a.ogg"), "a");
        writeFile(new File(pack, "audio/b.ogg"), "b");
        String jsonl = ""
                + "{\"id\":\"a\",\"speaker\":\"Bisasam\",\"text\":\"Der Boden hier ist pupstrocken und das Gras ist total verwelkt.\",\"first_line\":\"Der Boden hier ist pupstrocken\",\"audio\":\"audio/a.ogg\"}\n"
                + "{\"id\":\"b\",\"speaker\":\"Schiggy\",\"text\":\"Hier ist Gras gewachsen!\",\"first_line\":\"Hier ist Gras gewachsen!\",\"audio\":\"audio/b.ogg\"}\n";
        writeFile(new File(pack, "index.jsonl"), jsonl);

        StoryAudioIndex index = StoryAudioIndex.load(pack);
        // Unscharfer OCR-Treffer („pupstrocken“ -> „pupstr0cken“)
        StoryAudioIndex.Match fuzzy = index.matchFull("Bisasam",
                "Der Boden hier ist pupstr0cken und das Gras ist total verwelkt.", false);
        assertNotNull(fuzzy);
        assertEquals("a", fuzzy.entry.id);

        // Unbekannter Sprecher findet eindeutigen Satz
        StoryAudioIndex.Match uncertain = index.matchFull("Erzähler", "Hier ist Gras gewachsen!", true);
        assertNotNull(uncertain);
        assertEquals("b", uncertain.entry.id);
    }

    @Test
    public void earlyPrefixEnforcesQualificationAndUniqueness() throws IOException {
        File pack = temp.newFolder("pack-prefix");
        writeFile(new File(pack, "audio/1.ogg"), "1");
        writeFile(new File(pack, "audio/2.ogg"), "2");
        writeFile(new File(pack, "audio/3.ogg"), "3");
        writeFile(new File(pack, "audio/4.ogg"), "4");
        writeFile(new File(pack, "audio/5.ogg"), "5");

        String jsonl = ""
                // Eindeutige qualifizierende Zeile 1
                + "{\"id\":\"1\",\"speaker\":\"Erzähler\",\"text\":\"Guten Morgen! Da hat aber jemand tief und fest geschlafen!\",\"first_line\":\"Guten Morgen! Da hat aber jemand tief und fest\",\"audio\":\"audio/1.ogg\"}\n"
                // Zu kurz (< 25 Zeichen und < 4 Wörter)
                + "{\"id\":\"2\",\"speaker\":\"Professor Tangoloss\",\"text\":\"W-Was?! Ist da etwa jemand?!\",\"first_line\":\"W-Was?!\",\"audio\":\"audio/2.ogg\"}\n"
                // Mehrdeutiger Anfang beim selben Sprecher
                + "{\"id\":\"3\",\"speaker\":\"Professor Tangoloss\",\"text\":\"Oh? Was ist denn das für ein Geräusch? Irgendetwas piept...\",\"first_line\":\"Oh? Was ist denn das für ein Geräusch?\",\"audio\":\"audio/3.ogg\"}\n"
                + "{\"id\":\"4\",\"speaker\":\"Professor Tangoloss\",\"text\":\"Oh? Was ist denn das für ein Geräusch? Ein Feuer brennt!\",\"first_line\":\"Oh? Was ist denn das für ein Geräusch?\",\"audio\":\"audio/4.ogg\"}\n"
                // Gleicher Anfang bei anderem Sprecher vs. „Erzähler“-Basis („1“ eindeutig, aber testen wir Kollision zweier Sprecher):
                + "{\"id\":\"5\",\"speaker\":\"Schiggy\",\"text\":\"Das liegt doch bestimmt daran dass Wasser fehlt!\",\"first_line\":\"Das liegt doch bestimmt daran\",\"audio\":\"audio/5.ogg\"}\n";
        writeFile(new File(pack, "index.jsonl"), jsonl);

        StoryAudioIndex index = StoryAudioIndex.load(pack);

        StoryAudioIndex.Match ok = index.matchEarlyPrefix("Erzähler",
                "Guten Morgen! Da hat aber jemand tief und fest", false);
        assertNotNull(ok);
        assertTrue(ok.earlyPrefix);
        assertEquals("1", ok.entry.id);

        assertNull(index.matchEarlyPrefix("Professor Tangoloss", "W-Was?!", false));
        assertNull(index.matchEarlyPrefix("Professor Tangoloss",
                "Oh? Was ist denn das für ein Geräusch?", false));
    }

    @Test
    public void earlyPrefixAllowsKnownSpeakerWhenAnotherSpeakerSharesFirstLineButBlocksUncertain() throws IOException {
        File pack = temp.newFolder("pack-shared");
        writeFile(new File(pack, "audio/p.ogg"), "p");
        writeFile(new File(pack, "audio/s.ogg"), "s");
        String jsonl = ""
                + "{\"id\":\"p\",\"speaker\":\"Pikachu\",\"text\":\"Wir bauen heute eine große Brücke über den Fluss!\",\"first_line\":\"Wir bauen heute eine große Brücke\",\"audio\":\"audio/p.ogg\"}\n"
                + "{\"id\":\"s\",\"speaker\":\"Schiggy\",\"text\":\"Wir bauen heute eine große Brücke für alle Freunde!\",\"first_line\":\"Wir bauen heute eine große Brücke\",\"audio\":\"audio/s.ogg\"}\n";
        writeFile(new File(pack, "index.jsonl"), jsonl);

        StoryAudioIndex index = StoryAudioIndex.load(pack);

        StoryAudioIndex.Match known = index.matchEarlyPrefix("Pikachu",
                "Wir bauen heute eine große Brücke", false);
        assertNotNull(known);
        assertEquals("p", known.entry.id);

        // Ohne sicheres Namensschild wäre der Anfang mehrdeutig -> kein Frühstart
        assertNull(index.matchEarlyPrefix("Erzähler",
                "Wir bauen heute eine große Brücke", true));
    }

    @Test
    public void registeringFullTargetTextInHistoryPreventsDuplicateSpeechAfterEarlyStart() throws IOException {
        File pack = temp.newFolder("pack-history");
        writeFile(new File(pack, "audio/1.ogg"), "1");
        String fullText = "Guten Morgen! Da hat aber jemand tief und fest geschlafen, wie? Zeit zum Aufwachen!";
        writeFile(new File(pack, "index.jsonl"),
                "{\"id\":\"1\",\"speaker\":\"Erzähler\",\"text\":\"" + fullText
                        + "\",\"first_line\":\"Guten Morgen! Da hat aber jemand tief und fest\",\"audio\":\"audio/1.ogg\"}\n");

        StoryAudioIndex index = StoryAudioIndex.load(pack);
        StoryAudioIndex.Match early = index.matchEarlyPrefix("Erzähler",
                "Guten Morgen! Da hat aber jemand tief und fest", false);
        assertNotNull(early);

        DialogueHistory history = new DialogueHistory(20);
        String key = DialogueHistory.key("Erzähler", early.entry.textNorm);
        history.begin(key);

        // Während Zeile 2 noch tippt und wenn der komplette Text stabil wird: kein zweiter Start!
        assertTrue(history.wasSpoken("Erzähler", "guten morgen da hat aber jemand tief und fest geschlafen"));
        assertTrue(history.wasSpoken("Erzähler", TextRules.normalize(fullText)));
        assertTrue(history.finish(key, true));
        assertTrue(history.wasSpoken("Erzähler", TextRules.normalize(fullText)));
    }

    @Test
    public void prefixStabilityRequiresTwoHitsAndResetsOnChangeOrGap() {
        StoryAudioIndex.PrefixStability stability = new StoryAudioIndex.PrefixStability();
        assertFalse(stability.accept("Guten Morgen! Da hat aber jemand", 100L));
        assertTrue(stability.accept("Guten Morgen! Da hat aber jemand", 220L));

        // Textwechsel setzt auf 1 zurück
        assertFalse(stability.accept("Andere Zeile fängt jetzt an!", 340L));
        assertTrue(stability.accept("Andere Zeile fängt jetzt an!", 460L));

        // Lücke > 2000 ms setzt ebenfalls zurück
        assertFalse(stability.accept("Andere Zeile fängt jetzt an!", 2600L));
        assertTrue(stability.accept("Andere Zeile fängt jetzt an!", 2720L));

        stability.reset();
        assertFalse(stability.accept("Andere Zeile fängt jetzt an!", 2840L));
    }
}
