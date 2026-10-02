package community.mitmachim.lite;

import org.junit.Test;
import java.net.URI;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import static org.junit.Assert.*;

public class ReaderTest {
    @Test public void rejectsWeakLegacyCiphersWhileAllowingKitkatEcdheAes() {
        assertTrue(TlsPolicy.allowed("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"));
        assertTrue(TlsPolicy.allowed("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"));
        assertTrue(TlsPolicy.allowed("TLS_AES_128_GCM_SHA256"));
        assertFalse(TlsPolicy.allowed("SSL_RSA_WITH_RC4_128_SHA"));
        assertFalse(TlsPolicy.allowed("SSL_RSA_WITH_3DES_EDE_CBC_SHA"));
        assertFalse(TlsPolicy.allowed("TLS_ECDHE_RSA_WITH_NULL_SHA"));
        assertFalse(TlsPolicy.allowed("TLS_DH_anon_WITH_AES_128_CBC_SHA"));
        assertFalse(TlsPolicy.allowed("TLS_RSA_WITH_AES_128_CBC_SHA"));
    }
    @Test public void acceptsOnlyForumHttpsOrigins() {
        assertTrue(UrlPolicy.isForum(URI.create("https://mitmachim.top/api/recent")));
        assertTrue(UrlPolicy.isForum(URI.create("https://files.mitmachim.top:443/api")));
        for (String url : new String[]{"http://mitmachim.top/api", "https://mitmachim.top.evil.org/", "https://evilmitmachim.top/", "https://mitmachim.top@evil.org/", "https://user@mitmachim.top/", "https://mitmachim.top:80/", "file:///mitmachim.top", "https://127.0.0.1/"}) {
            assertFalse(url, UrlPolicy.isForum(URI.create(url)));
        }
    }
    @Test public void resolvesRelativeButRejectsProtocolRelativeExternalRequests() {
        assertEquals("https://mitmachim.top/api/categories", UrlPolicy.forum("/api/categories").toString());
        try { UrlPolicy.forum("//evil.org/api"); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void dangerousBrowserSchemesNeverBecomeClickable() {
        assertNull(UrlPolicy.browser("javascript:alert(1)"));
        assertNull(UrlPolicy.browser("data:text/html,a"));
        assertNull(UrlPolicy.browser("intent://evil/#Intent;scheme=https;end"));
        assertNull(UrlPolicy.browser("file:///data/private"));
        assertNull(UrlPolicy.browser("https://user:password@mitmachim.top/"));
        assertNotNull(UrlPolicy.browser("/topic/123/hello"));
    }
    @Test public void routeIdsDoNotAcceptNegativeOrOverflowedNumbers() {
        assertEquals(21, UrlPolicy.routeId(URI.create("https://mitmachim.top/topic/21/title"), "topic"));
        assertEquals(0, UrlPolicy.routeId(URI.create("https://evil.org/topic/21"), "topic"));
        assertEquals(0, UrlPolicy.routeId(URI.create("https://mitmachim.top/topic/-2"), "topic"));
        assertEquals(0, UrlPolicy.routeId(URI.create("https://mitmachim.top/topic/9999999999999"), "topic"));
    }
    @Test public void parsesTopicsAndOmitsDeleted() throws Exception {
        ForumPage page = ForumPage.parse("{\"topics\":[{\"tid\":7,\"titleRaw\":\"שלום\",\"postcount\":5,\"category\":{\"name\":\"מחשבים\"},\"user\":{\"displayname\":\"משתמש\"}},{\"tid\":8,\"deleted\":1}],\"pagination\":{\"currentPage\":2,\"pageCount\":4}}", 2);
        assertEquals(1, page.items.size()); assertEquals(7, page.items.get(0).id);
        assertEquals(4, page.items.get(0).count); assertEquals("משתמש", page.items.get(0).author);
        assertEquals(2, page.page); assertEquals(4, page.pages);
    }
    @Test public void parsesCategoriesChildrenAndExternalCategoryLinks() throws Exception {
        ForumPage page = ForumPage.parse("{\"children\":[{\"cid\":31,\"name\":\"ברוכים הבאים\",\"link\":\"/post/427\"},{\"cid\":4,\"disabled\":true},{\"cid\":0}]}", 1);
        assertEquals(1, page.items.size()); assertEquals("/post/427", page.items.get(0).link);
        assertEquals(ForumPage.Item.CATEGORY, page.items.get(0).kind);
    }
    @Test public void parsesPostsWithNativeHtmlAndCorrectIndex() throws Exception {
        ForumPage page = ForumPage.parse("{\"title\":\"דיון\",\"locked\":1,\"posts\":[{\"pid\":5,\"index\":20,\"content\":\"<b>טקסט</b>\",\"votes\":3},{\"pid\":6,\"deleted\":true}]}", 2);
        assertEquals(1, page.items.size()); assertEquals(21, page.items.get(0).number);
        assertEquals("<b>טקסט</b>", page.items.get(0).html); assertTrue(page.locked);
    }
    @Test public void handlesRedirectStringsWithoutPretendingTheyAreContent() throws Exception {
        ForumPage page = ForumPage.parse("\"/topic/123\"", 1);
        assertEquals("/topic/123", page.redirect); assertTrue(page.items.isEmpty());
    }
    @Test public void rejectsUnexpectedPayloads() throws Exception {
        for (String input : new String[]{"[]", "null", "{\"error\":\"failed\"}", "<html>blocked</html>"}) {
            try { ForumPage.parse(input, 1); fail(input); } catch (org.json.JSONException expected) { }
        }
    }
    @Test public void mapsErrorsWithoutExposingServerBodiesOrCredentials() {
        assertEquals("TLS_CERTIFICATE", ForumClient.errorCode(new SSLHandshakeException("secret")));
        assertEquals("TIMEOUT", ForumClient.errorCode(new SocketTimeoutException("secret")));
        assertFalse(ForumClient.explanation("TLS_CERTIFICATE").contains("secret"));
        assertEquals("HTTP_403", ForumClient.errorCode(new ForumClient.Failure("HTTP_403")));
    }
    @Test public void hidesNestedSpoilerButPreservesSurroundingText() {
        String html = "<p>before</p><button class=\"extended-markdown-spoiler\">Spoiler</button><div class=\"collapse\"><div class=\"card spoiler\"><p>secret</p></div></div><p>after</p>";
        String hidden = ReaderHtml.prepare(html, false);
        assertTrue(ReaderHtml.hasSpoiler(html)); assertFalse(hidden.contains("secret"));
        assertTrue(hidden.contains("before")); assertTrue(hidden.contains("after"));
        assertTrue(ReaderHtml.prepare(html, true).contains("secret"));
    }
    @Test public void malformedHiddenSpoilerFailsClosed() {
        assertFalse(ReaderHtml.prepare("<p>before</p><div class='spoiler'><div>secret", false).contains("secret"));
    }
    @Test public void detailsSpoilerIsHiddenUntilRequested() {
        assertFalse(ReaderHtml.prepare("<details><summary>label</summary>secret</details>after", false).contains("secret"));
        assertTrue(ReaderHtml.prepare("<details><summary>label</summary>secret</details>after", false).contains("after"));
    }
}
