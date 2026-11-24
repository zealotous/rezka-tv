package me.rezka.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.rezka.tv.App;
import me.rezka.tv.R;
import me.rezka.tv.model.CardItem;

/** Wide cards used on the "Подборки" index. */
public class CollectionAdapter extends RecyclerView.Adapter<CollectionAdapter.Holder> {

    public interface OnCollectionClick {
        void onCollection(CardItem card);
    }

    private final List<CardItem> items = new ArrayList<>();
    private final OnCollectionClick listener;

    public CollectionAdapter(OnCollectionClick listener) {
        this.listener = listener;
    }

    public void setItems(List<CardItem> values) {
        items.clear();
        if (values != null) {
            items.addAll(values);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_collection, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final CardItem card = items.get(position);
        holder.title.setText(card.title);
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
                    listener.onCollection(card);
                }
            }
        });
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
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

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.collectionImage);
            title = view.findViewById(R.id.collectionTitle);
        }
    }
}
