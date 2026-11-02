package de.stegmann.kameravorleser;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class PokopiaVoiceCatalogTest {
    private final List<String> voices = CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_DEFAULTS, true);

    @Test public void fifteenRolesHaveFifteenDistinctVoices() {
        String[] speakers = {"Erzähler", "Pikachu", "Evoli", "Professor Tangoloss", "Scherox",
                "Schlappchu", "Glumanda", "Bisasam", "Mooslaxo", "DJ Rotom", "Plinfa",
                "Schiggy", "Felori", "Onix", "Gengar"};
        HashSet<String> assigned = new HashSet<>();
        for (int slot = 0; slot < speakers.length; slot++) {
            assertEquals(speakers[slot], voices.get(slot), PokopiaVoiceCatalog.voiceFor(speakers[slot]));
            assertNotNull(PokopiaVoiceCatalog.directionFor(speakers[slot]));
            assigned.add(PokopiaVoiceCatalog.voiceFor(speakers[slot]));
        }
        assertEquals(15, assigned.size());
        assertEquals(5, CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS, true).size());
        assertEquals(CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS, true), voices.subList(0, 5));
    }

    @Test public void aliasesPrefixesSuffixesAndComposedUmlautsKeepTheirRoles() {
        assertSameRole("Schlappchu", "PEAKYCHU");
        assertSameRole("Professor Tangoloss", "Professor Tangrowth");
        assertSameRole("Bisasam", "Bulbasaur");
        assertSameRole("Glumanda", "Charmander");
        assertSameRole("Schiggy", "Squirtle");
        assertSameRole("Mooslaxo", "Mosslax");
        assertSameRole("Evoli", "Eevee");
        assertSameRole("Felori", "Sprigatito");
        assertSameRole("Plinfa", "Piplup");
        assertSameRole("Scherox", "Machop");
        assertSameRole("Rotom", "DJ Rotom");
        assertSameRole("Gengar", "Meister Farbeagle");
        assertSameRole("Unratütox", "Unratu\u0308tox");
        assertSameRole("Onix", "Gigantisches Onix");
        assertSameRole("Bisasam", "Bisasam der Gärtner");
        assertSameRole("Erzähler", "Erza\u0308hler");
    }

    @Test public void namesMatchWholeTokensAndUnknownCharactersRemainAvailableForFallback() {
        assertNotEquals(PokopiaVoiceCatalog.voiceFor("Mew"), PokopiaVoiceCatalog.voiceFor("Mewtwo"));
        assertNotEquals(PokopiaVoiceCatalog.voiceFor("Pikachu"), PokopiaVoiceCatalog.voiceFor("Schlappchu"));
        assertNull(PokopiaVoiceCatalog.voiceFor("Superpikachu"));
        assertNull(PokopiaVoiceCatalog.voiceFor("Mewtwos"));
        assertNull(PokopiaVoiceCatalog.voiceFor("Unbekanntes Pokémon"));
        assertNull(PokopiaVoiceCatalog.directionFor("Unbekanntes Pokémon"));
        assertEquals(voices.get(0), PokopiaVoiceCatalog.voiceFor(""));
    }

    @Test public void poolDetectionIgnoresOrderButDoesNotEnableRolesForCustomPools() {
        List<String> reversed = new ArrayList<>(voices);
        Collections.reverse(reversed);
        assertTrue(PokopiaVoiceCatalog.isDefaultPool(reversed));
        assertFalse(PokopiaVoiceCatalog.isDefaultPool(voices.subList(0, 14)));
        reversed.add("custom");
        assertFalse(PokopiaVoiceCatalog.isDefaultPool(reversed));
        assertFalse(PokopiaVoiceCatalog.isDefaultPool(Collections.emptyList()));
        assertFalse(PokopiaVoiceCatalog.isDefaultPool(null));
        // Eine erkannte Rolle liefert dieselbe ID unabhängig von vorherigen Abfragen.
        String professor = PokopiaVoiceCatalog.voiceFor("Professor Tangoloss");
        for (String speaker : Arrays.asList("Rotom", "Unbekannt", "Mew", "Bisasam")) {
            PokopiaVoiceCatalog.voiceFor(speaker);
        }
        assertEquals(professor, PokopiaVoiceCatalog.voiceFor("Professor Tangoloss"));
    }

    @Test public void migrationOnlyUpgradesMissingOrLegacyDefaults() {
        assertTrue(PokopiaVoiceCatalog.shouldUpgrade(null));
        List<String> legacy = CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS, true);
        Collections.reverse(legacy);
        assertTrue(PokopiaVoiceCatalog.shouldUpgrade("  " + String.join(" , ", legacy) + "  "));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade(CloudVoiceProfiles.FISH_DEFAULTS));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade(""));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade("  "));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade("own-a,own-b"));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS + ",own"));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade("https://fish.audio/voice"));
        assertFalse(PokopiaVoiceCatalog.shouldUpgrade(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS + ","));
    }

    private void assertSameRole(String left, String right) {
        assertNotNull(left, PokopiaVoiceCatalog.voiceFor(left));
        assertEquals(right, PokopiaVoiceCatalog.voiceFor(left), PokopiaVoiceCatalog.voiceFor(right));
        assertEquals(right, PokopiaVoiceCatalog.directionFor(left), PokopiaVoiceCatalog.directionFor(right));
    }
}
