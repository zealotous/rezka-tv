package me.rezka.tv.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.rezka.tv.App;
import me.rezka.tv.R;
import me.rezka.tv.data.FavoritesStore;
import me.rezka.tv.model.Anime;
import me.rezka.tv.model.Episode;
import me.rezka.tv.model.Translator;
import me.rezka.tv.net.RezkaClient;

public class DetailActivity extends Activity {

    public static final String EXTRA_URL = "anime_url";

    private final RezkaClient client = new RezkaClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String url;
    private Anime anime;
    private EpisodeAdapter episodeAdapter;
    private ChoiceAdapter seasonAdapter;
    private List<Episode> visibleEpisodes = new ArrayList<>();
    private int selectedSeason = -1;

    private ScrollViewProxy scroll;
    private View status;
    private TextView statusText;
    private Button retry;

    private ImageView poster;
    private TextView title;
    private TextView meta;
    private TextView genres;
    private TextView description;
    private TextView source;
    private TextView voice;
    private TextView seasonsLabel;
    private RecyclerView seasons;
    private Button play;
    private Button web;
    private Button favorite;
    private RecyclerView episodes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) {
            finish();
            return;
        }

        scroll = new ScrollViewProxy(findViewById(R.id.detailScroll));
        status = findViewById(R.id.detailStatus);
        statusText = findViewById(R.id.detailStatusText);
        retry = findViewById(R.id.detailRetry);

        poster = findViewById(R.id.detailPoster);
        title = findViewById(R.id.detailTitle);
        meta = findViewById(R.id.detailMeta);
        genres = findViewById(R.id.detailGenres);
        description = findViewById(R.id.detailDesc);
        source = findViewById(R.id.detailSource);
        voice = findViewById(R.id.detailVoice);
        seasonsLabel = findViewById(R.id.detailSeasonsLabel);
        seasons = findViewById(R.id.detailSeasons);
        play = findViewById(R.id.detailPlay);
        web = findViewById(R.id.detailWeb);
        favorite = findViewById(R.id.detailFav);
        episodes = findViewById(R.id.episodes);

        episodeAdapter = new EpisodeAdapter(new EpisodeAdapter.OnEpisodeClick() {
            @Override
            public void onEpisode(int index) {
                playEpisode(index);
            }
        });
        episodes.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        episodes.setItemAnimator(null);
        episodes.setAdapter(episodeAdapter);

        seasonAdapter = new ChoiceAdapter(new ChoiceAdapter.OnChoice() {
            @Override
            public void onChoice(int index) {
                if (anime != null && index >= 0 && index < anime.seasons.size()) {
                    selectedSeason = anime.seasons.get(index);
                    showSeason(selectedSeason);
                }
            }
        });
        seasons.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        seasons.setItemAnimator(null);
        seasons.setAdapter(seasonAdapter);

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playEpisode(0);
            }
        });
        web.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWeb();
            }
        });
        favorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (anime == null) {
                    return;
                }
                boolean added = FavoritesStore.toggle(DetailActivity.this,
                        anime.url, anime.title, anime.poster);
                updateFavoriteButton();
                Toast.makeText(DetailActivity.this,
                        added ? "Добавлено в избранное" : "Удалено из избранного",
                        Toast.LENGTH_SHORT).show();
            }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });

        load();
    }

    private void load() {
        scroll.setVisible(false);
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        statusText.setText(R.string.loading);

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Anime loaded = client.loadDetails(url);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            anime = loaded;
                            bind(loaded);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(R.string.error);
                            retry.setVisibility(View.VISIBLE);
                            retry.requestFocus();
                        }
                    });
                }
            }
        });
    }

    private void bind(Anime anime) {
        title.setText(anime.title);
        App.images().load(poster, anime.poster);

        StringBuilder metaBuilder = new StringBuilder();
        if (anime.year > 0) {
            metaBuilder.append(anime.year);
        }
        if (!anime.rating.isEmpty()) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append("   •   ");
            }
            metaBuilder.append("★ ").append(anime.rating);
        }
        int count = anime.episodes.size() > 1 ? anime.episodes.size() : anime.episodeCount;
        if (!anime.movie && count > 0) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append("   •   ");
            }
            metaBuilder.append(count).append(" серий");
        }
        if (anime.movie) {
            if (metaBuilder.length() > 0) {
                metaBuilder.append("   •   ");
            }
            metaBuilder.append("Фильм");
        }
        meta.setText(metaBuilder);
        genres.setText(anime.genres);
        description.setText(anime.description);

        String voiceName = anime.translatorName();
        voice.setText("Озвучка: " + (voiceName.isEmpty() ? "по умолчанию" : voiceName)
                + "   •   сменить можно в плеере");

        List<String> seasonNames = new ArrayList<>();
        for (Integer season : anime.seasons) {
            seasonNames.add(season > 0 ? "Сезон " + season : "Фильм");
        }
        boolean showSeasons = anime.seasons.size() > 1;
        seasonsLabel.setVisibility(showSeasons ? View.VISIBLE : View.GONE);
        seasons.setVisibility(showSeasons ? View.VISIBLE : View.GONE);
        if (showSeasons) {
            seasonAdapter.setItems(seasonNames, 0);
        }
        selectedSeason = anime.seasons.isEmpty() ? 1 : anime.seasons.get(0);
        showSeason(selectedSeason);

        status.setVisibility(View.GONE);
        scroll.setVisible(true);
        updateFavoriteButton();

        if (!visibleEpisodes.isEmpty()) {
            play.post(new Runnable() {
                @Override
                public void run() {
                    play.requestFocus();
                }
            });
        } else {
            web.requestFocus();
        }
    }

    private void showSeason(int season) {
        visibleEpisodes = new ArrayList<>();
        for (Episode episode : anime.episodes) {
            if (episode.season == season) {
                visibleEpisodes.add(episode);
            }
        }
        episodeAdapter.setItems(visibleEpisodes);

        boolean hasEpisodes = !visibleEpisodes.isEmpty();
        play.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        episodes.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);

        String voiceName = anime.translatorName();
        source.setText(hasEpisodes
                ? (anime.movie ? "Фильм" : visibleEpisodes.size() + " серий")
                + "  •  " + (voiceName.isEmpty() ? "озвучка по умолчанию" : voiceName)
                + "  •  HLS  •  без рекламы"
                : "Плеер недоступен — откройте страницу на сайте");
    }

    private void updateFavoriteButton() {
        if (anime == null || favorite == null) {
            return;
        }
        boolean isFavorite = FavoritesStore.contains(this, anime.url);
        favorite.setText(isFavorite ? "В избранном" : "В избранное");
        favorite.setAlpha(isFavorite ? 0.75f : 1f);
    }

    private void playEpisode(int index) {
        if (anime == null || visibleEpisodes.isEmpty()) {
            openWeb();
            return;
        }
        if (index < 0 || index >= visibleEpisodes.size()) {
            return;
        }

        int globalIndex = anime.episodes.indexOf(visibleEpisodes.get(index));
        if (globalIndex < 0) {
            globalIndex = 0;
        }

        int size = anime.episodes.size();
        int[] numbers = new int[size];
        int[] seasonNumbers = new int[size];
        String[] labels = new String[size];
        for (int i = 0; i < size; i++) {
            Episode episode = anime.episodes.get(i);
            numbers[i] = episode.number;
            seasonNumbers[i] = episode.season;
            labels[i] = episode.label;
        }

        int translatorCount = anime.translators.size();
        String[] translatorIds = new String[translatorCount];
        String[] translatorNames = new String[translatorCount];
        for (int i = 0; i < translatorCount; i++) {
            Translator translator = anime.translators.get(i);
            translatorIds[i] = translator.id;
            translatorNames[i] = translator.name;
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_ANIME_URL, anime.url);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, anime.title);
        intent.putExtra(PlayerActivity.EXTRA_POSTER, anime.poster);
        intent.putExtra(PlayerActivity.EXTRA_POST_ID, anime.postId);
        intent.putExtra(PlayerActivity.EXTRA_TRANSLATOR_ID, anime.translatorId);
        intent.putExtra(PlayerActivity.EXTRA_TRANSLATOR_IDS, translatorIds);
        intent.putExtra(PlayerActivity.EXTRA_TRANSLATOR_NAMES, translatorNames);
        intent.putExtra(PlayerActivity.EXTRA_EPISODE_NUMBERS, numbers);
        intent.putExtra(PlayerActivity.EXTRA_SEASONS, seasonNumbers);
        intent.putExtra(PlayerActivity.EXTRA_LABELS, labels);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, globalIndex);
        startActivity(intent);
    }

    private void openWeb() {
        WebActivity.start(this, url.split("#")[0],
                anime != null ? anime.title : getString(R.string.app_name));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    /** Small wrapper so the invisible ScrollView does not swallow focus. */
    private static class ScrollViewProxy {
        private final View view;

        ScrollViewProxy(View view) {
            this.view = view;
        }

        void setVisible(boolean visible) {
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }
}
