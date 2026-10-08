package de.stegmann.kameravorleser;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeechQueueTest {
    @Test public void synchronousRejectionAndExceptionFreeOnlyTheirOwnSlot() {
        SpeechQueue queue = new SpeechQueue();
        String active = queue.add("aktiv");
        assertFalse(queue.submit("abgelehnt", id -> false));
        assertFalse(queue.submit("fehler", id -> { throw new IllegalStateException("Engine ausgefallen"); }));
        assertEquals(1, queue.size());
        assertTrue(queue.contains(active));
        assertTrue(queue.submit("angenommen", id -> true));
        assertEquals(2, queue.size());
    }

    @Test public void lateCallbacksCannotConsumeNewSpeechAfterStop() {
        SpeechQueue queue = new SpeechQueue();
        String old = queue.add("alt");
        queue.clear();
        String current = queue.add("neu");
        assertNull(queue.finish(old));
        assertEquals(1, queue.size());
        assertTrue(queue.contains(current));
        assertEquals("neu", queue.finish(current));
        assertEquals(0, queue.size());
    }
    @Test public void completionAndErrorForSameIdAreIdempotent() {
        SpeechQueue queue = new SpeechQueue();
        String first = queue.add("eins");
        String second = queue.add("zwei");
        assertEquals("eins", queue.finish(first));
        assertNull(queue.finish(first));
        assertEquals(1, queue.size());
        assertEquals("zwei", queue.finish(second));
    }
    @Test public void repeatedPauseResumeRepeatLeavesOnlyCurrentIds() {
        SpeechQueue queue = new SpeechQueue();
        String previous = queue.add("gleich");
        for (int i = 0; i < 50; i++) {
            queue.clear();
            String current = queue.add("gleich");
            assertNotEquals(previous, current);
            assertNull(queue.finish(previous));
            assertEquals(1, queue.size());
            previous = current;
        }
    }
}
