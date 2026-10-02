import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Local integration probe: genuine release passes; modified bytes must fail. */
public class VerifyUpdaterSignature {
    public static void main(String[] args) throws Exception {
        File apk = new File(args[0]);
        String expected = "0af6f3e3379f1d10fe640537ce186f6288fff587d9e97e8367c7364344a45f77";
        for (int sdk : new int[]{24, 31, 35}) {
            var result = new ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(sdk).build().verify();
            if (!result.isVerified() || result.getSignerCertificates().size() != 1) {
                throw new IllegalStateException("Genuine release rejected at API " + sdk);
            }
            String signer = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(result.getSignerCertificates().get(0).getEncoded()));
            if (!expected.equals(signer)) throw new IllegalStateException("Wrong signing identity");
            System.out.println("Original release verified for API " + sdk);
        }
        Path altered = Files.createTempFile(apk.toPath().getParent(), "updater-tamper-probe-", ".apk");
        try {
            Files.copy(apk.toPath(), altered, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (RandomAccessFile file = new RandomAccessFile(altered.toFile(), "rw")) {
                long position = file.length() / 2;
                file.seek(position); int old = file.read(); file.seek(position); file.write(old ^ 1);
            }
            if (new ApkVerifier.Builder(altered.toFile()).setMinCheckedPlatformVersion(24).build().verify().isVerified()) {
                throw new IllegalStateException("Tampered APK incorrectly accepted");
            }
            System.out.println("Tampered APK rejected");
        } finally { Files.deleteIfExists(altered); }
    }
}
