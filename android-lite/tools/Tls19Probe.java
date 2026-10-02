import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/** Host-side approximation only; this is not a substitute for testing a KitKat device. */
class Tls19Probe {
    public static void main(String[] args) throws Exception {
        try (SSLSocket socket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket("mitmachim.top", 443)) {
            socket.setSoTimeout(15000);
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
            String[] kitkat = {
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                "TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"
            };
            socket.setEnabledCipherSuites(Arrays.stream(kitkat).filter(Arrays.asList(socket.getSupportedCipherSuites())::contains).toArray(String[]::new));
            socket.startHandshake();
            System.out.println("Validated handshake: " + socket.getSession().getProtocol() + " / " + socket.getSession().getCipherSuite());
            System.out.println("Peer: " + socket.getSession().getPeerPrincipal());
            for (java.security.cert.Certificate cert : socket.getSession().getPeerCertificates()) {
                System.out.println("Chain: " + ((java.security.cert.X509Certificate) cert).getSubjectX500Principal()
                    + " <- " + ((java.security.cert.X509Certificate) cert).getIssuerX500Principal());
            }
        }
    }
}
