package me.rezka.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.rezka.tv.App;
import me.rezka.tv.R;
import me.rezka.tv.model.CardItem;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {

    public interface OnCardClick {
        void onCard(CardItem card);
    }

    private final List<CardItem> items = new ArrayList<>();
    private final OnCardClick listener;
    private final boolean firstFocus;

    public CardAdapter(OnCardClick listener, boolean firstFocus) {
        this.listener = listener;
        this.firstFocus = firstFocus;
    }

    public void setItems(List<CardItem> cards) {
        items.clear();
        if (cards != null) {
            items.addAll(cards);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final CardItem card = items.get(position);
        holder.title.setText(card.title);
        if (card.subtitle != null && !card.subtitle.isEmpty()) {
            holder.subtitle.setText(card.subtitle);
            holder.subtitle.setVisibility(View.VISIBLE);
        } else {
            holder.subtitle.setVisibility(View.GONE);
        }
        if (card.progressPercent > 0) {
            holder.progress.setVisibility(View.VISIBLE);
            holder.progress.setProgress(card.progressPercent);
        } else {
            holder.progress.setVisibility(View.GONE);
        }
        App.images().load(holder.image, card.poster);

        holder.itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Ui.focus(v, hasFocus);
            }
        });
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onCard(card);
                }
            }
        });
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);

        if (firstFocus && position == 0) {
            holder.itemView.post(new Runnable() {
                @Override
                public void run() {
                    holder.itemView.requestFocus();
                }
            });
        }
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        holder.itemView.setElevation(0);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView subtitle;
        final ProgressBar progress;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.cardImage);
            title = view.findViewById(R.id.cardTitle);
            subtitle = view.findViewById(R.id.cardSubtitle);
            progress = view.findViewById(R.id.cardProgress);
        }
    }
}
