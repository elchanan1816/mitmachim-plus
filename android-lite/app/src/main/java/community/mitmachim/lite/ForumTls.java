package community.mitmachim.lite;

import android.content.Context;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/** Explicit system-only anchors plus the public forum/NetFree CA bundle. */
final class ForumTls {
    private ForumTls() { }
    static SSLSocketFactory create(Context context) throws GeneralSecurityException, IOException {
        KeyStore anchors = KeyStore.getInstance(KeyStore.getDefaultType());
        anchors.load(null, null);
        KeyStore system = KeyStore.getInstance("AndroidCAStore");
        system.load(null, null);
        Enumeration<String> aliases = system.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            // AndroidCAStore also exposes user-installed certificates: exclude those.
            if (alias.startsWith("system:")) {
                Certificate cert = system.getCertificate(alias);
                if (cert != null) anchors.setCertificateEntry(alias, cert);
            }
        }
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        for (Field field : R.raw.class.getFields()) {
            if (!field.getName().startsWith("netfree_") && !field.getName().equals("isrg_root_x1")) continue;
            try (InputStream input = context.getResources().openRawResource(field.getInt(null))) {
                X509Certificate cert = (X509Certificate) factory.generateCertificate(input);
                if (cert.getBasicConstraints() < 0) throw new CertificateException("Not a CA");
                anchors.setCertificateEntry(field.getName(), cert);
            } catch (IllegalAccessException e) {
                throw new GeneralSecurityException("CA bundle unavailable", e);
            } catch (CertificateException e) {
                // A legacy vendor may not understand one of the newer public key algorithms.
                // It does not receive a trust bypass; that particular chain will fail closed.
                if (field.getName().equals("isrg_root_x1")) throw e;
            }
        }
        TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(anchors);
        TrustManager[] trust = managers.getTrustManagers();
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trust, null);
        return new ModernProtocols(ssl.getSocketFactory());
    }

    static final class ModernProtocols extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        ModernProtocols(SSLSocketFactory delegate) { this.delegate = delegate; }
        private Socket secure(Socket socket, String host) throws IOException {
            if (!(socket instanceof SSLSocket)) throw new IOException("TLS socket required");
            SSLSocket ssl = (SSLSocket) socket;
            List<String> protocols = new ArrayList<>();
            for (String protocol : ssl.getSupportedProtocols()) {
                if (protocol.equals("TLSv1.2") || protocol.equals("TLSv1.3")) protocols.add(protocol);
            }
            if (protocols.isEmpty()) { socket.close(); throw new IOException("TLS 1.2 unavailable"); }
            ssl.setEnabledProtocols(protocols.toArray(new String[0]));
            List<String> suites = new ArrayList<>();
            for (String suite : ssl.getSupportedCipherSuites()) if (TlsPolicy.allowed(suite)) suites.add(suite);
            if (suites.isEmpty()) { socket.close(); throw new IOException("Supported TLS cipher required"); }
            // KitKat enables RC4/3DES/export suites by default: do not inherit those defaults.
            ssl.setEnabledCipherSuites(suites.toArray(new String[0]));
            if (host != null && android.os.Build.VERSION.SDK_INT < 23) {
                try { ssl.getClass().getMethod("setHostname", String.class).invoke(ssl, host); }
                catch (Exception ignored) { /* Hostname verification itself remains mandatory. */ }
            }
            return socket;
        }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket() throws IOException { return secure(delegate.createSocket(), null); }
        @Override public Socket createSocket(Socket s, String h, int p, boolean close) throws IOException { return secure(delegate.createSocket(s, h, p, close), h); }
        @Override public Socket createSocket(String h, int p) throws IOException { return secure(delegate.createSocket(h, p), h); }
        @Override public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException { return secure(delegate.createSocket(h, p, l, lp), h); }
        @Override public Socket createSocket(InetAddress h, int p) throws IOException { return secure(delegate.createSocket(h, p), h.getHostName()); }
        @Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException { return secure(delegate.createSocket(h, p, l, lp), h.getHostName()); }
    }
}
