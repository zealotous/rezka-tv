package me.rezka.tv.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.rezka.tv.R;
import me.rezka.tv.model.Category;
import me.rezka.tv.net.RezkaClient;

/** Wires the included rezka.ag-style top navigation. */
public final class TopNav {

    public interface Listener {
        void onCategory(Category category);
        void onSearch();
        void onHome();
    }

    private TopNav() {
    }

    public static void bind(Activity activity, String activeTitle, Listener listener) {
        RecyclerView list = activity.findViewById(R.id.topNavList);
        list.setLayoutManager(new LinearLayoutManager(
                activity, LinearLayoutManager.HORIZONTAL, false));
        list.setItemAnimator(null);
        TopNavAdapter adapter = new TopNavAdapter(new TopNavAdapter.OnCategory() {
            @Override
            public void onCategory(Category category) {
                listener.onCategory(category);
            }
        }, activeTitle);
        list.setAdapter(adapter);
        adapter.setItems(new RezkaClient().categories());

        activity.findViewById(R.id.topLogo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHome();
            }
        });
        activity.findViewById(R.id.topSearch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSearch();
            }
        });
    }

    public static void openCategory(Activity activity, Category category) {
        Intent intent = new Intent(activity, CategoryActivity.class);
        intent.putExtra(CategoryActivity.EXTRA_TITLE, category.title);
        intent.putExtra(CategoryActivity.EXTRA_URL, category.url);
        intent.putExtra(CategoryActivity.EXTRA_COLLECTIONS, category.collections);
        intent.putExtra(CategoryActivity.EXTRA_BEST_BLOCK, category.bestBlockId);
        activity.startActivity(intent);
    }
}
