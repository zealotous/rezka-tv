package me.rezka.tv.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.rezka.tv.R;
import me.rezka.tv.net.Http;

/**
 * Minimal poster loader: memory LruCache + raw disk cache + 4 worker threads.
 * Deliberately avoids Glide/Picasso so the APK stays small and starts fast on
 * the projector's Cortex-A53.
 */
public class ImageLoader {

    private final LruCache<String, Bitmap> memory;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File diskDir;

    public ImageLoader(Context context) {
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 7);
        maxKb = Math.min(maxKb, 48 * 1024);
        memory = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        diskDir = new File(context.getCacheDir(), "posters");
        if (!diskDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            diskDir.mkdirs();
        }
    }

    public void load(final ImageView view, final String url) {
        view.setTag(R.id.tag_image_url, url);
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            view.setBackgroundColor(0xFF15151C);
            return;
        }

        Bitmap cached = memory.get(url);
        if (cached != null) {
            view.setBackgroundColor(0);
            view.setImageBitmap(cached);
            return;
        }

        view.setImageDrawable(null);
        view.setBackgroundColor(0xFF15151C);

        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = loadFromDisk(url);
                if (bitmap == null) {
                    bitmap = download(url);
                    if (bitmap != null) {
                        saveToDisk(url, bitmap);
                    }
                }
                if (bitmap == null) {
                    return;
                }
                memory.put(url, bitmap);
                final Bitmap result = bitmap;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(view.getTag(R.id.tag_image_url))) {
                            view.setBackgroundColor(0);
                            view.setImageBitmap(result);
                        }
                    }
                });
            }
        });
    }

    private Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", Http.UA);
            connection.setRequestProperty("Accept", "image/*,*/*;q=0.8");
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(32 * 1024);
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            byte[] bytes = out.toByteArray();
            return decode(bytes);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Bitmap loadFromDisk(String url) {
        File file = fileFor(url);
        if (!file.exists()) {
            return null;
        }
        return decodeFile(file);
    }

    private void saveToDisk(String url, Bitmap bitmap) {
        File file = fileFor(url);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
        } catch (Exception ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Bitmap decode(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private Bitmap decodeFile(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth);
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private int sampleSize(int width) {
        int sample = 1;
        while (width / sample > 520) {
            sample *= 2;
        }
        return sample;
    }

    private File fileFor(String url) {
        return new File(diskDir, md5(url));
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
            }
            return builder.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }
}
