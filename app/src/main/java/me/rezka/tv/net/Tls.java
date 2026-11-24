package me.rezka.tv.net;

import android.content.Context;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Android 6 (2017 security patch) does not ship the ISRG Root X1/X2 anchors
 * used by Let's Encrypt's 2024+ hierarchy. rezka.ag, static.hdrezka.ac and
 * image/CDN hosts all chain to those roots, so without this every HTTPS
 * request fails with "Trust anchor for certification path not found".
 *
 * We keep the system trust store and simply add the two ISRG roots.
 */
public final class Tls {

    private Tls() {
    }

    public static void install(Context context) {
        try {
            KeyStore extraStore = KeyStore.getInstance(KeyStore.getDefaultType());
            extraStore.load(null, null);

            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            String[] assets = {"certs/isrgrootx1.pem", "certs/isrg-root-x2.pem"};
            int index = 0;
            for (String asset : assets) {
                InputStream in = context.getAssets().open(asset);
                X509Certificate certificate = (X509Certificate) factory.generateCertificate(in);
                in.close();
                extraStore.setCertificateEntry("isrg-" + index++, certificate);
            }

            TrustManagerFactory extraFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            extraFactory.init(extraStore);

            TrustManagerFactory systemFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            systemFactory.init((KeyStore) null);

            final X509TrustManager extra = first(extraFactory.getTrustManagers());
            final X509TrustManager system = first(systemFactory.getTrustManagers());
            if (extra == null || system == null) {
                return;
            }

            X509TrustManager wrapper = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try {
                        system.checkClientTrusted(chain, authType);
                    } catch (CertificateException e) {
                        extra.checkClientTrusted(chain, authType);
                    }
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try {
                        system.checkServerTrusted(chain, authType);
                    } catch (CertificateException e) {
                        extra.checkServerTrusted(chain, authType);
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    List<X509Certificate> all = new ArrayList<>();
                    all.addAll(Arrays.asList(system.getAcceptedIssuers()));
                    all.addAll(Arrays.asList(extra.getAcceptedIssuers()));
                    return all.toArray(new X509Certificate[0]);
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{wrapper}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        } catch (Exception ignored) {
            // If this fails we still have the system trust store.
        }
    }

    private static X509TrustManager first(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        return null;
    }
}
