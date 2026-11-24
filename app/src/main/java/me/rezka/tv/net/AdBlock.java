package me.rezka.tv.net;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Application-level request firewall (the "AdGuard" part).
 *
 * Every request made by the built-in WebView goes through {@link #isBlocked}.
 * The native player never requests the VAST/preroll tags at all: it gets the
 * signed HLS URL straight from the player AJAX endpoint and ignores the ad
 * configuration embedded next to it.
 */
public final class AdBlock {

    private static final Set<String> HOSTS = new HashSet<>();
    private static final String[] PATH_MARKERS = {
            "/vast", "vast.php", "/ads/", "/adserver", "/preroll",
            "/popunder", "/advert", "adfox", "adriver", "yandex.ru/ads"
    };

    private static final String[] NEVER_BLOCK = {
            "rezka.ag", "hdrezka.ag", "hdrezka.ac", "static.hdrezka.ac",
            "voidboost.one", "voidboost.net"
    };

    private AdBlock() {
    }

    public static void init(Context context) {
        if (!HOSTS.isEmpty()) {
            return;
        }
        try {
            InputStream in = context.getAssets().open("blocklist.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                HOSTS.add(line.toLowerCase(Locale.US));
            }
            reader.close();
        } catch (Exception ignored) {
            // A missing blocklist only degrades the WebView fallback.
        }
    }

    public static boolean isBlocked(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        for (String safe : NEVER_BLOCK) {
            if (lower.contains(safe)) {
                return false;
            }
        }

        Uri uri = Uri.parse(lower);
        String host = uri.getHost();
        if (host != null) {
            for (String blocked : HOSTS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) {
                    return true;
                }
            }
        }
        for (String marker : PATH_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    public static int ruleCount() {
        return HOSTS.size();
    }
}
