package me.rezka.tv.net;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.rezka.tv.model.Anime;
import me.rezka.tv.model.CardItem;
import me.rezka.tv.model.Category;
import me.rezka.tv.model.CategoryPage;
import me.rezka.tv.model.FilterOption;
import me.rezka.tv.model.Episode;
import me.rezka.tv.model.Row;
import me.rezka.tv.model.SortOption;
import me.rezka.tv.model.StreamInfo;
import me.rezka.tv.model.Translator;

/**
 * Scraper for the public rezka.ag pages plus the player AJAX endpoint.
 *
 * <p>Flow for one title:</p>
 * <pre>
 *   /cartoons/.../1982-slug.html   -> title, poster, translators, seasons
 *   POST /ajax/get_cdn_series/     -> episodes for a translator
 *   POST /ajax/get_cdn_series/     -> signed HLS URL for one episode
 * </pre>
 *
 * <p>rezka.ag sits behind the Anubis proof-of-work gate; {@link Http} solves
 * it automatically and keeps the resulting cookie. The resolver never loads
 * the site's player page, so its VAST/preroll configuration is never
 * requested. Playback is the clean HLS stream only.</p>
 */
public final class RezkaClient {

    public static final String BASE = "https://rezka.ag";

    /** The title this client is built around. */
    public static final String FEATURED =
            BASE + "/cartoons/comedy/1982-graviti-folz-2012.html";

