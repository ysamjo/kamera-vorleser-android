package de.stegmann.kameravorleser;

import java.util.concurrent.atomic.AtomicBoolean;

/** Gibt ein akzeptiertes Kamerabild genau einmal frei. */
final class FrameRelease {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable release;
    FrameRelease(Runnable release) { this.release = release; }
    void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }
}
