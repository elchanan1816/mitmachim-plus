package community.mitmachim.lite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONException;

/** Read-only client: no login, no password storage, no state-changing requests. */
public final class ForumClient {
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private final SSLSocketFactory sockets;
    private volatile HttpsURLConnection active;
    public ForumClient(SSLSocketFactory sockets) { this.sockets = sockets; }

    public ForumPage load(String path, int page) throws Exception {
        String join = path.contains("?") ? "&" : "?";
        return ForumPage.parse(get(UrlPolicy.forum(path + join + "page=" + Math.max(1, page))), page);
    }

    public void cancel() {
        HttpsURLConnection connection = active;
        if (connection != null) connection.disconnect();
    }

    private String get(URI first) throws IOException {
        URI uri = first;
        for (int redirect = 0; redirect < 5; redirect++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            if (!UrlPolicy.isForum(uri)) throw new Failure("REDIRECT_BLOCKED");
            HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
            active = connection;
            try {
                connection.setSSLSocketFactory(sockets);
                // Deliberately retain the platform hostname verifier.
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(14000); connection.setReadTimeout(18000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("User-Agent", "MitmachimLite/0.1 Android");
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new Failure("REDIRECT_INVALID");
                    try { uri = uri.resolve(location); }
                    catch (IllegalArgumentException e) { throw new Failure("REDIRECT_INVALID"); }
                    continue;
                }
                if (code < 200 || code >= 300) throw new Failure("HTTP_" + code);
                String contentType = connection.getContentType();
                if (contentType == null || !contentType.toLowerCase(Locale.US).contains("json")) {
                    throw new Failure("NOT_JSON");
                }
                if (connection.getContentLength() > MAX_BYTES) throw new Failure("TOO_LARGE");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
                        if (output.size() + count > MAX_BYTES) throw new Failure("TOO_LARGE");
                        output.write(buffer, 0, count);
                    }
                    return new String(output.toByteArray(), "UTF-8");
                }
            } finally { connection.disconnect(); if (active == connection) active = null; }
        }
        throw new Failure("REDIRECT_LIMIT");
    }

    public static String errorCode(Throwable error) {
        if (error instanceof Failure) return error.getMessage();
        if (error instanceof SSLException || error instanceof java.security.GeneralSecurityException) return "TLS_CERTIFICATE";
        if (error instanceof SocketTimeoutException) return "TIMEOUT";
        if (error instanceof UnknownHostException) return "DNS";
        if (error instanceof JSONException) return "INVALID_DATA";
        if (error instanceof InterruptedIOException) return "CANCELLED";
        return "NETWORK";
    }

    public static String explanation(String code) {
        if (code.startsWith("TLS")) return "לא הצלחנו לאמת את החיבור המאובטח. בדקו שהתאריך והשעה במכשיר נכונים. אם יש סינון, ייתכן שתעודת החיבור אינה נתמכת.";
        if (code.equals("HTTP_403") || code.equals("HTTP_401")) return "הפורום לא אישר גישה לעמוד הזה. ייתכן שנדרשת התחברות באתר, או שהגישה נחסמה.";
        if (code.equals("HTTP_404")) return "העמוד לא נמצא או שאינו זמין לקריאה.";
        if (code.equals("NOT_JSON") || code.equals("INVALID_DATA")) return "התקבלה תשובה שאינה מתאימה לאפליקציה. ייתכן שמסך סינון או הגנה מפריע לחיבור.";
        if (code.startsWith("REDIRECT")) return "הפורום הפנה לכתובת שאינה נתמכת בתוך אב־הטיפוס. אפשר לנסות לפתוח באתר.";
        return "לא הצלחנו להתחבר לפורום כרגע. בדקו את חיבור האינטרנט ונסו שוב. התוכן שכבר נטען נשאר זמין לקריאה.";
    }

    public static final class Failure extends IOException {
        Failure(String code) { super(code); }
    }
}