    private static final Pattern ITEM = Pattern.compile(
            "<div class=\"b-content__inline_item\"[^>]*data-url=\"([^\"]+)\"[^>]*>"
                    + "(.*?)<div class=\"b-content__inline_item-link\">\\s*<a[^>]*>(.*?)</a>",
            Pattern.DOTALL);
    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*?src=\"([^\"]+)\"");
    private static final Pattern COLLECTION = Pattern.compile(
            "<div class=\"b-content__collections_item\"[^>]*data-url=\"([^\"]+)\"[^>]*>"
                    + "(.*?)<a href=\"[^\"]*\" class=\"title\"[^>]*>(.*?)</a>",
            Pattern.DOTALL);
    private static final Pattern TRANSLATOR = Pattern.compile(
            "<(?:li|a)\\b([^>]*data-translator_id=\"(\\d+)\"[^>]*)>(.*?)</(?:li|a)>",
            Pattern.DOTALL);
    private static final Pattern TITLE_ATTR = Pattern.compile("title=\"([^\"]*)\"");
    private static final Pattern EPISODE = Pattern.compile(
            "<li class=\"b-simple_episode__item[^\"]*\"([^>]*)>(.*?)</li>", Pattern.DOTALL);
    private static final Pattern SEASON = Pattern.compile(
            "data-tab_id=\"(\\d+)\"[^>]*>(.*?)</li>", Pattern.DOTALL);
    private static final Pattern STREAM = Pattern.compile("\\[([^\\]]*)\\](https?://[^,]+)");
    private static final Pattern HEIGHT = Pattern.compile("(\\d{3,4})\\s*p", Pattern.CASE_INSENSITIVE);

    // ---------------------------------------------------------------- catalog

    /** Top navigation, mirroring the site header. */
    public List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("Фильмы", BASE + "/films/", false, 1));
        categories.add(new Category("Сериалы", BASE + "/series/", false, 2));
        categories.add(new Category("Мультфильмы", BASE + "/cartoons/", false, 3));
        categories.add(new Category("Аниме", BASE + "/animation/", false, 82));
        categories.add(new Category("Новинки", BASE + "/new/", false, 0));
        categories.add(new Category("Анонсы", BASE + "/announce/", false, 0));
        categories.add(new Category("Подборки", BASE + "/collections/", true, 0));
        return categories;
    }

    /** Parse the /collections/ index: wide cards linking to collection pages. */
    public List<CardItem> collections(String pathOrUrl) throws IOException {
        String html = Http.getHtml(abs(pathOrUrl), BASE + "/");
        List<CardItem> cards = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher item = COLLECTION.matcher(html);
        while (item.find() && cards.size() < 60) {
            String url = abs(item.group(1));
            if (url.isEmpty() || !seen.add(url)) {
                continue;
            }
            Matcher img = IMG.matcher(item.group(2));
            if (!img.find()) {
                continue;
            }
            cards.add(new CardItem(url, clean(item.group(3)), abs(img.group(1))));
        }
        return cards;
    }

    public List<Row> home() {
        List<Row> rows = new ArrayList<>();
        addRow(rows, "Сейчас на сайте", BASE + "/", 24);
        addRow(rows, "Мультфильмы", BASE + "/cartoons/", 24);
        addRow(rows, "Сериалы", BASE + "/series/", 24);
        addRow(rows, "Фильмы", BASE + "/films/", 24);
        return rows;
    }

    private void addRow(List<Row> rows, String title, String url, int limit) {
        List<CardItem> cards = safeCards(url);
        if (cards.size() > limit) {
            cards = new ArrayList<>(cards.subList(0, limit));
        }
        if (!cards.isEmpty()) {
            rows.add(new Row(title, cards));
        }
    }

    private List<CardItem> safeCards(String url) {
        try {
            return cards(url);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<CardItem> cards(String pathOrUrl) throws IOException {
        return parseCards(Http.getHtml(abs(pathOrUrl), BASE + "/"));
    }

    /** Fetches a category page together with its sort tabs and best filters. */
    public CategoryPage loadCategory(String pathOrUrl, int bestBlockId) throws IOException {
        String html = Http.getHtml(abs(pathOrUrl), BASE + "/");
        List<CardItem> cards = parseCards(html);
        List<SortOption> sorts = parseSorts(html);
        List<FilterOption> genres = new ArrayList<>();
        List<FilterOption> years = new ArrayList<>();
        String bestTitle = "";
        if (bestBlockId > 0) {
            String block = bestBlock(html, bestBlockId);
            bestTitle = bestBlockTitle(block);
            genres.addAll(parseOptions(firstGroup(block,
                    "<select class=\"select-category\"[^>]*>(.*?)</select>")));
            years.addAll(parseOptions(firstGroup(block,
                    "<select class=\"select-year\"[^>]*>(.*?)</select>")));
        }
        return new CategoryPage(cards, sorts, genres, years, bestTitle);
    }

    private List<CardItem> parseCards(String html) {
        List<CardItem> cards = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher item = ITEM.matcher(html);
        while (item.find() && cards.size() < 40) {
            String url = abs(item.group(1));
            if (url.isEmpty() || !seen.add(url)) {
                continue;
            }
            String block = item.group(2);
            Matcher img = IMG.matcher(block);
            if (!img.find()) {
                continue;
            }
            String title = clean(item.group(3));
            if (title.isEmpty()) {
                title = decode(metaIn(block));
            }
            cards.add(new CardItem(url, title, abs(img.group(1))));
        }
        return cards;
    }

    public List<CardItem> search(String query) throws IOException {
        String encoded = URLEncoder.encode(query, "UTF-8");
        return cards(BASE + "/search/?do=search&subaction=search&q=" + encoded);
    }

    // ---------------------------------------------------------------- details

    /** Metadata only (used for the Netflix-style hero banner). */
    public Anime basicDetails(String url) throws IOException {
        String page = Http.getHtml(abs(url), BASE + "/");
        Anime anime = new Anime();
        anime.url = abs(url).split("#")[0];
        anime.postId = postId(anime.url);
        anime.title = clean(firstGroup(page, "<h1\\b[^>]*>(.*?)</h1>"));
        anime.originalTitle = clean(firstGroup(page,
                "<div class=\"b-post__origtitle\"[^>]*>(.*?)</div>"));
        anime.status = clean(firstGroup(page,
                "<div class=\"b-post__infolast\"[^>]*>(.*?)</div>"));
        anime.description = description(page);
        anime.poster = abs(imageByItemprop(page));
        if (anime.poster.isEmpty()) {
            anime.poster = abs(meta(page, "og:image"));
        }
        anime.backdrop = abs(meta(page, "og:image"));
        if (anime.backdrop.isEmpty()) {
            anime.backdrop = anime.poster;
        }
        anime.year = year(page);
        anime.rating = rating(page);
        anime.genres = genres(page);
        anime.movie = !page.contains("b-simple_season__item")
                && page.contains("initCDNMoviesEvents");
        if (anime.movie) {
            anime.movieStreams = inlineStreams(page);
        }
        parseTranslators(page, anime);

        if (anime.title.isEmpty()) {
            anime.title = decode(meta(page, "og:title"));
        }
        if (anime.description.isEmpty()) {
            anime.description = decode(meta(page, "og:description"));
        }
        return anime;
    }

    /** Metadata + the episode list for the selected (or first) voiceover. */
    public Anime loadDetails(String url) throws IOException {
        Anime anime = basicDetails(url);

        if (anime.translatorId.isEmpty() && !anime.translators.isEmpty()) {
            anime.translatorId = anime.translators.get(0).id;
        }

        if (anime.movie) {
            anime.seasons.add(0);
            anime.episodes.add(new Episode(0, 0, "Фильм", anime.postId));
            anime.episodeCount = 1;
            return anime;
        }

        if (!anime.translatorId.isEmpty()) {
            try {
                Anime episodes = episodes(anime.postId, anime.translatorId, anime.url);
                anime.seasons.addAll(episodes.seasons);
                anime.episodes.addAll(episodes.episodes);
            } catch (Exception ignored) {
                // Details stay useful even when the player endpoint is down.
            }
        }
        if (anime.episodeCount == 0) {
            anime.episodeCount = anime.episodes.size();
        }
        return anime;
    }

    /**
     * Episodes for one voiceover. Used both by the detail screen and by the
     * player when the user changes the dub on the fly.
     */
    public Anime episodes(String postId, String translatorId, String referer)
            throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", postId);
        form.put("translator_id", translatorId);
        form.put("action", "get_episodes");
        String json = Http.postForm(BASE + "/ajax/get_cdn_series/?t="
                + System.currentTimeMillis(), form, referer);
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (Exception e) {
            throw new IOException("Некорректный ответ сайта");
        }
        if (!root.optBoolean("success", false)) {
            throw new IOException(message(root, "Не удалось получить список серий"));
        }

        Anime result = new Anime();
        result.postId = postId;
        result.translatorId = translatorId;

        parseSeasons(root.optString("seasons", ""), result);
        parseEpisodes(root.optString("episodes", ""), postId, result);

        if (result.seasons.isEmpty()) {
            for (Episode episode : result.episodes) {
                if (!result.seasons.contains(episode.season)) {
                    result.seasons.add(episode.season);
                }
            }
        }
        if (result.seasons.isEmpty()) {
            result.seasons.add(1);
        }
        return result;
    }

    // ----------------------------------------------------------------- stream

    /**
     * Resolves a signed HLS URL for one episode. For films pass season/episode
     * as 0; the AJAX call then omits them, as the site does.
     */
    public StreamInfo resolve(String postId, String translatorId, int season, int episode,
                              String referer) throws Exception {
        if (season <= 0 && episode <= 0) {
            return resolveMovie(translatorId, referer);
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("id", postId);
        form.put("translator_id", translatorId);
        if (season > 0 && episode > 0) {
            form.put("season", String.valueOf(season));
            form.put("episode", String.valueOf(episode));
        }
        form.put("action", "get_stream");

        String json = Http.postForm(BASE + "/ajax/get_cdn_series/?t="
                + System.currentTimeMillis(), form, referer);
        JSONObject root = new JSONObject(json);
        if (!root.optBoolean("success", false)) {
            throw new IOException(message(root, "Не удалось получить поток"));
        }
        if (root.optInt("premium_content", 0) == 1) {
            throw new IOException("Эта озвучка доступна только с Premium");
        }

        String streams = root.optString("url", "");
        if (streams.isEmpty()) {
            throw new IOException("Пустой список потоков");
        }

        String[] parsed = pickStream(streams);
        if (parsed == null) {
            throw new IOException("Не найден HLS-поток");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", referer == null ? BASE + "/" : referer);
        headers.put("Origin", BASE);
        headers.put("Accept", "*/*");

        return new StreamInfo(parsed[0], parsed[1], "", headers);
    }

    /**
     * Films do not use the episode AJAX call: their page ships the signed
     * streams inline, and each voiceover has its own page variant.
     */
    private StreamInfo resolveMovie(String translatorId, String referer) throws Exception {
        String pageUrl = abs(referer);
        String page = Http.getHtml(pageUrl, BASE + "/");
        Anime base = new Anime();
        base.postId = postId(pageUrl);
        parseTranslators(page, base);

        String streams = inlineStreams(page);
        String streamPageUrl = pageUrl;

        for (Translator translator : base.translators) {
            if (!translator.id.equals(translatorId)) {
                continue;
            }
            if (!translator.href.isEmpty() && !translator.href.equals(pageUrl)
                    && !translator.id.equals(base.translatorId)) {
                String variantUrl = abs(translator.href);
                String variant = Http.getHtml(variantUrl, pageUrl);
                String variantStreams = inlineStreams(variant);
                if (!variantStreams.isEmpty()) {
                    streams = variantStreams;
                    streamPageUrl = variantUrl;
                }
            }
            break;
        }

        String[] parsed = pickStream(streams);
        if (parsed == null) {
            throw new IOException("Не удалось получить поток фильма для этой озвучки");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", streamPageUrl);
        headers.put("Origin", BASE);
        headers.put("Accept", "*/*");
        return new StreamInfo(parsed[0], parsed[1], "", headers);
    }

    /**
     * Picks the best non-premium quality up to 1080p and returns
     * {url, type}. The first URL of an entry is the HLS variant
     * (…:hls:manifest.m3u8); the second is a progressive MP4 fallback.
     */
    private static String[] pickStream(String streams) {
        String best = "";
        String bestType = "hls";
        int bestHeight = -1;

        Matcher matcher = STREAM.matcher(streams);
        while (matcher.find()) {
            String quality = matcher.group(1);
            String url = matcher.group(2).replace("&amp;", "&");
            int or = url.indexOf(" or ");
            if (or >= 0) {
                url = url.substring(0, or);
            }
            if (quality.contains("<") || quality.toLowerCase(java.util.Locale.US).contains("ultra")) {
                continue;
            }
            int height = 0;
            Matcher h = HEIGHT.matcher(quality);
            if (h.find()) {
                try {
                    height = Integer.parseInt(h.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
            if (height > 1080) {
                continue;
            }
            if (height > bestHeight) {
                bestHeight = height;
                best = url;
                bestType = url.contains(":hls:") || url.contains(".m3u8") ? "hls" : "mp4";
            }
        }

        if (best.isEmpty()) {
            Matcher first = STREAM.matcher(streams);
            if (first.find()) {
                String url = first.group(2).replace("&amp;", "&");
                int or = url.indexOf(" or ");
                if (or >= 0) {
                    url = url.substring(0, or);
                }
                best = url;
                bestType = url.contains(":hls:") || url.contains(".m3u8") ? "hls" : "mp4";
            }
        }
        return best.isEmpty() ? null : new String[]{best, bestType};
    }

    // ---------------------------------------------------------------- parsing

    private static void parseTranslators(String page, Anime anime) {
        Matcher matcher = TRANSLATOR.matcher(page);
        Set<String> seen = new LinkedHashSet<>();
        String active = "";
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String id = matcher.group(2);
            if (id.isEmpty() || !seen.add(id)) {
                continue;
            }
            String name = "";
            Matcher title = TITLE_ATTR.matcher(attrs);
            if (title.find()) {
                name = decode(title.group(1)).trim();
            }
            if (name.isEmpty()) {
                name = clean(matcher.group(3));
            }
            if (name.isEmpty()) {
                name = "Озвучка " + id;
            }
            boolean premium = attrs.contains("b-prem_translator");
            String href = attrIn(attrs, "href");
            anime.translators.add(new Translator(id, name, premium, href));
            if (attrs.contains("active") && active.isEmpty()) {
                active = id;
            }
        }
        // Prefer the active voiceover, but fall back to a free one: premium
        // dubs cannot be resolved without a logged-in account.
        if (!active.isEmpty()) {
            boolean activePremium = false;
            for (Translator translator : anime.translators) {
                if (translator.id.equals(active) && translator.premium) {
                    activePremium = true;
                }
            }
            if (!activePremium) {
                anime.translatorId = active;
            }
        }
        if (anime.translatorId.isEmpty()) {
            for (Translator translator : anime.translators) {
                if (!translator.premium) {
                    anime.translatorId = translator.id;
                    break;
                }
            }
        }
        if (anime.translatorId.isEmpty() && !anime.translators.isEmpty()) {
            anime.translatorId = anime.translators.get(0).id;
        }
    }

    private static void parseSeasons(String html, Anime anime) {
        Matcher matcher = SEASON.matcher(html);
        while (matcher.find()) {
            int id;
            try {
                id = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (!anime.seasons.contains(id)) {
                anime.seasons.add(id);
            }
        }
        java.util.Collections.sort(anime.seasons);
    }

    private static void parseEpisodes(String html, String postId, Anime anime) {
        Map<String, Episode> found = new TreeMap<>();
        Matcher matcher = EPISODE.matcher(html);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String seasonValue = attrIn(attrs, "data-season_id");
            String episodeValue = attrIn(attrs, "data-episode_id");
            if (seasonValue.isEmpty() || episodeValue.isEmpty()) {
                continue;
            }
            int season;
            int number;
            try {
                season = Integer.parseInt(seasonValue);
                number = Integer.parseInt(episodeValue);
            } catch (NumberFormatException e) {
                continue;
            }
            String label = clean(matcher.group(2));
            if (label.startsWith("Серия ")) {
                label = label.substring("Серия ".length()).trim();
            }
            if (label.isEmpty()) {
                label = String.valueOf(number);
            }
            String key = String.format(java.util.Locale.US, "%04d:%05d", season, number);
            found.put(key, new Episode(season, number, label, postId));
        }
        anime.episodes.addAll(found.values());
    }

    private static String description(String page) {
        String raw = firstGroup(page,
                "<div class=\"b-post__description_text\"[^>]*>(.*?)</div>\\s*</div>");
        if (raw.isEmpty()) {
            raw = firstGroup(page,
                    "<div class=\"b-post__description_text\"[^>]*>(.*?)</div>");
        }
        if (raw.isEmpty()) {
            return "";
        }
        raw = raw.replaceAll("(?i)<br\\s*/?>", "\n");
        raw = raw.replaceAll("(?i)</p>", "\n\n");
        return clean(raw);
    }

    private static int year(String page) {
        String block = row(page, "Дата выхода");
        Matcher matcher = Pattern.compile("(\\d{4})").matcher(block);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher link = Pattern.compile("/year/(\\d{4})/").matcher(page);
        if (link.find()) {
            try {
                return Integer.parseInt(link.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static String rating(String page) {
        Matcher kp = Pattern.compile(
                "b-post__info_rates kp[\\s\\S]{0,400}?<span class=\"bold\">([^<]+)</span>")
                .matcher(page);
        if (kp.find()) {
            return kp.group(1).trim();
        }
        Matcher imdb = Pattern.compile(
                "b-post__info_rates imdb[\\s\\S]{0,400}?<span class=\"bold\">([^<]+)</span>")
                .matcher(page);
        if (imdb.find()) {
            return imdb.group(1).trim();
        }
        return "";
    }

    private static String genres(String page) {
        Matcher matcher = Pattern.compile(
                "<span itemprop=\"genre\"[^>]*>(.*?)</span>", Pattern.DOTALL).matcher(page);
        StringBuilder builder = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            String genre = clean(matcher.group(1));
            if (!genre.isEmpty() && seen.add(genre)) {
                if (builder.length() > 0) {
                    builder.append("  •  ");
                }
                builder.append(genre);
            }
        }
        return builder.toString();
    }

    private static String row(String page, String label) {
        Matcher matcher = Pattern.compile(
                "<td class=\"l\"><h2>" + Pattern.quote(label) + "</h2>:</td>\\s*<td[^>]*>(.*?)</td>",
                Pattern.DOTALL).matcher(page);
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private static String message(JSONObject root, String fallback) {
        String message = root.optString("message", "");
        return message == null || message.isEmpty() ? fallback : message;
    }

    /** Sort tabs from the site's category page (Последние, Популярные, …). */
    private static List<SortOption> parseSorts(String html) {
        List<SortOption> sorts = new ArrayList<>();
        Matcher list = Pattern.compile(
                "<ul class=\"b-content__main_filters\">(.*?)</ul>", Pattern.DOTALL).matcher(html);
        if (!list.find()) {
            return sorts;
        }
        Matcher link = Pattern.compile(
                "<a class=\"b-content__main_filters_link([^\"]*)\" href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.DOTALL).matcher(list.group(1));
        while (link.find()) {
            sorts.add(new SortOption(clean(link.group(3)), abs(link.group(2)),
                    link.group(1).contains("active")));
        }
        return sorts;
    }

    private static String bestBlock(String html, int id) {
        int start = html.indexOf("id=\"find-best-block-" + id + "\"");
        if (start < 0) {
            return "";
        }
        int end = html.indexOf("</button>", start);
        if (end < 0) {
            end = Math.min(html.length(), start + 30000);
        }
        return html.substring(start, end);
    }

    private static String bestBlockTitle(String block) {
        if (block == null || block.isEmpty()) {
            return "";
        }
        int select = block.indexOf("<div class=\"b-topnav__findbest_select\"");
        String head = select > 0 ? block.substring(0, select) : block;
        int gt = head.indexOf('>');
        if (gt >= 0) {
            head = head.substring(gt + 1);
        }
        return clean(head);
    }

    private static List<FilterOption> parseOptions(String selectHtml) {
        List<FilterOption> options = new ArrayList<>();
        Matcher option = Pattern.compile(
                "<option value=\"([^\"]*)\"[^>]*>(.*?)</option>", Pattern.DOTALL)
                .matcher(selectHtml);
        while (option.find()) {
            options.add(new FilterOption(clean(option.group(2)), option.group(1)));
        }
        return options;
    }

    /** Extracts the player payload from initCDNMoviesEvents/initCDNSeriesEvents. */
    private static String inlineStreams(String html) {
        Matcher matcher = Pattern.compile("\"streams\":\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(html);
        if (!matcher.find()) {
            return "";
        }
        try {
            return new JSONObject("{\"s\":\"" + matcher.group(1) + "\"}").getString("s");
        } catch (Exception e) {
            return "";
        }
    }

    private static String postId(String url) {
        Matcher matcher = Pattern.compile("/(\\d+)-[^/]+\\.html").matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String firstGroup(String html, String regex) {
        Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String attr(String html, String nameEqualsValue) {
        Matcher matcher = Pattern.compile(Pattern.quote(nameEqualsValue) + "\\s*=\\s*\"([^\"]+)\"")
                .matcher(html);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** Reads {@code <img itemprop="image" src="...">} without depending on attribute order. */
    private static String imageByItemprop(String html) {
        Matcher matcher = Pattern.compile("<img\\b([^>]*itemprop=\"image\"[^>]*)>").matcher(html);
        if (matcher.find()) {
            Matcher src = Pattern.compile("src=\"([^\"]+)\"").matcher(matcher.group(1));
            if (src.find()) {
                return src.group(1);
            }
        }
        return "";
    }

    private static String attrIn(String attrs, String name) {
        Matcher matcher = Pattern.compile(Pattern.quote(name) + "=\"([^\"]*)\"").matcher(attrs);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String meta(String html, String property) {
        Matcher matcher = Pattern.compile(
                "<meta[^>]+(?:property|name)=\"" + Pattern.quote(property)
                        + "\"[^>]+content=\"([^\"]*)\"")
                .matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher reversed = Pattern.compile(
                "<meta[^>]+content=\"([^\"]*)\"[^>]+(?:property|name)=\""
                        + Pattern.quote(property) + "\"")
                .matcher(html);
        return reversed.find() ? reversed.group(1) : "";
    }

    private static String metaIn(String block) {
        Matcher matcher = Pattern.compile("<img\\b[^>]*?alt=\"([^\"]*)\"").matcher(block);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("(?i)<br\\s*/?>", " ");
        text = text.replaceAll("<[^>]+>", " ");
        text = decode(text);
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    public static String abs(String url) {
        if (url == null) {
            return "";
        }
        url = url.trim();
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return BASE + url;
        }
        return url;
    }

    @SuppressWarnings("deprecation")
    private static String decode(String value) {
        return Http.decodeEntities(value);
    }
}
