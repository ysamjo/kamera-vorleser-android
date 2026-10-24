package de.stegmann.kameravorleser;

import java.text.Normalizer;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Lokales Wörterbuch; verändert ausschließlich den gesprochenen Text. */
final class PronunciationDictionary {
    private static final Map<String, String> pronunciationDict = createDictionary();
    private static Map<String, String> createDictionary() {
        Map<String, String> dict = new LinkedHashMap<>();

        // Basis-Begriffe
        dict.put("pokémon", "Pokemohn");
        dict.put("pokemon", "Pokemohn");
        dict.put("pokéball", "Pokeball");
        dict.put("pokeball", "Pokeball");
        dict.put("pokédex", "Pokedex");
        dict.put("pokedex", "Pokedex");
        dict.put("pokopia", "Pokohpia");

        // Starter Pokémon
        dict.put("bisasam", "Biesasam");
        dict.put("bisaknosp", "Biesaknosp");
        dict.put("bisaflor", "Biesaflor");
        dict.put("glumanda", "Glumannda");
        dict.put("glutexo", "Glutäxo");
        dict.put("glurak", "Glurahk");
        dict.put("schiggy", "Schiggie");
        dict.put("schillok", "Schillokk");
        dict.put("turtok", "Turtokk");
        dict.put("pikachu", "Piekatschu");
        dict.put("raichu", "Raichu");
        dict.put("pichu", "Piechu");

        // Häufige Pokémon
        dict.put("evoli", "Ewoli");
        dict.put("aquana", "Akwana");
        dict.put("blitza", "Blittsa");
        dict.put("flamara", "Flamara");
        dict.put("psiana", "Psieana");
        dict.put("nachtara", "Nachtara");
        dict.put("folipurba", "Folipurba");
        dict.put("glaziola", "Glatsieola");
        dict.put("feelinara", "Feelienara");
        dict.put("mewtu", "Mjutu");
        dict.put("mew", "Mju");
        dict.put("relaxo", "Rälaxo");
        dict.put("mooslaxo", "Mohslaxo");
        dict.put("dragoran", "Dragohran");
        dict.put("gengar", "Gängar");

        // Legendäre
        dict.put("arktos", "Arktohs");
        dict.put("zapdos", "Zapdohs");
        dict.put("lavados", "Lavadohs");
        dict.put("lugia", "Lugia");
        dict.put("ho-oh", "Ho Oh");
        dict.put("celebi", "Tselebi");
        dict.put("groudon", "Graudon");
        dict.put("kyogre", "Kaiogre");
        dict.put("rayquaza", "Räkwasa");
        dict.put("dialga", "Diealga");
        dict.put("palkia", "Palkia");
        dict.put("giratina", "Gieratina");

        // Figuren
        dict.put("tangoloss", "Tangoloss");
        dict.put("rotom", "Rohtom");
        dict.put("porygon", "Poriegon");

        return dict;
    }

    static String apply(String text) {
        // OCR kann Umlaute als Basisbuchstabe + kombinierendes Zeichen liefern.
        // NFC erhält ä, ö, ü und ß für die deutsche TTS-Engine.
        String result = Normalizer.normalize(text, Normalizer.Form.NFC);
        for (Map.Entry<String, String> entry : pronunciationDict.entrySet()) {
            // Case-insensitive Ersetzung mit Wortgrenzen
            result = result.replaceAll("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(entry.getKey())
                    + "(?![\\p{L}\\p{N}_])", Matcher.quoteReplacement(entry.getValue()));
        }
        return result;
    }

    static String addNaturalPauses(String text) {
        return text.replace(" - ", " – ").replace("...", "…");
    }
}
