package me.rezka.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.rezka.tv.R;
import me.rezka.tv.model.CardItem;
import me.rezka.tv.model.Category;
import me.rezka.tv.model.CategoryPage;
import me.rezka.tv.model.FilterOption;
import me.rezka.tv.model.SortOption;
import me.rezka.tv.net.RezkaClient;

/**
 * Poster grid for a category, collection or best-of page.
 *
 * <p>Category pages keep the site's structure: the sort tabs (Последние
 * поступления / Популярные / В ожидании / Сейчас смотрят) and the
 * «Найти лучшие» dialog with the genre + period selects and «Поехали!».</p>
 */
public class CategoryActivity extends Activity {

    public static final String EXTRA_TITLE = "category_title";
    public static final String EXTRA_URL = "category_url";
    public static final String EXTRA_COLLECTIONS = "category_collections";
    public static final String EXTRA_BEST_BLOCK = "category_best_block";
    public static final String EXTRA_NAV_TITLE = "category_nav_title";

    private static final Pattern YEAR_AT_END = Pattern.compile("/(\\d{4})/?$");

    private final RezkaClient client = new RezkaClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String url;
    private String baseUrl;
    private boolean collections;
    private int bestBlockId;
    private int loadToken;

    private RecyclerView grid;
    private RecyclerView sortTabs;
    private RecyclerView genreTabs;
    private View filterRow;
    private Button bestFilter;
    private View status;
    private TextView statusText;
    private Button retry;

    private CardAdapter cardAdapter;
    private CollectionAdapter collectionAdapter;
    private SortAdapter sortAdapter;
    private SortAdapter genreAdapter;

    private List<SortOption> sorts = new ArrayList<>();
    private List<FilterOption> genres = new ArrayList<>();
    private List<FilterOption> years = new ArrayList<>();
    private FilterOption selectedGenre;
    private int selectedYear;
    private String bestTitle = "Найти лучшие";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        url = getIntent().getStringExtra(EXTRA_URL);
        baseUrl = url;
        collections = getIntent().getBooleanExtra(EXTRA_COLLECTIONS, false);
        bestBlockId = getIntent().getIntExtra(EXTRA_BEST_BLOCK, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }

        final String pageTitle = title == null || title.isEmpty()
                ? getString(R.string.app_name) : title;
        TextView titleView = findViewById(R.id.categoryTitle);
        titleView.setText(pageTitle);

