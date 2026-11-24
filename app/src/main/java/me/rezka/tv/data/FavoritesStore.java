package me.rezka.tv.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import me.rezka.tv.model.CardItem;

/** Persistent favorites, stored as a small JSON array in SharedPreferences. */
public final class FavoritesStore {

    public static final String FEATURED_URL =
            "https://rezka.ag/cartoons/comedy/1982-graviti-folz-2012.html";
    public static final String FEATURED_TITLE = "Гравити Фолз";
    public static final String FEATURED_POSTER =
            "https://static.hdrezka.ac/i/2021/3/3/w2e24625f538ddw56k10h.jpeg";

    private static final String PREFS = "favorites_rezka";
    private static final String KEY = "items";
    private static final String SEED = "seeded_gravity_falls_v1";

    private FavoritesStore() {
    }

    public static void seedDefaults(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(SEED, false)) {
            return;
        }
        JSONArray items = read(context);
        if (!contains(items, FEATURED_URL)) {
            items.put(item(FEATURED_URL, FEATURED_TITLE, FEATURED_POSTER));
        }
        prefs.edit()
                .putString(KEY, items.toString())
                .putBoolean(SEED, true)
                .apply();
    }

    public static List<CardItem> cards(Context context) {
        List<CardItem> result = new ArrayList<>();
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String url = o.optString("url", "");
            if (url.isEmpty()) {
                continue;
            }
            result.add(new CardItem(url, o.optString("title"), o.optString("poster")));
        }
        return result;
    }

    public static boolean contains(Context context, String url) {
        return contains(read(context), url);
    }

    /** @return true when the item is now a favorite. */
    public static boolean toggle(Context context, String url, String title, String poster) {
        JSONArray current = read(context);
        JSONArray out = new JSONArray();
        boolean found = false;
        for (int i = 0; i < current.length(); i++) {
            JSONObject o = current.optJSONObject(i);
            if (o == null) {
                continue;
            }
            if (url.equals(o.optString("url"))) {
                found = true;
                continue;
            }
            out.put(o);
        }
        if (!found) {
            out.put(item(url, title, poster));
        }
        write(context, out);
        return !found;
    }

    private static JSONObject item(String url, String title, String poster) {
        JSONObject o = new JSONObject();
        try {
            o.put("url", url);
            o.put("title", title == null ? "" : title);
            o.put("poster", poster == null ? "" : poster);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static boolean contains(JSONArray items, String url) {
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) {
                return true;
            }
        }
        return false;
    }

    private static JSONArray read(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void write(Context context, JSONArray items) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, items.toString())
                .apply();
    }
}
