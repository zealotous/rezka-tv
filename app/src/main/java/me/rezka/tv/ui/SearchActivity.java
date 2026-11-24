package me.rezka.tv.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.rezka.tv.R;
import me.rezka.tv.model.CardItem;
import me.rezka.tv.net.RezkaClient;

public class SearchActivity extends Activity implements CardAdapter.OnCardClick {

    private final RezkaClient client = new RezkaClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private EditText input;
    private View status;
    private TextView empty;
    private CardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        input = findViewById(R.id.searchInput);
        status = findViewById(R.id.searchStatus);
        empty = findViewById(R.id.searchEmpty);

        RecyclerView results = findViewById(R.id.searchResults);
        results.setLayoutManager(new GridLayoutManager(this, 4));
        results.setItemAnimator(null);
        adapter = new CardAdapter(this, false);
        results.setAdapter(adapter);

        findViewById(R.id.searchGo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    search();
                    return true;
                }
                return false;
            }
        });

        input.requestFocus();
        input.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 350);

        loadPopular();
    }

    /** Lets the projector be useful even with the Chinese system keyboard. */
    private void loadPopular() {
        status.setVisibility(View.VISIBLE);
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<CardItem> cards = client.cards(RezkaClient.BASE + "/cartoons/");
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setVisibility(View.GONE);
                            adapter.setItems(cards);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setVisibility(View.GONE);
                        }
                    });
                }
            }
        });
    }

    private void search() {
        final String query = input.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Введите название или выберите мультфильм из списка",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        status.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        adapter.setItems(null);

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<CardItem> cards = client.search(query);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setVisibility(View.GONE);
                            adapter.setItems(cards);
                            empty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            status.setVisibility(View.GONE);
                            empty.setText(R.string.error);
                            empty.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    @Override
    public void onCard(CardItem card) {
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
