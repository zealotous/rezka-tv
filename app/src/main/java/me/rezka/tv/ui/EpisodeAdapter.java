package me.rezka.tv.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.rezka.tv.R;
import me.rezka.tv.model.Episode;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.Holder> {

    public interface OnEpisodeClick {
        void onEpisode(int index);
    }

    private final List<Episode> episodes = new ArrayList<>();
    private final OnEpisodeClick listener;

    public EpisodeAdapter(OnEpisodeClick listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> items) {
        episodes.clear();
        if (items != null) {
            episodes.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_episode, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        Episode episode = episodes.get(position);
        holder.number.setText(episode.label);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = holder.getAdapterPosition();
                if (index != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEpisode(index);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return episodes.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView number;

        Holder(View view) {
            super(view);
            number = (TextView) view;
        }
    }
}
