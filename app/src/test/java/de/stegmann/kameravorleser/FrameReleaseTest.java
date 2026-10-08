package de.stegmann.kameravorleser;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class FrameReleaseTest {
    @Test public void releasesOnceWhenBothFailureAndCompletionRun() {
        AtomicInteger count = new AtomicInteger();
        FrameRelease frame = new FrameRelease(count::incrementAndGet);
        frame.close();
        frame.close();
        assertEquals(1, count.get());
    }
    @Test public void exceptionDuringCleanupDoesNotCauseDoubleClose() {
        AtomicInteger count = new AtomicInteger();
        FrameRelease frame = new FrameRelease(() -> { count.incrementAndGet(); throw new IllegalStateException(); });
        try { frame.close(); fail(); } catch (IllegalStateException expected) { }
        frame.close();
        assertEquals(1, count.get());
    }
}
