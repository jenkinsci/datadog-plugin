package org.datadog.jenkins.plugins.datadog.clients;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import hudson.ProxyConfiguration;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import jenkins.model.Jenkins;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class HttpClientTlsTest {

    @Test
    public void sendsTrustedHttpsRequest() throws Exception {
        KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(2048);
        KeyPair keyPair = keyGenerator.generateKeyPair();
        X500Name subject = new X500Name("CN=127.0.0.1");
        long now = System.currentTimeMillis();
        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), new Date(now - 60_000), new Date(now + 600_000), subject, keyPair.getPublic());
        certificateBuilder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));

        char[] password = "test-password".toCharArray();
        KeyStore serverKeys = KeyStore.getInstance("PKCS12");
        serverKeys.load(null, password);
        serverKeys.setKeyEntry("server", keyPair.getPrivate(), password, new Certificate[]{certificate});
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(serverKeys, password);
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), null, new SecureRandom());

        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/secure", exchange -> {
            byte[] body = "secure".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });

        KeyStore trustedCertificates = KeyStore.getInstance("PKCS12");
        trustedCertificates.load(null, password);
        trustedCertificates.setCertificateEntry("server", certificate);
        Path trustStore = Files.createTempFile("datadog-http-client-trust-", ".p12");
        try (OutputStream out = Files.newOutputStream(trustStore)) {
            trustedCertificates.store(out, password);
        }

        String previousTrustStore = System.getProperty("javax.net.ssl.trustStore");
        String previousPassword = System.getProperty("javax.net.ssl.trustStorePassword");
        String previousType = System.getProperty("javax.net.ssl.trustStoreType");
        try {
            System.setProperty("javax.net.ssl.trustStore", trustStore.toString());
            System.setProperty("javax.net.ssl.trustStorePassword", new String(password));
            System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
            server.start();

            try (MockedStatic<Jenkins> mockedJenkins = Mockito.mockStatic(Jenkins.class)) {
                Jenkins jenkins = Mockito.mock(Jenkins.class);
                when(jenkins.getProxy()).thenReturn(new ProxyConfiguration("127.0.0.1", 1, null, null, "127.0.0.1"));
                mockedJenkins.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);

                String url = "https://127.0.0.1:" + server.getAddress().getPort() + "/secure";
                assertEquals("secure", new HttpClient(5000).get(url, Collections.emptyMap(), content -> content));
            }
        } finally {
            server.stop(0);
            restoreProperty("javax.net.ssl.trustStore", previousTrustStore);
            restoreProperty("javax.net.ssl.trustStorePassword", previousPassword);
            restoreProperty("javax.net.ssl.trustStoreType", previousType);
            Files.deleteIfExists(trustStore);
        }
    }

    private static void restoreProperty(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }
}
