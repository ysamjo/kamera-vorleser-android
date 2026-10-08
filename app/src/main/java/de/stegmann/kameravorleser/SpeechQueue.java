package de.stegmann.kameravorleser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Gehört dem Main Thread; alte und doppelte Callbacks sind wirkungslos. */
final class SpeechQueue {
    private final Map<String, String> pending = new LinkedHashMap<>();
    String add(String dialogueKey) {
        String id = UUID.randomUUID().toString();
        pending.put(id, dialogueKey);
        return id;
    }
    boolean contains(String id) { return pending.containsKey(id); }
    String finish(String id) { return pending.remove(id); }
    int size() { return pending.size(); }
    void clear() { pending.clear(); }
}
