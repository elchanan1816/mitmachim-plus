package community.mitmachim.lite;

import java.net.URI;
import java.util.Locale;

/** Every in-app network request is restricted to the HTTPS forum origin family. */
public final class UrlPolicy {
    public static final String BASE = "https://mitmachim.top";
    private UrlPolicy() { }

    public static boolean isForum(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
        String host = uri.getHost().toLowerCase(Locale.US);
        return host.equals("mitmachim.top") || host.endsWith(".mitmachim.top");
    }

    public static URI forum(String path) {
        URI uri = URI.create(BASE + "/").resolve(path);
        if (!isForum(uri)) throw new IllegalArgumentException("Unsafe forum URL");
        return uri;
    }

    public static URI browser(String value) {
        try {
            URI uri = URI.create(BASE + "/").resolve(value.replace(" ", "%20"));
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) return null;
            return uri;
        } catch (IllegalArgumentException e) { return null; }
    }

    public static int routeId(URI uri, String kind) {
        if (!isForum(uri)) return 0;
        String[] parts = uri.getPath().split("/");
        if (parts.length < 3 || !parts[1].equals(kind)) return 0;
        try { return Math.max(0, Integer.parseInt(parts[2])); }
        catch (NumberFormatException e) { return 0; }
    }
}
