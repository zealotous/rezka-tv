package me.rezka.tv.net;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Solver for the Anubis proof-of-work gate used by rezka.ag.
 *
 * <p>Anubis serves an HTML page with an {@code anubis_challenge} JSON blob and
 * a verification cookie. The client must find {@code nonce} so that
 * {@code SHA-256(randomData + nonce)} starts with {@code difficulty} hex
 * zeroes, then call {@code /api/pass-challenge}. The server responds with a
 * signed auth cookie which is valid for 30 days. The browser does this in a
 * Web Worker; we do it on a background thread with MessageDigest. Difficulty
 * is normally 2 (roughly 256 hashes), so this is effectively instant.</p>
 */
public final class Anubis {

    private static final Pattern CHALLENGE = Pattern.compile(
            "<script id=\"anubis_challenge\" type=\"application/json\">(.*?)</script>",
            Pattern.DOTALL);
    private static final Pattern BASE_PREFIX = Pattern.compile(
            "<script id=\"anubis_base_prefix\" type=\"application/json\">(.*?)</script>",
            Pattern.DOTALL);

    private Anubis() {
    }

    public static boolean isChallenge(String body) {
        return body != null && body.contains("anubis_challenge");
    }

    /** @return absolute or root-relative pass-challenge URL. */
    public static String solve(String html, String redir) throws Exception {
        Matcher matcher = CHALLENGE.matcher(html);
        if (!matcher.find()) {
            throw new IOException("Anubis challenge payload not found");
        }
        JSONObject root = new JSONObject(matcher.group(1));
        JSONObject challenge = root.optJSONObject("challenge");
        if (challenge == null) {
            throw new IOException("Anubis challenge object missing");
        }

        JSONObject rules = root.optJSONObject("rules");
        int difficulty = rules != null
                ? rules.optInt("difficulty", 2)
                : challenge.optInt("difficulty", 2);
        difficulty = Math.max(0, Math.min(difficulty, 12));

        String id = challenge.optString("id", "");
        String randomData = challenge.optString("randomData", "");
        if (id.isEmpty() || randomData.isEmpty()) {
            throw new IOException("Incomplete Anubis challenge");
        }

        long started = System.currentTimeMillis();
        String zeros = repeat('0', difficulty);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hash = null;
        long nonce = 0;
        // Difficulty 2 needs ~256 tries; even difficulty 6 is only ~16M.
        long limit = 40_000_000L;
        for (; nonce < limit; nonce++) {
            digest.reset();
            digest.update((randomData + nonce).getBytes("UTF-8"));
            String candidate = hex(digest.digest());
            if (candidate.startsWith(zeros)) {
                hash = candidate;
                break;
            }
        }
        if (hash == null) {
            throw new IOException("Anubis proof-of-work limit reached");
        }
        long elapsed = System.currentTimeMillis() - started;

        String prefix = "";
        Matcher base = BASE_PREFIX.matcher(html);
        if (base.find()) {
            try {
                prefix = new org.json.JSONArray("[" + base.group(1) + "]").optString(0, "");
            } catch (Exception ignored) {
                prefix = "";
            }
        }

        return prefix + "/.within.website/x/cmd/anubis/api/pass-challenge"
                + "?id=" + encode(id)
                + "&response=" + hash
                + "&nonce=" + nonce
                + "&redir=" + encode(redir)
                + "&elapsedTime=" + elapsed;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return builder.toString();
    }
}
