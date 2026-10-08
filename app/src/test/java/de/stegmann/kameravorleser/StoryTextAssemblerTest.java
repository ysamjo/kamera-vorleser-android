package de.stegmann.kameravorleser;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StoryTextAssemblerTest {
    private StoryTextAssembler.Line line(String text, int x, int y) {
        return new StoryTextAssembler.Line(text, x, y, x + 60, y + 20);
    }

    @Test public void geometricOrderSurvivesFortyPermutationsAndSmallOffsets() {
        List<StoryTextAssembler.Line> lines = new ArrayList<>(Arrays.asList(
                line("Hallo", 0, 12), line("du!", 90, 10),
                line("Komm", 0, 40), line("mit.", 90, 43)));
        for (int seed = 0; seed < 40; seed++) {
            Collections.shuffle(lines, new Random(seed));
            assertEquals("Permutation " + seed, "Hallo du! Komm mit.", StoryTextAssembler.assemble(lines));
        }
    }

    @Test public void joinsHyphenationOnlyAcrossRows() {
        assertEquals("Pokéball", StoryTextAssembler.assemble(Arrays.asList(line("ball", 0, 40), line("Poké-", 0, 10))));
        assertEquals("Poké- ball", StoryTextAssembler.assemble(Arrays.asList(line("ball", 90, 10), line("Poké-", 0, 10))));
        assertEquals("Hallo - du!", StoryTextAssembler.assemble(Arrays.asList(line("Hallo -", 0, 10), line("du!", 0, 40))));
    }

    @Test public void ignoresInvalidLinesAndPreservesPunctuation() {
        assertEquals("Hallo …", StoryTextAssembler.assemble(Arrays.asList(null, line(null, 0, 0),
                line("  ", 0, 0), new StoryTextAssembler.Line("kaputt", 0, 0, 0, 0), line(" Hallo … ", 0, 10))));
        assertEquals("", StoryTextAssembler.assemble(null));
        assertEquals("", StoryTextAssembler.assemble(Collections.emptyList()));
    }
}
