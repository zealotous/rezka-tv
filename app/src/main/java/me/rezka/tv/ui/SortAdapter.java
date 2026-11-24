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
import me.rezka.tv.model.SortOption;

/** Plain-text sort tabs (Последние поступления, Популярные, …). */
public class SortAdapter extends RecyclerView.Adapter<SortAdapter.Holder> {

    public interface OnSort {
        void onSort(SortOption option);
    }

    private static final int COLOR_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_NORMAL = 0xFFB3B3B3;

    private final List<SortOption> items = new ArrayList<>();
    private final OnSort listener;
    private int activeIndex = -1;

    public SortAdapter(OnSort listener) {
        this.listener = listener;
    }

    public void setItems(List<SortOption> values) {
        items.clear();
        activeIndex = -1;
        if (values != null) {
            items.addAll(values);
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).active) {
                    activeIndex = i;
                    break;
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sort_tab, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final SortOption option = items.get(position);
        holder.text.setText(option.title);
        final int index = holder.getAdapterPosition();

        holder.text.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyState(holder, hasFocus);
            }
        });
        holder.text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onSort(option);
                }
            }
        });
        applyState(holder, holder.text.isFocused());
    }

    private void applyState(Holder holder, boolean focused) {
        int index = holder.getAdapterPosition();
        boolean active = index == activeIndex;
        holder.text.setTextColor(active || focused ? COLOR_ACTIVE : COLOR_NORMAL);
        if (active) {
            holder.text.setBackgroundResource(R.drawable.bg_sort_active);
        } else if (focused) {
            holder.text.setBackgroundResource(R.drawable.bg_sort_focused);
        } else {
            holder.text.setBackgroundResource(android.R.color.transparent);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(View view) {
            super(view);
            text = (TextView) view;
        }
    }
}
