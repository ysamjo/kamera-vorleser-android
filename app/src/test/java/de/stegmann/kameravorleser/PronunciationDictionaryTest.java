package de.stegmann.kameravorleser;
import org.junit.Test;
import static org.junit.Assert.*;
public class PronunciationDictionaryTest {
    @Test public void replacesNamesWithUnicodeCaseAndPunctuation() {
        assertEquals("Piekatschu, Pokemohn! Ho Oh?", PronunciationDictionary.apply("PIKACHU, POKÉMON! Ho-Oh?"));
        assertEquals("Ewoli und Glurahk", PronunciationDictionary.apply("Evoli und Glurak"));
    }
    @Test public void germanUmlautsRemainGermanAndDecomposedOcrIsNormalized() {
        assertEquals("Schön, dass du zurück bist! Äpfel, Öl, Übermut und Straße.",
                PronunciationDictionary.apply("Schön, dass du zurück bist! Äpfel, Öl, Übermut und Straße."));
    }

    @Test public void doesNotReplaceInsideOtherWords() {
        String original = "Pokémonwelt Äevoli evoliÄ evoli2 _evoli";
        assertEquals(original, PronunciationDictionary.apply(original));
    }
    @Test public void preservesOrdinarySentencesAndOnlyConvertsPauseMarks() {
        assertEquals("Hallo, du! Komm; bitte.", PronunciationDictionary.addNaturalPauses("Hallo, du! Komm; bitte."));
        assertEquals("Hallo – du …", PronunciationDictionary.addNaturalPauses("Hallo - du ..."));
        assertEquals("", PronunciationDictionary.apply(""));
    }
}
