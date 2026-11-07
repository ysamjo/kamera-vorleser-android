package de.stegmann.kameravorleser;

import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PcmSpeechOutputTest {
    @Test(timeout = 10000) public void audioTrackRendersWhileTheHttpStreamIsStillOpen() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        try (PcmSpeechOutput output = new PcmSpeechOutput(0f, () -> false, () -> started.set(true))) {
            byte[] pcm = new byte[PcmSpeechStream.START_BYTES * 4];
            ByteArrayInputStream chunks = new ByteArrayInputStream(pcm);
            try (InputStream source = new InputStream() {
                @Override public int read() { return chunks.read(); }

                @Override public int read(byte[] bytes, int offset, int count) throws IOException {
                    if (chunks.available() == 0) {
                        assertTrue("AudioTrack startet vor dem EOF", started.get());
                        awaitFrames(output, pcm.length / 2);
                    }
                    return chunks.read(bytes, offset, count);
                }
            }) {
                PcmSpeechStream.transfer(source, -1, output, () -> false);
            }
            output.awaitPlaybackEnd();
        }
    }
    @Test(timeout = 10000) public void veryShortStreamDoesNotWaitForAFullNaturalBuffer() throws Exception {
        try (PcmSpeechOutput output = new PcmSpeechOutput(0f, () -> false, () -> {})) {
            byte[] pcm = new byte[480];
            PcmSpeechStream.transfer(new ByteArrayInputStream(pcm), pcm.length, output, () -> false);
            output.awaitPlaybackEnd();
            assertTrue(output.started());
        }
    }

    @Test(timeout = 10000) public void shortTailIsPlayedAfterTheFirstBlockDrains() throws Exception {
        try (PcmSpeechOutput output = new PcmSpeechOutput(0f, () -> false, () -> {})) {
            short[] first = new short[PcmSpeechStream.START_BYTES * 2];
            short[] tail = new short[240];
            output.write(first, first.length);
            awaitFrames(output, first.length);
            output.write(tail, tail.length);
            output.awaitPlaybackEnd();
            assertTrue(output.playedFrames() >= first.length + tail.length);
        }
    }

    private static void awaitFrames(PcmSpeechOutput output, int frames) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + 3000;
        while (output.playedFrames() < frames && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(10);
        }
        assertTrue("Der Wiedergabekopf muss die geschriebenen Frames erreichen",
                output.playedFrames() >= frames);
    }

}
