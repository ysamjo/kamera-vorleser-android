package de.stegmann.kameravorleser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Kuratierte Cloud-Rollen. IDs bleiben unabhängig von Listen- und Erkennungsreihenfolge. */
final class PokopiaVoiceCatalog {
    private static final List<String> VOICES = CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_DEFAULTS, true);
    private static final Set<String> DEFAULT_POOL = new HashSet<>(VOICES);
    private static final Set<String> LEGACY_POOL = new HashSet<>(
            CloudVoiceProfiles.parse(CloudVoiceProfiles.FISH_LEGACY_DEFAULTS, true));

    private static final Role[] ROLES = {
            new Role(0, "ruhig und klar erzählen, warm und deutlich sprechen",
                    "erzähler", "narrator", "system"),
            new Role(1, "hell, neugierig und energiegeladen, deutlich sprechen",
                    "pikachu", "pichu"),
            new Role(2, "herzlich, verspielt und freundlich, deutlich sprechen",
                    "evoli", "eevee", "togepi", "emolga", "dedenne", "pummeluff", "jigglypuff",
                    "wuffels", "rockruff", "hoppspross", "hoppip", "knospi", "budew", "mobai",
                    "bonsly", "riolu"),
            new Role(3, "bedacht, erfahren und geduldig, ruhig und deutlich sprechen",
                    "professor tangoloss", "professor tangrowth", "professor", "tangoloss", "tangrowth"),
            new Role(4, "kraftvoll und tatkräftig, rau aber freundlich und deutlich sprechen",
                    "scherox", "scizor", "sichlor", "scyther", "praktibalk", "timburr", "rotomurf",
                    "drilbur", "machollo", "machop", "maschock", "machoke", "machomei", "machamp",
                    "panzaeron", "skarmory", "tinkmaster"),
            new Role(5, "sanft und etwas erschöpft, herzlich und deutlich sprechen",
                    "schlappchu", "peakychu"),
            new Role(6, "warm, mutig und lebhaft, deutlich sprechen",
                    "glumanda", "charmander", "glutexo", "charmeleon", "glurak", "charizard"),
            new Role(7, "sanft, naturverbunden und zuversichtlich, deutlich sprechen",
                    "bisasam", "bulbasaur", "bisaknosp", "ivysaur"),
            new Role(8, "gemütlich, tief und schläfrig, langsam aber deutlich sprechen",
                    "mooslaxo", "mosslax", "relaxo", "snorlax", "dragoran", "dragonite", "lapras",
                    "bisaflor", "venusaur", "turtok", "blastoise", "chelterrar", "torterra", "altaria",
                    "wailord", "chef dente"),
            new Role(9, "präzise, rhythmisch und lebhaft, klar und deutlich sprechen",
                    "dj rotom", "rotom", "porygon", "magnetilo", "magnemite", "magneton", "metagross",
                    "genesect", "golbit", "golett", "golgantes", "golurk"),
            new Role(10, "klein, stolz und aufgeweckt, hell und deutlich sprechen",
                    "plinfa", "piplup", "meikro", "rookidee", "staralili", "starly", "taubsi", "pidgey",
                    "habitak", "spearow", "waumpel", "wurmple", "raupy", "caterpie", "hornliu",
                    "weedle", "wommel", "cutiefly"),
            new Role(11, "frech, fröhlich und flink, natürlich und deutlich sprechen",
                    "schiggy", "squirtle", "schillok", "wartortle", "mauzi", "meowth", "karpador",
                    "magikarp", "unratütox", "trubbish", "schlaraffel", "greedent", "kleptifux", "nickit"),
            new Role(12, "weich, elegant und gelassen, ruhig und deutlich sprechen",
                    "felori", "sprigatito", "suicune", "mew", "celebi", "guardevoir", "gardevoir",
                    "galagladi", "gallade", "vulnona", "ninetales", "milotic", "arktos", "articuno"),
            new Role(13, "tief, standfest und würdevoll, ruhig und deutlich sprechen",
                    "onix", "stahlos", "steelix", "meistagrif", "conkeldurr", "stalobor", "excadrill",
                    "geowaz", "golem", "rihorn", "rhyhorn", "rizeros", "rhydon", "despotar",
                    "tyranitar", "volcanion", "groudon", "mewtu", "mewtwo", "entei", "raikou",
                    "zapdos", "lavados", "moltres", "ho oh", "lugia", "rayquaza", "dialga", "palkia"),
            new Role(14, "geheimnisvoll, verschmitzt und leise, trotzdem deutlich sprechen",
                    "driftlon", "drifloon", "nebulak", "gastly", "alpollo", "haunter", "gengar",
                    "traunfugil", "misdreavus", "zwirrlicht", "duskull", "lichtel", "litwick",
                    "mimigma", "mimikyu", "kryppuk", "spiritomb", "farbeagle", "smeargle",
                    "meister farbeagle", "smearguru")
    };

    private PokopiaVoiceCatalog() {}

    static String voiceFor(String speaker) {
        Role role = roleFor(speaker);
        return role == null ? null : VOICES.get(role.slot);
    }

    static String directionFor(String speaker) {
        Role role = roleFor(speaker);
        return role == null ? null : role.direction;
    }

    static boolean isDefaultPool(List<String> voices) {
        return voices != null && DEFAULT_POOL.equals(new HashSet<>(voices));
    }

    /** Nur den ausgelieferten alten Pool erweitern, auch bei anderer Reihenfolge. */
    static boolean shouldUpgrade(String raw) {
        if (raw == null) return true;
        try {
            return LEGACY_POOL.equals(new HashSet<>(CloudVoiceProfiles.parse(raw, true)));
        } catch (IllegalArgumentException invalidPool) {
            return false;
        }
    }

    private static Role roleFor(String speaker) {
        String key = TextRules.normalize(speaker == null ? "" : speaker);
        if (key.isEmpty()) return ROLES[0];
        for (Role role : ROLES) {
            for (String alias : role.aliases) {
                if (key.equals(alias) || key.startsWith(alias + " ") || key.endsWith(" " + alias)) {
                    return role;
                }
            }
        }
        return null;
    }

    private static final class Role {
        final int slot;
        final String direction;
        final String[] aliases;

        Role(int slot, String direction, String... aliases) {
            this.slot = slot;
            this.direction = direction;
            this.aliases = aliases;
        }
    }
}
