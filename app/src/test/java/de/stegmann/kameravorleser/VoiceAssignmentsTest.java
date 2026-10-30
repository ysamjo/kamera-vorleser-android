package de.stegmann.kameravorleser;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class VoiceAssignmentsTest {
    private final List<String> voices = Arrays.asList("de-DE-a", "de-DE-b", "de-DE-c");

    @Test public void charactersUseDifferentRealVoicesUntilExhausted() {
        VoiceAssignments assignments = new VoiceAssignments();
        assertEquals("de-DE-a", assignments.choose("Professor Tangoloss", voices));
        assertEquals("de-DE-b", assignments.choose("Evoli", voices));
        assertEquals("de-DE-c", assignments.choose("Pikachu", voices));
        assertEquals("de-DE-a", assignments.choose("Professor Tangoloss", voices));
        assertNotNull(assignments.choose("Bisasam", voices));
    }

    @Test public void manualVoiceSurvivesRestartAndReorderedVoiceList() {
        VoiceAssignments first = new VoiceAssignments();
        first.put("Flabe\u0301be\u0301", "de-DE-c");
        VoiceAssignments restart = new VoiceAssignments();
        first.snapshot().forEach(restart::put);
        assertEquals("de-DE-c", restart.choose("Flabébé", Arrays.asList("de-DE-c", "de-DE-a")));
    }

    @Test public void removedVoiceHasAvailableFallbackAndEmptyListDoesNotEraseSelection() {
        VoiceAssignments assignments = new VoiceAssignments();
        assignments.put("Evoli", "removed");
        assertNull(assignments.choose("Evoli", Collections.emptyList()));
        assertEquals("removed", assignments.snapshot().get("evoli"));
        assertEquals("de-DE-a", assignments.choose("Evoli", voices));
    }
}
