package me.rezka.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import me.rezka.tv.data.FavoritesStore;

/**
 * TV context menu for a poster (hold OK): toggle favorites or open the
 * episode list on the detail screen.
 */
public final class CardMenu {

    private CardMenu() {
    }

    public static void show(final Context context, final String url,
                            final String title, final String poster) {
        if (url == null || url.isEmpty()) {
            return;
        }
        final boolean exists = FavoritesStore.contains(context, url);
        String favorite = exists ? "Удалить из избранного" : "Добавить в избранное";
        final String[] actions = {favorite, "Список эпизодов"};

        new AlertDialog.Builder(context)
                .setTitle(title == null || title.isEmpty() ? "Действия" : title)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            boolean added = FavoritesStore.toggle(context, url, title, poster);
                            Toast.makeText(context,
                                    added ? "Добавлено в избранное" : "Удалено из избранного",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Intent intent = new Intent(context, DetailActivity.class);
                            intent.putExtra(DetailActivity.EXTRA_URL, url);
                            if (!(context instanceof Activity)) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            }
                            context.startActivity(intent);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
