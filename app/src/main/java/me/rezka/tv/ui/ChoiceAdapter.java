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

/** Small horizontal chip picker used for seasons. */
public class ChoiceAdapter extends RecyclerView.Adapter<ChoiceAdapter.Holder> {

    public interface OnChoice {
        void onChoice(int index);
    }

    private final List<String> items = new ArrayList<>();
    private final OnChoice listener;
    private int selected = -1;

    public ChoiceAdapter(OnChoice listener) {
        this.listener = listener;
    }

    public void setItems(List<String> values, int selectedIndex) {
        items.clear();
        if (values != null) {
            items.addAll(values);
        }
        selected = selectedIndex;
        notifyDataSetChanged();
    }

    public void setSelected(int index) {
        if (selected == index) {
            return;
        }
        selected = index;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chip, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        holder.text.setText(items.get(position));
        boolean isSelected = position == selected;
        holder.text.setBackgroundResource(isSelected
                ? R.drawable.bg_chip_selected : R.drawable.selector_chip);
        holder.text.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = holder.getAdapterPosition();
                if (index != RecyclerView.NO_POSITION) {
                    setSelected(index);
                    if (listener != null) {
                        listener.onChoice(index);
                    }
                }
            }
        });
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
