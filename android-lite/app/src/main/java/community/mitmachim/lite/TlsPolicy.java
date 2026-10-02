package community.mitmachim.lite;

final class TlsPolicy {
    private TlsPolicy() { }
    static boolean allowed(String suite) {
        if (suite.equals("TLS_AES_128_GCM_SHA256") || suite.equals("TLS_AES_256_GCM_SHA384") || suite.equals("TLS_CHACHA20_POLY1305_SHA256")) return true;
        if (!suite.startsWith("TLS_ECDHE_RSA_WITH_") && !suite.startsWith("TLS_ECDHE_ECDSA_WITH_") && !suite.startsWith("TLS_DHE_RSA_WITH_")) return false;
        return suite.contains("_WITH_AES_128_GCM_") || suite.contains("_WITH_AES_256_GCM_")
            || suite.contains("_WITH_CHACHA20_POLY1305_") || suite.contains("_WITH_AES_128_CBC_") || suite.contains("_WITH_AES_256_CBC_");
    }
}
