package me.rezka.tv.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.rezka.tv.R;
import me.rezka.tv.data.FavoritesStore;
import me.rezka.tv.data.ProgressStore;
import me.rezka.tv.model.Anime;
import me.rezka.tv.model.CardItem;
import me.rezka.tv.model.Category;
import me.rezka.tv.model.Row;
import me.rezka.tv.net.RezkaClient;

public class MainActivity extends Activity implements CardAdapter.OnCardClick {

    private final RezkaClient client = new RezkaClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private RecyclerView rowsView;
    private RowsAdapter adapter;
    private View status;
    private TextView statusText;
    private Button statusButton;

    private List<Row> catalogRows;
    private final Map<String, ProgressStore.Entry> continueMap = new HashMap<>();
    private boolean rowsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rowsView = findViewById(R.id.rows);
        status = findViewById(R.id.status);
        statusText = findViewById(R.id.statusText);
        statusButton = findViewById(R.id.statusButton);

        rowsView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rowsView.setItemAnimator(null);
        rowsView.setClipChildren(false);
        rowsView.setClipToPadding(false);

        adapter = new RowsAdapter(this);
        rowsView.setAdapter(adapter);

        TopNav.bind(this, "", new TopNav.Listener() {
            @Override
            public void onCategory(Category category) {
                TopNav.openCategory(MainActivity.this, category);
            }

            @Override
            public void onSearch() {
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
            }

            @Override
            public void onHome() {
                rowsView.smoothScrollToPosition(0);
            }
        });

        statusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });

        handleDeepLink(getIntent());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rowsLoaded && catalogRows != null && adapter != null) {
            adapter.setRows(buildRows());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri data = intent.getData();
        if (data == null || data.getHost() == null) {
            return;
        }
        String host = data.getHost().toLowerCase(java.util.Locale.US);
        if (host.contains("rezka.ag") || host.contains("hdrezka.ag")) {
            openDetail(new CardItem(data.toString(), "", ""));
        }
    }

    private void load() {
        status.setVisibility(View.VISIBLE);
        statusText.setText(R.string.loading);
        statusButton.setVisibility(View.GONE);

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Row> rows = client.home();

                    // The app is built around the Gravity Falls page: it is the
                    // hero banner and the first favorite, regardless of what is
                    // currently on the rezka homepage.
                    Anime heroAnime = null;
                    try {
                        heroAnime = client.basicDetails(RezkaClient.FEATURED);
                    } catch (Exception ignored) {
                    }

                    final Anime finalHero = heroAnime;
                    final CardItem hero = heroAnime != null
                            ? new CardItem(RezkaClient.FEATURED, heroAnime.title, heroAnime.poster)
                            : (rows.isEmpty() ? null
                            : new CardItem(rows.get(0).items.get(0).url,
                            rows.get(0).items.get(0).title,
                            rows.get(0).items.get(0).poster));

                    if (hero == null) {
                        throw new IllegalStateException("empty home");
                    }

                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            catalogRows = rows;
                            rowsLoaded = true;
                            adapter.setData(hero, finalHero, buildRows(), true);
                            status.setVisibility(View.GONE);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText(R.string.error);
                            statusButton.setVisibility(View.VISIBLE);
                            statusButton.requestFocus();
                        }
                    });
                }
            }
        });
    }

    /**
     * Builds the local rows (continue watching + favorites) followed by the
     * catalog. Called on the UI thread and after returning from playback, so
     * the progress/favorites rows are instantly up to date.
     */
    private List<Row> buildRows() {
        List<Row> result = new ArrayList<>();
        continueMap.clear();

        List<ProgressStore.Entry> progress = ProgressStore.list(this);
        if (!progress.isEmpty()) {
            List<CardItem> cards = new ArrayList<>();
            for (ProgressStore.Entry entry : progress) {
                CardItem card = new CardItem(entry.animeUrl, entry.title, entry.poster);
                card.subtitle = entry.subtitle();
                card.progressPercent = entry.percent();
                cards.add(card);
                continueMap.put(entry.animeUrl, entry);
            }
            result.add(new Row("Продолжить просмотр", cards));
        }

        List<CardItem> favorites = FavoritesStore.cards(this);
        if (!favorites.isEmpty()) {
            result.add(new Row("Избранное", favorites));
        }

        if (catalogRows != null) {
            result.addAll(catalogRows);
        }
        return result;
    }

    @Override
    public void onCard(CardItem card) {
        ProgressStore.Entry entry = continueMap.get(card.url);
        if (entry != null) {
            startPlayer(entry);
        } else {
            openDetail(card);
        }
    }

    private void startPlayer(ProgressStore.Entry entry) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_ANIME_URL, entry.animeUrl);
        intent.putExtra(PlayerActivity.EXTRA_TITLE, entry.title);
        intent.putExtra(PlayerActivity.EXTRA_POSTER, entry.poster);
        intent.putExtra(PlayerActivity.EXTRA_POST_ID, entry.postId);
        intent.putExtra(PlayerActivity.EXTRA_TRANSLATOR_ID, entry.translatorId);
        intent.putExtra(PlayerActivity.EXTRA_EPISODE_NUMBERS, entry.episodeNumbers);
        intent.putExtra(PlayerActivity.EXTRA_SEASONS, entry.seasons);
        intent.putExtra(PlayerActivity.EXTRA_LABELS, entry.labels);
        intent.putExtra(PlayerActivity.EXTRA_INDEX, entry.index);
        startActivity(intent);
    }

    private void openDetail(CardItem card) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_URL, card.url);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
