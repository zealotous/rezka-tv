package me.rezka.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.rezka.tv.R;
import me.rezka.tv.data.ProgressStore;
import me.rezka.tv.model.Anime;
import me.rezka.tv.model.Episode;
import me.rezka.tv.model.StreamInfo;
import me.rezka.tv.net.Http;
import me.rezka.tv.net.RezkaClient;

public class PlayerActivity extends Activity {

    public static final String EXTRA_ANIME_URL = "anime_url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_POSTER = "poster";
    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_TRANSLATOR_ID = "translator_id";
    public static final String EXTRA_TRANSLATOR_IDS = "translator_ids";
    public static final String EXTRA_TRANSLATOR_NAMES = "translator_names";
    public static final String EXTRA_EPISODE_NUMBERS = "episode_numbers";
    public static final String EXTRA_SEASONS = "seasons";
    public static final String EXTRA_LABELS = "labels";
    public static final String EXTRA_INDEX = "index";

    private static final long BACK_EXIT_WINDOW_MS = 2000L;

    private final RezkaClient client = new RezkaClient();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SimpleExoPlayer player;
    private PlayerView playerView;
    private ProgressBar loading;
    private TextView status;
    private View seekLeftOverlay;
    private View seekRightOverlay;
    private TextView seekLeftAmount;
    private TextView seekLeftTime;
    private TextView seekRightAmount;
    private TextView seekRightTime;
    private View nextButton;
    private View prevButton;
    private Button voiceButton;
    private Button voiceOverlay;
    private TextView voiceHint;

    private String animeUrl;
    private String animeTitle;
    private String animePoster;
    private String postId;
    private String translatorId;
    private String[] translatorIds;
    private String[] translatorNames;
    private int[] episodeNumbers;
    private int[] seasons;
    private String[] labels;
    private int index;

