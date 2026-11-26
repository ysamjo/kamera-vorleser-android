package de.stegmann.kameravorleser;

final class ZeldaTheme {
    enum Style { NORMAL, ZELDA }

    private ZeldaTheme() {}

    static Style forSpeaker(String speaker) {
        return speaker != null && TextRules.normalize(speaker).equals("zelda")
                ? Style.ZELDA : Style.NORMAL;
    }
}
