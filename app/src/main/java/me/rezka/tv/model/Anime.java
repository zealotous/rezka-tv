package me.rezka.tv.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One title (film or series) on rezka.ag. */
public class Anime {
    public String url = "";
    public String title = "";
    public String originalTitle = "";
    public String poster = "";
    public String backdrop = "";
    public String description = "";
    public String genres = "";
    public String rating = "";
    public String status = "";
    public int year;
    public int episodeCount;

    /** Numeric post id used by /ajax/get_cdn_series/. */
    public String postId = "";

    /** Currently selected voiceover (озвучка). */
    public String translatorId = "";

    /** True for films: a single stream without seasons/episodes. */
    public boolean movie;

    /** For films: the inline player payload of the page's default voiceover. */
    public String movieStreams = "";

    /** Available voiceovers, in site order. */
    public final List<Translator> translators = new ArrayList<>();

    /** Season numbers available for the selected voiceover. */
    public final List<Integer> seasons = new ArrayList<>();

    /** All episodes of the selected voiceover (every season). */
    public final List<Episode> episodes = new ArrayList<>();

    public String translatorName() {
        for (Translator translator : translators) {
            if (translator.id.equals(translatorId)) {
                return translator.name;
            }
        }
        return "";
    }

    public Map<String, String> translatorMap() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Translator translator : translators) {
            result.put(translator.id, translator.name);
        }
        return result;
    }
}
