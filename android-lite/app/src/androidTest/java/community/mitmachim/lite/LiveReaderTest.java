package community.mitmachim.lite;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.util.Arrays;

/** Run explicitly: checks the actual Android TLS stack against public forum data. */
public class LiveReaderTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            testOnlyModernTlsProtocolsEnabled(); testPublicCategoriesRecentAndThread();
            result.putString("stream", "OK (2 live checks): TLS protocols, categories/recent/thread API\n");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError error) {
            result.putString("stream", "FAILED: " + error + "\n"); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertEquals(int expected, int actual) { if (expected != actual) throw new AssertionError(expected + " != " + actual); }
    public void testOnlyModernTlsProtocolsEnabled() throws Exception {
        SSLSocketFactory factory = ForumTls.create(getTargetContext());
        try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
            assertTrue(Arrays.asList(socket.getEnabledProtocols()).contains("TLSv1.2"));
            assertFalse(Arrays.asList(socket.getEnabledProtocols()).contains("TLSv1"));
            assertFalse(Arrays.asList(socket.getEnabledProtocols()).contains("TLSv1.1"));
        }
    }
    public void testPublicCategoriesRecentAndThread() throws Exception {
        ForumClient client = new ForumClient(ForumTls.create(getTargetContext()));
        ForumPage categories = client.load("/api/categories", 1);
        assertFalse(categories.items.isEmpty());
        ForumPage recent = client.load("/api/recent?term=alltime", 1);
        assertFalse(recent.items.isEmpty());
        int tid = recent.items.get(0).id;
        ForumPage thread = client.load("/api/topic/" + tid, 1);
        assertFalse(thread.items.isEmpty());
        assertEquals(ForumPage.Item.POST, thread.items.get(0).kind);
    }
}
