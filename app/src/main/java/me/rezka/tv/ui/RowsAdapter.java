package me.rezka.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.rezka.tv.App;
import me.rezka.tv.R;
import me.rezka.tv.model.Anime;
import me.rezka.tv.model.CardItem;
import me.rezka.tv.model.Row;

public class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HERO = 0;
    private static final int TYPE_ROW = 1;

    private final List<Row> rows = new ArrayList<>();
    private final CardAdapter.OnCardClick listener;
    private CardItem heroCard;
    private Anime heroAnime;
    private boolean focusConsumed;

    public RowsAdapter(CardAdapter.OnCardClick listener) {
        this.listener = listener;
    }

    public void setData(CardItem hero, Anime heroAnime, List<Row> newRows) {
        setData(hero, heroAnime, newRows, true);
    }

    public void setData(CardItem hero, Anime heroAnime, List<Row> newRows, boolean focusHero) {
        this.heroCard = hero;
        this.heroAnime = heroAnime;
        this.rows.clear();
        if (newRows != null) {
            this.rows.addAll(newRows);
        }
        focusConsumed = !focusHero;
        notifyDataSetChanged();
    }

    public void updateHero(Anime anime) {
        if (anime == null) {
            return;
        }
        this.heroAnime = anime;
        if (heroCard != null) {
            notifyItemChanged(0);
        }
    }

    /** Refresh rows without touching the hero or stealing focus. */
    public void setRows(List<Row> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return heroCard != null && position == 0 ? TYPE_HERO : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HERO) {
            return new HeroHolder(inflater.inflate(R.layout.item_hero, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.item_row, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeroHolder) {
            ((HeroHolder) holder).bind(heroCard, heroAnime, listener, !focusConsumed);
            focusConsumed = true;
        } else {
            int rowIndex = heroCard == null ? position : position - 1;
            if (rowIndex >= 0 && rowIndex < rows.size()) {
                ((RowHolder) holder).bind(rows.get(rowIndex));
            }
        }
    }

    @Override
    public int getItemCount() {
        return (heroCard != null ? 1 : 0) + rows.size();
    }

    static class HeroHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView poster;
        final TextView title;
        final TextView meta;
        final TextView description;
        final View play;
        final View info;
        final View search;

        HeroHolder(View view) {
            super(view);
            image = view.findViewById(R.id.heroImage);
            poster = view.findViewById(R.id.heroPoster);
            title = view.findViewById(R.id.heroTitle);
            meta = view.findViewById(R.id.heroMeta);
            description = view.findViewById(R.id.heroDesc);
            play = view.findViewById(R.id.heroPlay);
            info = view.findViewById(R.id.heroInfo);
            search = view.findViewById(R.id.heroSearch);
        }

        void bind(final CardItem card, Anime anime, final CardAdapter.OnCardClick listener,
                  boolean requestFocus) {
            String posterUrl = anime != null && !anime.backdrop.isEmpty()
                    ? anime.backdrop
                    : (anime != null && !anime.poster.isEmpty() ? anime.poster : card.poster);
            App.images().load(image, posterUrl);
            if (poster != null) {
                App.images().load(poster, anime != null && !anime.poster.isEmpty()
                        ? anime.poster : card.poster);
            }

            String name = anime != null && !anime.title.isEmpty() ? anime.title : card.title;
            title.setText(name);

            StringBuilder metaBuilder = new StringBuilder();
            if (anime != null) {
                if (anime.year > 0) {
                    metaBuilder.append(anime.year);
                }
                if (!anime.rating.isEmpty()) {
                    if (metaBuilder.length() > 0) {
                        metaBuilder.append("   •   ");
                    }
                    metaBuilder.append("★ ").append(anime.rating);
                }
                if (anime.episodeCount > 0) {
                    if (metaBuilder.length() > 0) {
                        metaBuilder.append("   •   ");
                    }
                    metaBuilder.append(anime.episodeCount).append(" серий");
                }
            }
            meta.setText(metaBuilder);
            meta.setVisibility(metaBuilder.length() == 0 ? View.GONE : View.VISIBLE);

            if (anime != null && !anime.description.isEmpty()) {
                description.setText(anime.description);
                description.setVisibility(View.VISIBLE);
            } else {
                description.setVisibility(View.GONE);
            }

            View.OnClickListener click = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onCard(card);
                    }
                }
            };
            play.setOnClickListener(click);
            info.setOnClickListener(click);
            search.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.getContext().startActivity(
                            new android.content.Intent(v.getContext(), SearchActivity.class));
                }
            });

            if (requestFocus) {
                play.post(new Runnable() {
                    @Override
                    public void run() {
                        play.requestFocus();
                    }
                });
            }
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final RecyclerView recycler;
        final CardAdapter adapter;

        RowHolder(View view, CardAdapter.OnCardClick listener) {
            super(view);
            title = view.findViewById(R.id.rowTitle);
            recycler = view.findViewById(R.id.rowRecycler);
            recycler.setLayoutManager(new LinearLayoutManager(
                    view.getContext(), LinearLayoutManager.HORIZONTAL, false));
            recycler.setItemAnimator(null);
            recycler.setHasFixedSize(true);
            adapter = new CardAdapter(listener, false);
            recycler.setAdapter(adapter);
        }

        void bind(Row row) {
            title.setText(row.title);
            adapter.setItems(row.items);
            recycler.scrollToPosition(0);
        }
    }
}
