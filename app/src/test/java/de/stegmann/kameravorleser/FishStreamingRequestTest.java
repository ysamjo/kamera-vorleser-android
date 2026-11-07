package de.stegmann.kameravorleser;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FishStreamingRequestTest {
    private static final CloudSpeechRequest REQUEST =
            new CloudSpeechRequest("Hallo!", "voice", "backup", "", 1f);

    @Test public void fishRetriesOnlyThePaymentErrorAndStreamsTheFreeResponse() throws Exception {
        FakeConnection paid = new FakeConnection(402);
        FakeConnection free = new FakeConnection(200);
        ArrayDeque<FakeConnection> calls = new ArrayDeque<>();
        calls.add(paid);
        calls.add(free);
        CloudSpeechClient client = new CloudSpeechClient(endpoint -> {
            assertEquals("https://api.fish.audio/v1/tts", endpoint);
            return calls.remove();
        });
        AtomicInteger played = new AtomicInteger();
        client.streamFish(REQUEST, "test", new CloudSpeechClient.Job(), (audio, count) -> {
            assertEquals(0x0201, audio[0]);
            played.addAndGet(count);
        });
        assertTrue(calls.isEmpty());
        assertEquals("drama-3", paid.getRequestProperty("model"));
        assertEquals("s2.1-pro-free", free.getRequestProperty("model"));
        assertEquals("POST", free.getRequestMethod());
        assertEquals("Bearer test", free.getRequestProperty("Authorization"));
        assertFalse(free.getInstanceFollowRedirects());
        assertTrue(new String(free.sent.toByteArray(), StandardCharsets.UTF_8).contains("\"format\":\"pcm\""));
        assertTrue(paid.disconnected);
        assertTrue(free.disconnected);
        assertEquals(2, played.get());
    }

    @Test public void cancellingAPaymentErrorDoesNotSendASecondRequest() throws Exception {
        CloudSpeechClient.Job job = new CloudSpeechClient.Job();
        FakeConnection response = new FakeConnection(402) {
            @Override public int getResponseCode() {
                job.cancel(Runnable::run);
                return super.getResponseCode();
            }
        };
        AtomicInteger connections = new AtomicInteger();
        CloudSpeechClient client = new CloudSpeechClient(endpoint -> {
            connections.incrementAndGet();
            return response;
        });
        try {
            client.streamFish(REQUEST, "test", job, (audio, count) -> fail("Keine Audioausgabe"));
            fail("Abbruch erwartet");
        } catch (InterruptedIOException expected) {
            assertEquals(1, connections.get());
            assertTrue(response.disconnected);
        }
    }

    @Test public void aNonPaymentErrorNeverRetriesFish() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        FakeConnection response = new FakeConnection(503);
        CloudSpeechClient client = new CloudSpeechClient(endpoint -> {
            connections.incrementAndGet();
            return response;
        });
        try {
            client.streamFish(REQUEST, "test", new CloudSpeechClient.Job(), (audio, count) -> fail());
            fail("Anbieterfehler erwartet");
        } catch (IOException expected) {
            assertEquals("Fish Audio: HTTP 503", expected.getMessage());
            assertEquals(1, connections.get());
        }
    }

    private static class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        final int status;
        boolean disconnected;

        FakeConnection(int status) throws IOException {
            super(new URL("http://localhost"));
            this.status = status;
        }

        @Override public OutputStream getOutputStream() { return sent; }
        @Override public InputStream getInputStream() {
            assertEquals(200, status);
            return new ByteArrayInputStream(new byte[]{1, 2, 3, 4});
        }
        @Override public int getResponseCode() { return status; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public int getContentLength() { return 4; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }
}