        String navTitle = getIntent().getStringExtra(EXTRA_NAV_TITLE);
        if (navTitle == null || navTitle.isEmpty()) {
            navTitle = pageTitle;
        }
        TopNav.bind(this, navTitle, new TopNav.Listener() {
            @Override
            public void onCategory(Category category) {
                TopNav.openCategory(CategoryActivity.this, category);
            }

            @Override
            public void onSearch() {
                startActivity(new Intent(CategoryActivity.this, SearchActivity.class));
            }

            @Override
            public void onHome() {
                Intent intent = new Intent(CategoryActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        });

        grid = findViewById(R.id.categoryGrid);
        sortTabs = findViewById(R.id.sortTabs);
        genreTabs = findViewById(R.id.genreTabs);
        filterRow = findViewById(R.id.filterRow);
        bestFilter = findViewById(R.id.bestFilter);
        status = findViewById(R.id.categoryStatus);
        statusText = findViewById(R.id.categoryStatusText);
        retry = findViewById(R.id.categoryRetry);

        int widthDp = getResources().getConfiguration().screenWidthDp;
        if (collections) {
            grid.setLayoutManager(new GridLayoutManager(this, Math.max(2, widthDp / 300)));
            collectionAdapter = new CollectionAdapter(new CollectionAdapter.OnCollectionClick() {
                @Override
                public void onCollection(CardItem card) {
                    Intent intent = new Intent(CategoryActivity.this, CategoryActivity.class);
                    intent.putExtra(EXTRA_TITLE, card.title);
                    intent.putExtra(EXTRA_URL, card.url);
                    intent.putExtra(EXTRA_COLLECTIONS, false);
                    intent.putExtra(EXTRA_NAV_TITLE, pageTitle);
                    startActivity(intent);
                }
            });
            grid.setAdapter(collectionAdapter);
        } else {
            grid.setLayoutManager(new GridLayoutManager(this, Math.max(3, widthDp / 200)));
            cardAdapter = new CardAdapter(new CardAdapter.OnCardClick() {
                @Override
                public void onCard(CardItem card) {
                    Intent intent = new Intent(CategoryActivity.this, DetailActivity.class);
                    intent.putExtra(DetailActivity.EXTRA_URL, card.url);
                    startActivity(intent);
                }
            }, false);
            grid.setAdapter(cardAdapter);
        }

        sortTabs.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        sortTabs.setItemAnimator(null);
        sortAdapter = new SortAdapter(new SortAdapter.OnSort() {
            @Override
            public void onSort(SortOption option) {
                load(option.url, true);
            }
        });
        sortTabs.setAdapter(sortAdapter);

        genreTabs.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        genreTabs.setItemAnimator(null);
        genreAdapter = new SortAdapter(new SortAdapter.OnSort() {
            @Override
            public void onSort(SortOption option) {
                load(option.url, true);
            }
        });
        genreTabs.setAdapter(genreAdapter);

        bestFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showBestFilterDialog();
            }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load(url, false);
            }
        });

        load(url, true);
    }

    // --------------------------------------------------------------- loading

    private void load(final String nextUrl, final boolean focusGrid) {
        url = nextUrl;
        loadToken++;
        final int token = loadToken;
        status.setVisibility(View.VISIBLE);
        statusText.setText(R.string.loading);
        retry.setVisibility(View.GONE);
        if (cardAdapter != null) {
            cardAdapter.setItems(null);
        }
        if (collectionAdapter != null) {
            collectionAdapter.setItems(null);
        }

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<CardItem> cards;
                    if (collections) {
                        cards = client.collections(nextUrl);
                        sorts = new ArrayList<>();
                        genres = new ArrayList<>();
                        years = new ArrayList<>();
                    } else {
                        CategoryPage page = client.loadCategory(nextUrl, bestBlockId);
                        cards = page.cards;
                        sorts = page.sorts;
                        genres = page.genres;
                        years = page.years;
                        bestTitle = page.bestTitle;
                    }

                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken) {
                                return;
                            }
                            bindFilters();
                            if (cards.isEmpty()) {
                                statusText.setText(R.string.search_empty);
                                status.setVisibility(View.VISIBLE);
                                retry.setVisibility(View.VISIBLE);
                                retry.requestFocus();
                                return;
                            }
                            if (collections) {
                                collectionAdapter.setItems(cards);
                            } else {
                                cardAdapter.setItems(cards);
                            }
                            status.setVisibility(View.GONE);
                            if (focusGrid) {
                                grid.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (grid.getChildCount() > 0) {
                                            grid.getChildAt(0).requestFocus();
                                        }
                                    }
                                });
                            }
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (token != loadToken) {
                                return;
                            }
                            statusText.setText(R.string.error);
                            status.setVisibility(View.VISIBLE);
                            retry.setVisibility(View.VISIBLE);
                            retry.requestFocus();
                        }
                    });
                }
            }
        });
    }

    private void bindFilters() {
        if (collections) {
            filterRow.setVisibility(View.GONE);
            return;
        }

        sortAdapter.setItems(sorts);
        sortTabs.setVisibility(sorts.isEmpty() ? View.GONE : View.VISIBLE);

        if (!genres.isEmpty()) {
            readFilterFromUrl();
            List<SortOption> genreOptions = new ArrayList<>();
            for (FilterOption genre : genres) {
                String normal = RezkaClient.abs(genre.value).replace("/best/", "/");
                String name = "любого жанра".equalsIgnoreCase(genre.title)
                        ? "Все" : genre.title;
                genreOptions.add(new SortOption(name, normal, samePath(url, normal)));
            }
            genreAdapter.setItems(genreOptions);
            genreTabs.setVisibility(View.VISIBLE);
        } else {
            genreAdapter.setItems(null);
            genreTabs.setVisibility(View.GONE);
        }

        bestFilter.setVisibility(genres.isEmpty() ? View.GONE : View.VISIBLE);
        filterRow.setVisibility(sorts.isEmpty() ? View.GONE : View.VISIBLE);
        updateBestFilterLabel();
    }

    /** Preselect the genre/year when the current page is a best-of page. */
    private void readFilterFromUrl() {
        selectedGenre = genres.get(0);
        selectedYear = 0;
        if (url == null || !url.contains("/best/")) {
            return;
        }
        String bestPrefix = "";
        for (FilterOption genre : genres) {
            String genreUrl = RezkaClient.abs(genre.value);
            if (url.startsWith(genreUrl) && genreUrl.length() > bestPrefix.length()) {
                selectedGenre = genre;
                bestPrefix = genreUrl;
            }
        }
        Matcher matcher = YEAR_AT_END.matcher(url);
        if (matcher.find()) {
            try {
                selectedYear = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void updateBestFilterLabel() {
        if (genres.isEmpty()) {
            return;
        }
        if (url != null && url.contains("/best/") && selectedGenre != null) {
            String title = selectedGenre.title;
            if ("любого жанра".equalsIgnoreCase(title)) {
                title = "все жанры";
            }
            if (selectedYear > 0) {
                title += " " + selectedYear;
            }
            bestFilter.setText("Лучшие: " + title + " ▾");
        } else {
            bestFilter.setText(bestTitle + " ▾");
        }
    }

    // --------------------------------------------------------------- filters

    private void showBestFilterDialog() {
        if (genres.isEmpty()) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_best_filters, null);
        final Button genreButton = view.findViewById(R.id.filterGenre);
        final Button yearButton = view.findViewById(R.id.filterYear);
        final Button goButton = view.findViewById(R.id.filterGo);
        final Button resetButton = view.findViewById(R.id.filterReset);

        updateDialogButtons(genreButton, yearButton);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(bestTitle)
                .setView(view)
                .create();

        genreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOptionDialog("Жанр", genres, indexOfValue(genres,
                        selectedGenre == null ? "" : selectedGenre.value),
                        new OnOption() {
                            @Override
                            public void onOption(int index) {
                                selectedGenre = genres.get(index);
                                updateDialogButtons(genreButton, yearButton);
                            }
                        });
            }
        });

        yearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOptionDialog("Период", years, indexOfValue(years,
                        String.valueOf(selectedYear)), new OnOption() {
                    @Override
                    public void onOption(int index) {
                        try {
                            selectedYear = Integer.parseInt(years.get(index).value);
                        } catch (NumberFormatException e) {
                            selectedYear = 0;
                        }
                        updateDialogButtons(genreButton, yearButton);
                    }
                });
            }
        });

        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                load(bestUrl(), true);
            }
        });
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                load(baseUrl, true);
            }
        });

        dialog.show();
        genreButton.post(new Runnable() {
            @Override
            public void run() {
                genreButton.requestFocus();
            }
        });
    }

    private void updateDialogButtons(Button genreButton, Button yearButton) {
        genreButton.setText("Жанр: " + (selectedGenre == null
                ? "любой" : selectedGenre.title));
        yearButton.setText("Период: " + (selectedYear > 0
                ? String.valueOf(selectedYear) : "за все время"));
    }

    private interface OnOption {
        void onOption(int index);
    }

    private void showOptionDialog(String title, List<FilterOption> options,
                                  int checked, final OnOption callback) {
        if (options.isEmpty()) {
            return;
        }
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        callback.onOption(which);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static boolean samePath(String left, String right) {
        String a = left == null ? "" : left.split("\\?")[0];
        String b = right == null ? "" : right.split("\\?")[0];
        if (!a.endsWith("/")) {
            a += "/";
        }
        if (!b.endsWith("/")) {
            b += "/";
        }
        return a.equalsIgnoreCase(b);
    }

    private static int indexOfValue(List<FilterOption> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value.equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private String bestUrl() {
        if (selectedGenre == null) {
            return baseUrl;
        }
        String value = RezkaClient.abs(selectedGenre.value);
        if (selectedYear > 0) {
            if (!value.endsWith("/")) {
                value += "/";
            }
            value += selectedYear + "/";
        }
        return value;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }
}
