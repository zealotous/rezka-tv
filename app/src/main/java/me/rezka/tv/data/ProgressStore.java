package me.rezka.tv.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Persistent "continue watching" entries with voiceover and season info. */
public final class ProgressStore {

    private static final String PREFS = "continue_rezka_v1";
    private static final String KEY = "entries";
    private static final int MAX_ENTRIES = 12;

    private ProgressStore() {
    }

    public static class Entry {
        public String animeUrl;
        public String title;
        public String poster;
        public String postId;
        public String translatorId;
        public int index;
        public int[] episodeNumbers;
        public int[] seasons;
        public String[] labels;
        public long positionMs;
        public long durationMs;
        public long updatedAt;

        public int percent() {
            if (durationMs <= 0) {
                return 0;
            }
            int value = (int) (positionMs * 100L / durationMs);
            return Math.max(1, Math.min(100, value));
        }

        public String subtitle() {
            int number = episodeNumbers != null && index >= 0 && index < episodeNumbers.length
                    ? episodeNumbers[index] : index + 1;
            int season = seasons != null && index >= 0 && index < seasons.length
                    ? seasons[index] : 0;
            String label = labels != null && index >= 0 && index < labels.length
                    ? labels[index] : String.valueOf(number);
            String prefix = season > 0 ? "Сезон " + season + " • Серия " + label : "Фильм";
            return prefix + "  •  " + formatTime(positionMs);
        }
    }

    public static void save(Context context, String animeUrl, String title, String poster,
                            String postId, String translatorId, int index,
                            int[] numbers, int[] seasons, String[] labels,
                            long positionMs, long durationMs) {
        if (animeUrl == null || numbers == null || seasons == null
                || index < 0 || index >= numbers.length || index >= seasons.length) {
            return;
        }
        if (durationMs > 0 && positionMs >= durationMs - 5000L) {
            remove(context, animeUrl);
            return;
        }
        if (positionMs < 3000L) {
            return;
        }

        JSONArray current = read(context);
        JSONArray out = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < current.length(); i++) {
            JSONObject o = current.optJSONObject(i);
            if (o == null) {
                continue;
            }
            if (animeUrl.equals(o.optString("url"))) {
                if (!replaced) {
                    out.put(create(animeUrl, title, poster, postId, translatorId, index,
                            numbers, seasons, labels, positionMs, durationMs));
                    replaced = true;
                }
            } else {
                out.put(o);
            }
        }
        if (!replaced) {
            out.put(create(animeUrl, title, poster, postId, translatorId, index,
                    numbers, seasons, labels, positionMs, durationMs));
        }

        List<JSONObject> objects = new ArrayList<>();
        for (int i = 0; i < out.length(); i++) {
            JSONObject o = out.optJSONObject(i);
            if (o != null) {
                objects.add(o);
            }
        }
        Collections.sort(objects, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(b.optLong("updated"), a.optLong("updated"));
            }
        });
        JSONArray trimmed = new JSONArray();
        for (int i = 0; i < objects.size() && i < MAX_ENTRIES; i++) {
            trimmed.put(objects.get(i));
        }
        write(context, trimmed);
    }

    public static List<Entry> list(Context context) {
        List<Entry> result = new ArrayList<>();
        JSONArray array = read(context);
        for (int i = 0; i < array.length(); i++) {
            Entry entry = parse(array.optJSONObject(i));
            if (entry != null) {
                result.add(entry);
            }
        }
        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return result;
    }

    public static void remove(Context context, String animeUrl) {
        JSONArray current = read(context);
        JSONArray out = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            JSONObject o = current.optJSONObject(i);
            if (o != null && !animeUrl.equals(o.optString("url"))) {
                out.put(o);
            }
        }
        write(context, out);
    }

    public static String formatTime(long ms) {
        long total = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    private static JSONObject create(String url, String title, String poster,
                                     String postId, String translatorId, int index,
                                     int[] numbers, int[] seasons, String[] labels,
                                     long positionMs, long durationMs) {
        JSONObject o = new JSONObject();
        try {
            o.put("url", url);
            o.put("title", title == null ? "" : title);
            o.put("poster", poster == null ? "" : poster);
            o.put("post", postId == null ? "" : postId);
            o.put("translator", translatorId == null ? "" : translatorId);
            o.put("index", index);
            o.put("position", positionMs);
            o.put("duration", durationMs);
            o.put("updated", System.currentTimeMillis());

            JSONArray numberArray = new JSONArray();
            for (int number : numbers) {
                numberArray.put(number);
            }
            JSONArray seasonArray = new JSONArray();
            for (int season : seasons) {
                seasonArray.put(season);
            }
            JSONArray labelArray = new JSONArray();
            for (int i = 0; i < numbers.length; i++) {
                labelArray.put(labels != null && i < labels.length
                        ? labels[i] : String.valueOf(numbers[i]));
            }
            o.put("numbers", numberArray);
            o.put("seasons", seasonArray);
            o.put("labels", labelArray);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static Entry parse(JSONObject o) {
        if (o == null) {
            return null;
        }
        try {
            Entry entry = new Entry();
            entry.animeUrl = o.optString("url", "");
            entry.title = o.optString("title", "");
            entry.poster = o.optString("poster", "");
            entry.postId = o.optString("post", "");
            entry.translatorId = o.optString("translator", "");
            entry.index = o.optInt("index", 0);
            entry.positionMs = o.optLong("position", 0);
            entry.durationMs = o.optLong("duration", 0);
            entry.updatedAt = o.optLong("updated", 0);

            JSONArray numberArray = o.optJSONArray("numbers");
            JSONArray seasonArray = o.optJSONArray("seasons");
            if (entry.animeUrl.isEmpty() || numberArray == null || seasonArray == null) {
                return null;
            }
            entry.episodeNumbers = new int[numberArray.length()];
            for (int i = 0; i < numberArray.length(); i++) {
                entry.episodeNumbers[i] = numberArray.optInt(i, i + 1);
            }
            entry.seasons = new int[seasonArray.length()];
            for (int i = 0; i < seasonArray.length(); i++) {
                entry.seasons[i] = seasonArray.optInt(i, 0);
            }
            JSONArray labelArray = o.optJSONArray("labels");
            entry.labels = new String[numberArray.length()];
            for (int i = 0; i < numberArray.length(); i++) {
                entry.labels[i] = labelArray != null && i < labelArray.length()
                        ? labelArray.optString(i, String.valueOf(entry.episodeNumbers[i]))
                        : String.valueOf(entry.episodeNumbers[i]);
            }
            if (entry.index < 0 || entry.index >= entry.episodeNumbers.length
                    || entry.index >= entry.seasons.length) {
                return null;
            }
            return entry;
        } catch (Exception e) {
            return null;
        }
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

    private static void write(Context context, JSONArray array) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, array.toString())
                .apply();
    }
}
