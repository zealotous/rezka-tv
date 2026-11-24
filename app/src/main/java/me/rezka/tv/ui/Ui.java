package me.rezka.tv.ui;

import android.content.Context;
import android.view.View;

public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static void focus(View view, boolean focused) {
        float target = focused ? 1.06f : 1f;
        view.animate()
                .scaleX(target)
                .scaleY(target)
                .setDuration(110)
                .start();
        view.setElevation(focused ? dp(view.getContext(), 12) : 0);
    }
}
