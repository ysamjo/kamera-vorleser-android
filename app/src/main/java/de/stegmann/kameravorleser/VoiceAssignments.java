package de.stegmann.kameravorleser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Gespeicherte echte Engine-Stimmen, unabhängig von Android testbar. */
final class VoiceAssignments {
    private final Map<String, String> assigned = new HashMap<>();

    void put(String speaker, String voice) { assigned.put(TextRules.normalize(speaker), voice); }
    Map<String, String> snapshot() { return new HashMap<>(assigned); }

    String choose(String speaker, List<String> available) {
        return choose(speaker, available, -1);
    }

    String choose(String speaker, List<String> available, int preferredSlot) {
        if (available.isEmpty()) return null;
        String key = TextRules.normalize(speaker);
        String saved = assigned.get(key);
        if (available.contains(saved)) return saved;
        if (preferredSlot >= 0 && preferredSlot < available.size()) {
            String preferredVoice = available.get(preferredSlot);
            if (!assigned.containsValue(preferredVoice)) {
                assigned.put(key, preferredVoice);
                return preferredVoice;
            }
        }
        // Beste verfügbare Stimme zuerst; keine Grundstimme doppelt vergeben,
        // solange noch eine unbenutzte vorhanden ist. Danach die seltenste wählen.
        String best = (preferredSlot >= 0 && preferredSlot < available.size())
                ? available.get(preferredSlot) : available.get(0);
        int least = Integer.MAX_VALUE;
        for (String voice : available) {
            int uses = 0;
            for (Map.Entry<String, String> entry : assigned.entrySet()) {
                if (!entry.getKey().equals(key) && voice.equals(entry.getValue())) uses++;
            }
            if (uses < least) { best = voice; least = uses; }
        }
        assigned.put(key, best);
        return best;
    }
}
