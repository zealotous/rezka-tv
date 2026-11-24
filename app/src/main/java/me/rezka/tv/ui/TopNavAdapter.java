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
import me.rezka.tv.model.Category;

/** Plain-text top navigation, mirroring the rezka.ag header. */
public class TopNavAdapter extends RecyclerView.Adapter<TopNavAdapter.Holder> {

    public interface OnCategory {
        void onCategory(Category category);
    }

    private static final int COLOR_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_NORMAL = 0xFFB3B3B3;

    private final List<Category> items = new ArrayList<>();
    private final OnCategory listener;
    private final String activeTitle;

    public TopNavAdapter(OnCategory listener, String activeTitle) {
        this.listener = listener;
        this.activeTitle = activeTitle == null ? "" : activeTitle;
    }

    public void setItems(List<Category> values) {
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
                .inflate(R.layout.item_top_nav, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final Category category = items.get(position);
        holder.text.setText(category.title);
        final boolean active = category.title.equals(activeTitle);

        holder.text.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyState(holder, active, hasFocus);
            }
        });
        holder.text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onCategory(category);
                }
            }
        });
        applyState(holder, active, holder.text.isFocused());
    }

    private void applyState(Holder holder, boolean active, boolean focused) {
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
