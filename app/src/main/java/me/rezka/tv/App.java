package me.rezka.tv;

import android.app.Application;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import me.rezka.tv.data.FavoritesStore;
import me.rezka.tv.net.AdBlock;
import me.rezka.tv.net.Tls;
import me.rezka.tv.ui.ImageLoader;

/**
 * Small process-wide singletons. Kept intentionally free of Google Play
 * Services: the Mijia Youth projector runs a Chinese MIUI TV build with no GMS.
 */
public class App extends Application {

    private static App sInstance;
    private static ImageLoader sImages;

    public static App get() {
        return sInstance;
    }

    public static ImageLoader images() {
        return sImages;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        Tls.install(this);

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookies);

        AdBlock.init(this);
        FavoritesStore.seedDefaults(this);
        sImages = new ImageLoader(this);
    }
}
