package me.rezka.tv.net;

import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Tiny blocking HTTP helper. HttpURLConnection is used on purpose: on the
 * Amlogic T968 / Android 6 there is no need for OkHttp, and this keeps the
 * APK small enough for the projector's limited /data partition.
 *
 * <p>All calls share the process-wide {@link java.net.CookieManager} installed
 * in {@link me.rezka.tv.App}, so the Anubis auth cookie is reused. When a
 * request hits the Anubis challenge page, it is solved once and the request is
 * retried transparently.</p>
 */
public final class Http {

    public static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 25000;
    private static final int MAX_BODY = 12 * 1024 * 1024;

    private Http() {
    }

    public static String get(String url) throws IOException {
        return request(url, null, null, url, true);
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        return request(url, null, headers, url, true);
    }

    public static String getHtml(String url, String referer) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        if (referer != null) {
            headers.put("Referer", referer);
        }
        return request(url, null, headers, referer != null ? referer : url, true);
    }

    public static String getJson(String url, String referer) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("X-Requested-With", "XMLHttpRequest");
        if (referer != null) {
            headers.put("Referer", referer);
        }
        return request(url, null, headers, referer != null ? referer : url, true);
    }

    /**
     * POSTs an application/x-www-form-urlencoded body (used by the rezka
     * player AJAX endpoint).
     */
    public static String postForm(String url, Map<String, String> form,
                                  String referer) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/javascript, */*; q=0.01");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        headers.put("Origin", origin(url));
        if (referer != null) {
            headers.put("Referer", referer);
        }
        return request(url, body.toString().getBytes("UTF-8"), headers,
                referer != null ? referer : url, true);
    }

    /** Low-level GET without the Anubis retry (used by the solver itself). */
    static String rawGet(String url) throws IOException {
        return request(url, null, null, url, false);
    }

    private static String request(String url, byte[] body, Map<String, String> headers,
                                  String challengeRedir, boolean allowSolve) throws IOException {
        String text = raw(url, body, headers);

        if (allowSolve && Anubis.isChallenge(text)) {
            String pass;
            try {
                pass = Anubis.solve(text, challengeRedir == null ? url : challengeRedir);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Anubis: " + e.getMessage(), e);
            }
            if (pass.startsWith("/")) {
                pass = origin(url) + pass;
            }
            rawGet(pass);
            text = raw(url, body, headers);
            if (Anubis.isChallenge(text)) {
                throw new IOException("Anubis challenge was not accepted");
            }
        }
        return text;
    }

    private static String raw(String url, byte[] body, Map<String, String> headers)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", UA);
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream out = connection.getOutputStream();
                out.write(body);
                out.close();
            }

            int code = connection.getResponseCode();
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) {
                throw new IOException("HTTP " + code + " for " + url);
            }

            String encoding = connection.getContentEncoding();
            if (encoding != null && encoding.toLowerCase(Locale.US).contains("gzip")) {
                input = new GZIPInputStream(input);
            }

            byte[] bytes = readAll(input);
            if (code >= 400) {
                throw new IOException("HTTP " + code + " for " + url);
            }

            String charset = "UTF-8";
            String contentType = connection.getContentType();
            if (contentType != null) {
                int idx = contentType.toLowerCase(Locale.US).indexOf("charset=");
                if (idx >= 0) {
                    charset = contentType.substring(idx + 8).split("[;\\s]")[0].trim();
                }
            }
            return new String(bytes, charset);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static Map<String, String> referer(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", url);
        return headers;
    }

    /** Decodes HTML entities found in attributes and text. */
    @SuppressWarnings("deprecation")
    public static String decodeEntities(String value) {
        return Html.fromHtml(value).toString();
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String origin(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getProtocol() + "://" + parsed.getHost();
        } catch (Exception e) {
            return "https://rezka.ag";
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buffer = new byte[32 * 1024];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY) {
                    throw new IOException("Response too large");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            input.close();
        }
    }
}
