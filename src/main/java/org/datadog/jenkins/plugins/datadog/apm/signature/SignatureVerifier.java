package org.datadog.jenkins.plugins.datadog.apm.signature;

import java.io.IOException;
import java.io.InputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;

public class SignatureVerifier {

    public static final String DATADOG_PUBLIC_KEY = (
            "-----BEGIN PGP PUBLIC KEY BLOCK-----\n" +
            "Version: Hockeypuck 2.1.0-222-g25248d4\n" +
            "Comment: Hostname:\n" +
            "\n" +
            "xsFNBGBbo2cBEAC16RsAMkhlN1YYzmihO//AwHRQ+n33bnmXsGkXgp2vKPcM7bty\n" +
            "oiZxdz3FAM6fBlBKG0RNn7DBEmkiu2EdxFCp7zLzLa2NC3KW89D0GnmkvX9Uebgf\n" +
            "Iv8a4QcMHsWl3sAINnbiy2uNnJqmDzA5ofsAkJyL9fEMVdagLASC/CsxckxPDPgg\n" +
            "RBp8Et625lMdIA4Owf5ZPibYVMR5xOdoucsH3KFGJNVyvX+xwx8hQDyLMg7hbI0T\n" +
            "9nu840oZR+CQqndu6sFxqxGNzbwRYoJO/zZKttwnfDQnruJjkGL8dptHxuBJ7c+8\n" +
            "TAabUzvbhNltuJDnt3+qKbV9hg1+tNgbA6oo1E5xONzg4mm/IG9TzejU1uFGNRC2\n" +
            "2PSzwG8ps9saB61+Px3/1VmYFisdpaGssQJHbSFylQcgWJx4gVo0EBZER/i+JLn6\n" +
            "g8AiNf2RQoFKkliHMHgR0UXek+5vxiPYCF5fqMvpDqPjKq0TgbVZyfPz50LrNEy/\n" +
            "HA/a3hdJbYyMThNQ1LTKQVW7JJnIO/87P3/XV0wSpwt4JBJHXNFk+w1YY/DbAHyS\n" +
            "OGDxYUvJ8AOz1gvt8MBf/pDoV/kbY8b+PsX41eCe0uQ58tmbXhAXBZss2P2AcuZq\n" +
            "BwNho0CnfXWp04g5I3FlaWb+/ZMQ+/AsXgbTWCRldC+P2Cou5zPVrGrgMQARAQAB\n" +
            "zWdEYXRhZG9nIGRkLXRyYWNlLWphdmEgUGFja2FnaW5nIChEYXRhZG9nIGRkLXRy\n" +
            "YWNlLWphdmEgUGFja2FnaW5nKSA8cGFja2FnZStkZC10cmFjZS1qYXZhQGRhdGFk\n" +
            "b2docS5jb20+wsGTBBMBCAA9FiEE2O7camr/v79OPdjRyiBgiJT0O00FAmBbo2cC\n" +
            "GwMFCQICKQAECwkIBwUVCgkICwUWAwIBAAIeAQIXgAAKCRDKIGCIlPQ7TV2RD/4w\n" +
            "wypKpk4GFYoxnfpsNT7g3U1ZSy3qGwabo4FxXN0mH0i92AI2bREWKjkvQQcQUmfR\n" +
            "+vG05nyF0MJ3Vpre/Qzt7TOcy2sBIOpFzo4pMJpRHp5W9Pqm+PCUpzs+X5LBz04i\n" +
            "6DRhNWT0kBJI9U5mQCdZETEHQZ8iUC/UAfNkXRrUMNF6OcjkPhWUPZB2OtH07bZl\n" +
            "GZkX9iwxUcM7nsAbj/qnqcVAxSnr3ylYSBo8ctgEIY/YsQsMzq6JasxtBCaMMz10\n" +
            "TflGD0bNV0zd8xjY4OfF42C9W+o0lDlTPq3HkxOUC/uUmaX/gSniJOyAp9wtD3Al\n" +
            "P9oi3RWXEAdbIQ/D38smTYZD/0VopeoX0M7QrH6ifsCCRiMj/rbw3M/KDheWDk6w\n" +
            "5CKpYDqJVmsVvWm+h7H51nQTM5CZLXHkasWmhOkj48SU3p+NSAnvQywaVW9drU79\n" +
            "wxjUgCXSwl8IHmTDSEbrBRbLSXWLiGGeqw+EmLZckJE6BQNDFYflkVLuObhNJtRi\n" +
            "pZtYi8gqyelflomEN6YBJ8dOk6SCcnU6SEzua/2YpYsHakGld0/uyux2MfXwCiJ9\n" +
            "Wf37KG6e1eegDQWy8BPg6iRoIaEyyeEWQ8CpG8ZG2zfMxUOe4pqsRfUT5k2LE5kx\n" +
            "IPdMz7WGf1S8iuZcBwF6PhAqJJv41pJ0mO4ewnZbnQ==\n" +
            "=7erW\n" +
            "-----END PGP PUBLIC KEY BLOCK-----"
    );

    public static boolean verifySignature(InputStream artifactStream, InputStream signatureStream, InputStream publicKeyStream) throws Exception {
        PGPPublicKeyRing publicKeys = loadPublicKeyRing(publicKeyStream);
        PGPSignature signature = loadSignature(signatureStream);
        signature.init(new BcPGPContentVerifierBuilderProvider(), publicKeys.getPublicKey());
        readSignedContentInto(signature, artifactStream);
        return signature.verify();
    }

    private static PGPPublicKeyRing loadPublicKeyRing(InputStream keyStream) throws Exception {
        InputStream keyIn = PGPUtil.getDecoderStream(keyStream);
        PGPPublicKeyRingCollection pgpRing = new PGPPublicKeyRingCollection(keyIn, new BcKeyFingerprintCalculator());
        return pgpRing.getKeyRings().next();
    }

    private static PGPSignature loadSignature(InputStream signatureStream) throws Exception {
        InputStream sigInputStream = PGPUtil.getDecoderStream(signatureStream);
        PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(sigInputStream, new BcKeyFingerprintCalculator());

        Object object;
        while ((object = pgpObjectFactory.nextObject()) != null) {
            if (object instanceof PGPSignatureList) {
                return ((PGPSignatureList) object).get(0);
            }

            if (object instanceof PGPCompressedData) {
                pgpObjectFactory = new PGPObjectFactory(((PGPCompressedData) object).getDataStream(),
                        new BcKeyFingerprintCalculator());
            }

            if (object instanceof PGPLiteralData) {
                InputStream dataStream = ((PGPLiteralData) object).getDataStream();
                byte[] buf = new byte[8192];
                while (dataStream.read(buf) > 0) {
                }
            }
        }
        throw new IllegalArgumentException("PGP signature not found");
    }

    private static void readSignedContentInto(PGPSignature signature, InputStream inputStream) throws IOException {
        byte[] buf = new byte[8192];
        int t;
        while ((t = inputStream.read(buf)) >= 0) {
            signature.update(buf, 0, t);
        }
    }

}
