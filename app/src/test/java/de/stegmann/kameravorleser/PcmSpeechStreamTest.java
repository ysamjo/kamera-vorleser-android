package de.stegmann.kameravorleser;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PcmSpeechStreamTest {
    @Test public void writesBeforeEofAndPreservesSamplesAcrossOddHttpBlocks() throws Exception {
        int samples = PcmSpeechStream.START_BYTES / 2 + 1300;
        ByteBuffer pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) pcm.putShort((short) (i * 193 - 16384));
        List<Short> heard = new ArrayList<>();
        AtomicBoolean eof = new AtomicBoolean();
        try (InputStream source = new ByteArrayInputStream(pcm.array()) {
            @Override public synchronized int read(byte[] bytes, int offset, int count) {
                int read = super.read(bytes, offset, Math.min(count, 101));
                if (read < 0) {
                    assertFalse("Vor HTTP-EOF muss bereits Audio angekommen sein", heard.isEmpty());
                    eof.set(true);
                }
                return read;
            }
        }) {
            PcmSpeechStream.transfer(source, pcm.capacity(), (block, count) -> {
                assertFalse(eof.get());
                for (int i = 0; i < count; i++) heard.add(block[i]);
            }, () -> false);
        }
        assertEquals(samples, heard.size());
        for (int i = 0; i < samples; i++) assertEquals(pcm.getShort(i * 2), (short) heard.get(i));
    }

    @Test public void shortClipStillPlaysAtEof() throws Exception {
        short[] heard = new short[2];
        PcmSpeechStream.transfer(new ByteArrayInputStream(new byte[]{0, 0, 0, -128}), -1,
                (block, count) -> {
                    assertEquals(2, count);
                    System.arraycopy(block, 0, heard, 0, count);
                }, () -> false);
        assertArrayEquals(new short[]{0, Short.MIN_VALUE}, heard);
    }

    @Test public void emptyInvalidAndTruncatedResponsesDoNotStartPlayback() throws Exception {
        rejectBeforePlayback(new byte[0], -1);
        rejectBeforePlayback(new byte[]{1}, -1);
        rejectBeforePlayback(new byte[]{1, 2}, 4);
        rejectBeforePlayback(new byte[]{'I', 'D', '3', 0}, 4);
        rejectBeforePlayback(new byte[0], PcmSpeechStream.MAX_BYTES + 2);
    }

    private static void rejectBeforePlayback(byte[] bytes, int length) throws IOException {
        try {
            PcmSpeechStream.transfer(new ByteArrayInputStream(bytes), length,
                    (block, count) -> fail("Keine unvollständigen Samples abspielen"), () -> false);
            fail("Fehler erwartet");
        } catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }

    @Test public void truncatedStreamAfterInitialAudioDoesNotCompleteSuccessfully() throws Exception {
        AtomicBoolean started = new AtomicBoolean();
        try {
            byte[] data = new byte[PcmSpeechStream.START_BYTES];
            PcmSpeechStream.transfer(new ByteArrayInputStream(data), data.length + 2,
                    (block, count) -> started.set(true), () -> false);
            fail("Fehler erwartet");
        } catch (IOException expected) { assertTrue(started.get()); }
    }

    @Test public void stoppingDuringReadPreventsMoreAudioFromEnteringTheSink() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        try {
            InputStream source = new ByteArrayInputStream(new byte[PcmSpeechStream.START_BYTES]) {
                @Override public synchronized int read(byte[] bytes, int offset, int count) {
                    cancelled.set(true);
                    return super.read(bytes, offset, count);
                }
            };
            PcmSpeechStream.transfer(source, -1, (block, count) -> fail("Abbruch vor Ausgabe"), cancelled::get);
            fail("Abbruch erwartet");
        } catch (InterruptedIOException expected) { assertTrue(cancelled.get()); }
    }
}
