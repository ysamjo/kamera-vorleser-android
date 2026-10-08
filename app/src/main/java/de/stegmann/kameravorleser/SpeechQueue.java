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
    interface Sender { boolean send(String id); }
    boolean submit(String key, Sender sender) {
        String id = add(key);
        try {
            if (sender.send(id)) return true;
        } catch (RuntimeException error) {
            // Ein synchroner Engine-Ausfall darf keinen Queue-Platz behalten.
        }
        finish(id);
        return false;
    }
    boolean contains(String id) { return pending.containsKey(id); }
    String finish(String id) { return pending.remove(id); }
    int size() { return pending.size(); }
    void clear() { pending.clear(); }
}