    private SharedPreferences progress;
    private boolean resumeApplied;
    private boolean destroyed;
    private int resolveToken;
    private int switchToken;
    private long pendingSeekMs = -1;
    private String lastError;
    private boolean controlsVisible;
    private long lastBackAt;
    private int seekDirection;
    private long seekAccumMs;
    private long lastSeekTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_player);

        animeUrl = getIntent().getStringExtra(EXTRA_ANIME_URL);
        animeTitle = getIntent().getStringExtra(EXTRA_TITLE);
        animePoster = getIntent().getStringExtra(EXTRA_POSTER);
        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        translatorId = getIntent().getStringExtra(EXTRA_TRANSLATOR_ID);
        translatorIds = getIntent().getStringArrayExtra(EXTRA_TRANSLATOR_IDS);
        translatorNames = getIntent().getStringArrayExtra(EXTRA_TRANSLATOR_NAMES);
        episodeNumbers = getIntent().getIntArrayExtra(EXTRA_EPISODE_NUMBERS);
        seasons = getIntent().getIntArrayExtra(EXTRA_SEASONS);
        labels = getIntent().getStringArrayExtra(EXTRA_LABELS);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);

        if (animeUrl == null || episodeNumbers == null || seasons == null
                || episodeNumbers.length == 0 || seasons.length == 0) {
            finish();
            return;
        }
        if (labels == null || labels.length != episodeNumbers.length) {
            labels = new String[episodeNumbers.length];
            for (int i = 0; i < episodeNumbers.length; i++) {
                labels[i] = String.valueOf(episodeNumbers[i]);
            }
        }

        progress = getSharedPreferences("progress_rezka", MODE_PRIVATE);

        playerView = findViewById(R.id.playerView);
        loading = findViewById(R.id.playerLoading);
        status = findViewById(R.id.playerStatus);
        seekLeftOverlay = findViewById(R.id.seekLeftOverlay);
        seekRightOverlay = findViewById(R.id.seekRightOverlay);
        seekLeftAmount = findViewById(R.id.seekLeftAmount);
        seekLeftTime = findViewById(R.id.seekLeftTime);
        seekRightAmount = findViewById(R.id.seekRightAmount);
        seekRightTime = findViewById(R.id.seekRightTime);
        playerView.setKeepScreenOn(true);
        playerView.setControllerShowTimeoutMs(8000);
        playerView.setControllerAutoShow(true);
        playerView.setControllerVisibilityListener(new PlayerControlView.VisibilityListener() {
            @Override
            public void onVisibilityChange(int visibility) {
                controlsVisible = visibility == View.VISIBLE;
                if (voiceOverlay != null) {
                    voiceOverlay.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
                }
                if (voiceHint != null) {
                    voiceHint.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
                }

            }
        });

        player = new SimpleExoPlayer.Builder(this,
                new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setTrackSelector(buildTrackSelector())
                .build();
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);

        // ExoPlayer's built-in prev/next buttons act on its media-item playlist.
        // This app resolves one episode at a time, so rewire those buttons to
        // the episode navigation used by the remote's MEDIA_NEXT key.
        nextButton = playerView.findViewById(R.id.episode_next);
        prevButton = playerView.findViewById(R.id.episode_prev);
        if (nextButton != null) {
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    next();
                }
            });
        }
        if (prevButton != null) {
            prevButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    previous();
                }
            });
        }

        // The voiceover picker lives in the player, exactly where the site's
        // player shows "В русской озвучке от".
        voiceButton = playerView.findViewById(R.id.voice_button);
        if (voiceButton != null) {
            voiceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showVoiceDialog();
                }
            });
        }

        // ExoPlayer's controller blocks D-pad focus on custom children, so the
        // remote-friendly picker is a focusable overlay above the controller.
        voiceOverlay = findViewById(R.id.voiceOverlay);
        voiceHint = findViewById(R.id.voiceHint);
        voiceOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVoiceDialog();
            }
        });
        updateVoiceButton();
        updateEpisodeButtons();

        player.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player p, Player.Events events) {
                updateEpisodeButtons();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == ExoPlayer.STATE_READY) {
                    loading.setVisibility(View.GONE);
                    applyResume();
                } else if (state == ExoPlayer.STATE_BUFFERING) {
                    loading.setVisibility(View.VISIBLE);
                } else if (state == ExoPlayer.STATE_ENDED) {
                    next();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e("RezkaTV", "ExoPlayer playback error", error);
                lastError = "ExoPlayer code " + error.errorCode + ": " + error.getMessage();
                fail();
            }
        });

        if (needsMetadata()) {
            loadMetadataThenPlay();
        } else {
            resolveCurrent();
        }
        main.postDelayed(saveTicker, 5000);
    }

    private boolean needsMetadata() {
        return postId == null || postId.isEmpty()
                || translatorId == null || translatorId.isEmpty()
                || translatorIds == null || translatorIds.length == 0;
    }

    /** Used when playback is resumed from "Продолжить просмотр". */
    private void loadMetadataThenPlay() {
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.loading);
        io.execute(new Runnable() {
            @Override
            public void run() {
                Anime anime = null;
                try {
                    anime = client.basicDetails(animeUrl);
                } catch (Exception ignored) {
                }
                final Anime loaded = anime;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed) {
                            return;
                        }
                        if (loaded != null) {
                            if (postId == null || postId.isEmpty()) {
                                postId = loaded.postId;
                            }
                            if ((translatorId == null || translatorId.isEmpty())
                                    && !loaded.translatorId.isEmpty()) {
                                translatorId = loaded.translatorId;
                            }
                            if (translatorIds == null || translatorIds.length == 0) {
                                int count = loaded.translators.size();
                                translatorIds = new String[count];
                                translatorNames = new String[count];
                                for (int i = 0; i < count; i++) {
                                    translatorIds[i] = loaded.translators.get(i).id;
                                    translatorNames[i] = loaded.translators.get(i).name;
                                }
                            }
                        }
                        updateVoiceButton();
                        if (postId == null || postId.isEmpty()
                                || translatorId == null || translatorId.isEmpty()) {
                            fail();
                        } else {
                            resolveCurrent();
                        }
                    }
                });
            }
        });
    }

    private final Runnable saveTicker = new Runnable() {
        @Override
        public void run() {
            if (destroyed) {
                return;
            }
            if (player != null && player.isPlaying()) {
                savePosition();
            }
            main.postDelayed(this, 5000);
        }
    };

    private final Runnable hideSeekOverlay = new Runnable() {
        @Override
        public void run() {
            if (seekLeftOverlay != null) {
                seekLeftOverlay.setVisibility(View.GONE);
            }
            if (seekRightOverlay != null) {
                seekRightOverlay.setVisibility(View.GONE);
            }
            seekDirection = 0;
            seekAccumMs = 0;
        }
    };

    /** YouTube-style 10-second seek; consecutive presses accumulate. */
    private void seekBy(int direction) {
        if (player == null) {
            return;
        }
        if (seekDirection != direction) {
            seekDirection = direction;
            seekAccumMs = 0;
            lastSeekTarget = player.getCurrentPosition();
        }
        long target = lastSeekTarget + direction * 10000L;
        if (target < 0) {
            target = 0;
        }
        long duration = player.getDuration();
        if (duration != C.TIME_UNSET && duration > 0 && target > duration - 1000L) {
            target = duration - 1000L;
        }
        lastSeekTarget = target;
        player.seekTo(target);
        seekAccumMs += direction * 10000L;

        boolean forward = direction > 0;
        View show = forward ? seekRightOverlay : seekLeftOverlay;
        View hide = forward ? seekLeftOverlay : seekRightOverlay;
        TextView amount = forward ? seekRightAmount : seekLeftAmount;
        TextView time = forward ? seekRightTime : seekLeftTime;
        if (show != null && amount != null) {
            if (hide != null) {
                hide.setVisibility(View.GONE);
            }
            String sign = forward ? "+" : "−";
            amount.setText(sign + (Math.abs(seekAccumMs) / 1000L) + " сек");
            if (time != null) {
                time.setText(ProgressStore.formatTime(target));
            }
            show.setVisibility(View.VISIBLE);
            main.removeCallbacks(hideSeekOverlay);
            main.postDelayed(hideSeekOverlay, 900);
        }
    }

    private DefaultTrackSelector buildTrackSelector() {
        DefaultTrackSelector selector = new DefaultTrackSelector(this);
        selector.setParameters(selector.buildUponParameters()
                .setMaxVideoSize(1920, 1080)
                .setMaxVideoBitrate(12_000_000));
        return selector;
    }

    // ------------------------------------------------------------- voiceovers

    private void showVoiceDialog() {
        if (translatorIds == null || translatorNames == null
                || translatorIds.length == 0 || translatorIds.length != translatorNames.length) {
            Toast.makeText(this, "Список озвучек ещё загружается", Toast.LENGTH_SHORT).show();
            return;
        }

        int checked = 0;
        for (int i = 0; i < translatorIds.length; i++) {
            if (translatorIds[i].equals(translatorId)) {
                checked = i;
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Озвучка")
                .setSingleChoiceItems(translatorNames, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                switchTranslator(which);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    /**
     * Switches the dub without leaving the player: fetches that voiceover's
     * episode list, finds the same season/episode and resumes at the same
     * position.
     */
    private void switchTranslator(int which) {
        if (translatorIds == null || which < 0 || which >= translatorIds.length) {
            return;
        }
        final String newId = translatorIds[which];
        final String newName = translatorNames[which];
        if (newId.equals(translatorId)) {
            return;
        }

        final long position = player != null ? player.getCurrentPosition() : 0L;
        final int wantSeason = seasons[index];
        final int wantNumber = episodeNumbers[index];

        // Films have no episode list: swap the voiceover and re-resolve the
        // same single stream, keeping the position.
        if (wantSeason <= 0) {
            translatorId = newId;
            pendingSeekMs = position;
            updateVoiceButton();
            Toast.makeText(this, "Озвучка: " + newName, Toast.LENGTH_SHORT).show();
            resolveCurrent();
            return;
        }

        final int token = ++switchToken;
        ++resolveToken; // cancel an in-flight resolve
        loading.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("Переключаем озвучку: " + newName);

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Anime list = client.episodes(postId, newId, animeUrl);
                    final int found = findEpisode(list.episodes, wantSeason, wantNumber);
                    if (found < 0) {
                        throw new IllegalStateException("В этой озвучке нет выбранной серии");
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed || token != switchToken) {
                                return;
                            }
                            int size = list.episodes.size();
                            int[] newSeasons = new int[size];
                            int[] newNumbers = new int[size];
                            String[] newLabels = new String[size];
                            for (int i = 0; i < size; i++) {
                                Episode episode = list.episodes.get(i);
                                newSeasons[i] = episode.season;
                                newNumbers[i] = episode.number;
                                newLabels[i] = episode.label;
                            }
                            seasons = newSeasons;
                            episodeNumbers = newNumbers;
                            labels = newLabels;
                            index = found;
                            translatorId = newId;
                            pendingSeekMs = position;
                            updateVoiceButton();
                            updateEpisodeButtons();
                            Toast.makeText(PlayerActivity.this,
                                    "Озвучка: " + newName, Toast.LENGTH_SHORT).show();
                            resolveCurrent();
                        }
                    });
                } catch (final Exception e) {
                    Log.e("RezkaTV", "voiceover switch failed", e);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (destroyed || token != switchToken) {
                                return;
                            }
                            loading.setVisibility(View.GONE);
                            updateVoiceButton();
                            Toast.makeText(PlayerActivity.this,
                                    e.getMessage() == null ? "Не удалось сменить озвучку"
                                            : e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    /** Exact episode first, then a merged label ("20-21"), then the season's last. */
    private static int findEpisode(List<Episode> episodes, int season, int number) {
        for (int i = 0; i < episodes.size(); i++) {
            Episode episode = episodes.get(i);
            if (episode.season == season && episode.number == number) {
                return i;
            }
        }
        for (int i = 0; i < episodes.size(); i++) {
            Episode episode = episodes.get(i);
            if (episode.season == season && labelCovers(episode.label, number)) {
                return i;
            }
        }
        int lastInSeason = -1;
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).season == season) {
                lastInSeason = i;
            }
        }
        return lastInSeason;
    }

    private static boolean labelCovers(String label, int number) {
        if (label == null) {
            return false;
        }
        String[] parts = label.split("[-–—]");
        if (parts.length < 2) {
            return false;
        }
        try {
            int from = Integer.parseInt(parts[0].trim());
            int to = Integer.parseInt(parts[1].trim());
            return number >= Math.min(from, to) && number <= Math.max(from, to);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void updateVoiceButton() {
        String name = currentTranslatorName();
        String display = name.isEmpty() ? "Озвучка" : "Озвучка: " + name;
        if (display.length() > 26) {
            display = display.substring(0, 25) + "…";
        }
        boolean available = translatorIds != null && translatorIds.length > 1;
        if (voiceButton != null) {
            voiceButton.setText(display);
            voiceButton.setEnabled(available);
            voiceButton.setAlpha(available ? 1f : 0.4f);
        }
        if (voiceOverlay != null) {
            voiceOverlay.setText(display);
            voiceOverlay.setEnabled(available);
            voiceOverlay.setAlpha(available ? 1f : 0.4f);
        }
    }

    private String currentTranslatorName() {
        if (translatorIds == null || translatorNames == null) {
            return "";
        }
        for (int i = 0; i < translatorIds.length && i < translatorNames.length; i++) {
            if (translatorIds[i].equals(translatorId)) {
                return translatorNames[i];
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- playback

    private void resolveCurrent() {
        if (index < 0 || index >= episodeNumbers.length || index >= seasons.length) {
            finish();
            return;
        }
        loading.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.resolving);
        resumeApplied = false;
        lastError = null;
        seekDirection = 0;
        seekAccumMs = 0;
        if (seekLeftOverlay != null) {
            seekLeftOverlay.setVisibility(View.GONE);
        }
        if (seekRightOverlay != null) {
            seekRightOverlay.setVisibility(View.GONE);
        }
        updateEpisodeButtons();
        updateVoiceButton();

        final int token = ++resolveToken;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final StreamInfo stream = client.resolve(postId, translatorId,
                            seasons[index], episodeNumbers[index], animeUrl);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed && token == resolveToken) {
                                start(stream);
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e("RezkaTV", "stream resolve failed", e);
                    lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!destroyed && token == resolveToken) {
                                fail();
                            }
                        }
                    });
                }
            }
        });
    }

    private void start(StreamInfo stream) {
        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory()
                .setUserAgent(Http.UA)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(25000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(stream.headers);

        MediaSource source;
        MediaItem item = MediaItem.fromUri(stream.url);
        if ("mp4".equals(stream.type)) {
            source = new ProgressiveMediaSource.Factory(factory).createMediaSource(item);
        } else {
            source = new HlsMediaSource.Factory(factory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(item);
        }

        player.setMediaSource(source);
        player.prepare();

        long saved = pendingSeekMs > 0 ? pendingSeekMs : progress.getLong(progressKey(), 0L);
        pendingSeekMs = -1;
        if (saved > 5000L) {
            player.seekTo(saved);
            resumeApplied = true;
            Toast toast = Toast.makeText(this, getString(R.string.resume_from, formatTime(saved)),
                    Toast.LENGTH_SHORT);
            toast.setGravity(android.view.Gravity.TOP, 0, me.rezka.tv.ui.Ui.dp(this, 150));
            toast.show();
        }

        player.setPlayWhenReady(true);
        playerView.requestFocus();

        boolean resuming = saved > 5000L;
        playerView.setControllerAutoShow(!resuming);
        if (resuming) {
            controlsVisible = false;
            playerView.hideController();
        } else {
            controlsVisible = true;
            playerView.showController();
        }

        status.setText(episodeLabel());
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!destroyed) {
                    status.setVisibility(View.GONE);
                }
            }
        }, 4000);
    }

    private void applyResume() {
        if (resumeApplied || player == null) {
            return;
        }
        resumeApplied = true;
        long saved = progress.getLong(progressKey(), 0L);
        long duration = player.getDuration();
        if (saved > 5000L && (duration == C.TIME_UNSET || duration > saved + 5000L)) {
            player.seekTo(saved);
        }
    }

    private void savePosition() {
        if (player == null || destroyed) {
            return;
        }
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (position > 3000L) {
            progress.edit().putLong(progressKey(), position).apply();
            ProgressStore.save(this, animeUrl, animeTitle, animePoster, postId, translatorId,
                    index, episodeNumbers, seasons, labels, position, duration);
        }
    }

    private String progressKey() {
        int number = episodeNumbers != null && index < episodeNumbers.length
                ? episodeNumbers[index] : index + 1;
        int season = seasons != null && index < seasons.length ? seasons[index] : 0;
        return animeUrl + "|" + season + "x" + number;
    }

    private void updateEpisodeButtons() {
        if (nextButton != null) {
            boolean hasNext = episodeNumbers != null && index + 1 < episodeNumbers.length;
            nextButton.setEnabled(hasNext);
            nextButton.setAlpha(hasNext ? 1f : 0.35f);
        }
        if (prevButton != null) {
            boolean hasPrev = index > 0;
            prevButton.setEnabled(hasPrev);
            prevButton.setAlpha(hasPrev ? 1f : 0.35f);
        }
    }

    private void next() {
        if (episodeNumbers != null && index + 1 < episodeNumbers.length) {
            savePosition();
            index++;
            updateEpisodeButtons();
            resolveCurrent();
        } else {
            finish();
        }
    }

    private void previous() {
        if (index > 0) {
            savePosition();
            index--;
            updateEpisodeButtons();
            resolveCurrent();
        } else if (player != null) {
            player.seekTo(0);
        }
    }

    private void fail() {
        loading.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.resolve_error);
        if (isFinishing() || destroyed) {
            return;
        }

        String message = getString(R.string.resolve_error);
        if (lastError != null && !lastError.isEmpty()) {
            message += "\n\n" + lastError;
        }

        new AlertDialog.Builder(this)
                .setTitle("Ошибка воспроизведения")
                .setMessage(message)
                .setPositiveButton("Повторить", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resolveCurrent();
                    }
                })
                .setNeutralButton("Сменить озвучку", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showVoiceDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private String episodeLabel() {
        int number = episodeNumbers != null && index < episodeNumbers.length
                ? episodeNumbers[index] : index + 1;
        String label = labels != null && index < labels.length
                ? labels[index] : String.valueOf(number);
        int season = seasons != null && index < seasons.length ? seasons[index] : 0;
        String prefix = season > 0 ? "Сезон " + season + "  •  Серия " + label : "Фильм";
        String voice = currentTranslatorName();
        String title = animeTitle != null ? animeTitle : "";
        return prefix + "  •  " + (voice.isEmpty() ? title : voice + "  •  " + title);
    }

    private static String formatTime(long ms) {
        long total = ms / 1000;
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                long now = SystemClock.elapsedRealtime();
                if (lastBackAt > 0 && now - lastBackAt <= BACK_EXIT_WINDOW_MS) {
                    // Double-back: leave the player.
                    savePosition();
                    finish();
                } else {
                    lastBackAt = now;
                    if (controlsVisible) {
                        controlsVisible = false;
                        playerView.hideController();
                    } else {
                        Toast.makeText(this, R.string.back_exit_hint,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && playerView != null) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK || code == KeyEvent.KEYCODE_BUTTON_Y) {
                showVoiceDialog();
                return true;
            }
            if (code == KeyEvent.KEYCODE_MENU) {
                showVoiceDialog();
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && playerView != null
                && !controlsVisible) {
            int code = event.getKeyCode();
            if (isCenterKey(code)) {
                controlsVisible = true;
                playerView.showController();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                seekBy(1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                seekBy(-1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                showVoiceDialog();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                controlsVisible = true;
                playerView.showController();
                // Let the event through so it can still seek / move focus.
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static boolean isCenterKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_BUTTON_SELECT
                || code == KeyEvent.KEYCODE_BUTTON_START
                || code == KeyEvent.KEYCODE_SPACE;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            next();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
            previous();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePosition();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastBackAt = 0;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(saveTicker);
        main.removeCallbacks(hideSeekOverlay);
        savePosition();
        io.shutdownNow();
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
