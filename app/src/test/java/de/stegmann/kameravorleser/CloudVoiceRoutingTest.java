package de.stegmann.kameravorleser;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class CloudVoiceRoutingTest {
    private final List<String> defaults = CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_DEFAULTS, true);

    @Test public void curatedRolesSurviveOccupiedVoicesAndReverseDiscoveryOrder() {
        List<String> speakers = Arrays.asList("Erzähler", "Pikachu", "Evoli", "Professor Tangoloss",
                "Scherox", "Schlappchu", "Glumanda", "Bisasam", "Mooslaxo", "DJ Rotom",
                "Plinfa", "Schiggy", "Felori", "Onix", "Gengar");
        VoiceAssignments assignments = new VoiceAssignments();
        // Unbekannte Figuren dürfen vorab jede Grundstimme belegen.
        for (int i = 0; i < 30; i++) CloudVoiceRouting.choose("Gast " + i, defaults, assignments, 0);
        List<String> reverse = new ArrayList<>(speakers);
        Collections.reverse(reverse);
        for (String speaker : reverse) {
            assertEquals(PokopiaVoiceCatalog.voiceFor(speaker),
                    CloudVoiceRouting.choose(speaker, defaults, assignments, 0));
        }
        assertEquals(15, new java.util.HashSet<>(speakers.stream()
                .map(PokopiaVoiceCatalog::voiceFor).collect(java.util.stream.Collectors.toList())).size());
    }

    @Test public void oldAutomaticAssignmentsMoveToNewRoleAndSurviveRestartAndPoolReorder() {
        VoiceAssignments assignments = new VoiceAssignments();
        assignments.put("Bisasam", defaults.get(4));
        String voice = CloudVoiceRouting.choose("Bisasam", defaults, assignments, 4);
        assertEquals(PokopiaVoiceCatalog.voiceFor("Bisasam"), voice);
        assertNotEquals(defaults.get(4), voice);
        VoiceAssignments restart = new VoiceAssignments();
        assignments.snapshot().forEach(restart::put);
        List<String> reordered = new ArrayList<>(defaults);
        Collections.reverse(reordered);
        assertEquals(voice, CloudVoiceRouting.choose("BISASAM", reordered, restart, 4));
    }

    @Test public void customPoolAndSavedVoiceKeepExistingBehavior() {
        List<String> custom = Arrays.asList("custom-a", "custom-b");
        VoiceAssignments assignments = new VoiceAssignments();
        assignments.put("Bisasam", "custom-b");
        assertEquals("custom-b", CloudVoiceRouting.choose("Bisasam", custom, assignments, 0));
        assertNull(CloudVoiceRouting.choose("Bisasam", Collections.emptyList(), assignments, 0));
        assertEquals("custom-b", assignments.snapshot().get("bisasam"));
        assertEquals(CloudVoiceProfiles.direction(.8f, 1f),
                CloudVoiceRouting.direction("Bisasam", custom, .8f, 1f));
    }

    @Test public void unknownSpeakersKeepSavedFallbackAndDoNotReceiveInventedDirections() {
        VoiceAssignments assignments = new VoiceAssignments();
        String first = CloudVoiceRouting.choose("Testgast", defaults, assignments, 4);
        assertTrue(defaults.contains(first));
        assertEquals(first, CloudVoiceRouting.choose("TESTGAST", defaults, assignments, 0));
        assertEquals(CloudVoiceProfiles.direction(1f, 1f),
                CloudVoiceRouting.direction("Testgast", defaults, 1f, 1f));
        assertEquals(PokopiaVoiceCatalog.directionFor("Mooslaxo"),
                CloudVoiceRouting.direction("Mooslaxo", defaults, 1f, 1f));
    }
}
