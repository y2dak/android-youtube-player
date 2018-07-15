package com.pierfrancescosoffritti.androidyoutubeplayer.player;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.net.ConnectivityManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.pierfrancescosoffritti.androidyoutubeplayer.R;
import com.pierfrancescosoffritti.androidyoutubeplayer.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.player.listeners.YouTubePlayerFullScreenListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.player.listeners.YouTubePlayerInitListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.player.playerUtils.FullScreenHelper;
import com.pierfrancescosoffritti.androidyoutubeplayer.player.playerUtils.PlaybackResumer;
import com.pierfrancescosoffritti.androidyoutubeplayer.ui.DefaultPlayerUIController;
import com.pierfrancescosoffritti.androidyoutubeplayer.ui.PlayerUIController;
import com.pierfrancescosoffritti.androidyoutubeplayer.utils.Callable;
import com.pierfrancescosoffritti.androidyoutubeplayer.utils.NetworkReceiver;
import com.pierfrancescosoffritti.androidyoutubeplayer.utils.Utils;

public class YouTubePlayerView extends FrameLayout implements NetworkReceiver.NetworkListener, LifecycleObserver {

    @NonNull private final WebViewYouTubePlayer youTubePlayer;
    @Nullable private DefaultPlayerUIController defaultPlayerUIController;

    @NonNull private final NetworkReceiver networkReceiver;
    @NonNull private final PlaybackResumer playbackResumer;
    @NonNull private final FullScreenHelper fullScreenHelper;
    @Nullable private Callable asyncInitialization;

    private final int aspectHeight;
    private final int aspectWidth;

    public YouTubePlayerView(Context context) {
        this(context, null);
    }

    public YouTubePlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YouTubePlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = getContext().getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.YouTubePlayerView,
                0, 0);

        try {
            aspectHeight = a.getInt(R.styleable.YouTubePlayerView_aspectHeight, 9);
            aspectWidth = a.getInt(R.styleable.YouTubePlayerView_aspectWidth, 16);
        } finally {
            a.recycle();
        }
        youTubePlayer = new WebViewYouTubePlayer(context);
        addView(youTubePlayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        defaultPlayerUIController = new DefaultPlayerUIController(this, youTubePlayer);

        playbackResumer = new PlaybackResumer();
        networkReceiver = new NetworkReceiver(this);
        fullScreenHelper = new FullScreenHelper();

        fullScreenHelper.addFullScreenListener(defaultPlayerUIController);
        addYouTubePlayerListeners(youTubePlayer);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // if height == wrap content make the view 16:9
        if(getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            int sixteenNineHeight = View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec) * aspectHeight / aspectWidth, View.MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, sixteenNineHeight);
        } else
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Initialize the player
     * @param youTubePlayerInitListener lister for player init events
     * @param handleNetworkEvents if <b>true</b> a broadcast receiver will be registered.<br/>If <b>false</b> you should handle network events with your own broadcast receiver. See {@link YouTubePlayerView#onNetworkAvailable()} and {@link YouTubePlayerView#onNetworkUnavailable()}
     */
    public void initialize(@NonNull final YouTubePlayerInitListener youTubePlayerInitListener, boolean handleNetworkEvents) {
        Log.d("YOUTUBEPLAYERVIEW", "initialize");
        if(handleNetworkEvents)
            getContext().registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        asyncInitialization = new Callable() {
            @Override
            public void call() {
                youTubePlayer.initialize(new YouTubePlayerInitListener() {
                    @Override
                    public void onInitSuccess(@NonNull YouTubePlayer youTubePlayer) {
                        youTubePlayerInitListener.onInitSuccess(youTubePlayer);
                    }
                });
            }
        };

        if(Utils.isOnline(getContext()))
            asyncInitialization.call();
    }

    /**
     * Calls {@link WebView#destroy()} on the player. And unregisters the broadcast receiver (for network events), if registered.
     * Call this method before destroying the host Fragment/Activity, or register this View as an observer of its host lifcecycle
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void release() {
        Log.d("YOUTUBEPLAYERVIEW", "release");
        removeView(youTubePlayer);
        youTubePlayer.removeAllViews();
        youTubePlayer.destroy();
        try {
            getContext().unregisterReceiver(networkReceiver);
        } catch (Exception ignore) {
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    void onStop() {
        Log.d("YOUTUBEPLAYERVIEW", "onStop");
        youTubePlayer.pause();
    }

    @Override
    public void onNetworkAvailable() {
        Log.d("YOUTUBEPLAYERVIEW", "onNetworkAvailable");
        if(asyncInitialization != null)
            asyncInitialization.call();
        else
            playbackResumer.resume(youTubePlayer);
    }

    @Override
    public void onNetworkUnavailable() {
    }

    @NonNull
    public PlayerUIController getPlayerUIController() {
        if(defaultPlayerUIController == null)
            throw new RuntimeException("You have inflated a custom player UI. You must manage it with your own controller.");

        return defaultPlayerUIController;
    }

    /**
     * Replaces the default UI of the player with a custom UI.<br/>
     * You will have to control the custom UI in your application,
     * the default controller obtained through {@link YouTubePlayerView#getPlayerUIController()} won't be available anymore.
     * @param customPlayerUILayoutID the ID of the layout defining the custom UI.
     * @return The inflated View
     */
    public View inflateCustomPlayerUI(@LayoutRes int customPlayerUILayoutID) {
        removeViews(1, this.getChildCount()-1);

        if (defaultPlayerUIController != null) {
            youTubePlayer.removeListener(defaultPlayerUIController);
            fullScreenHelper.removeFullScreenListener(defaultPlayerUIController);
        }

        defaultPlayerUIController = null;

        return View.inflate(getContext(), customPlayerUILayoutID, this);
    }

    public void enterFullScreen() {
        fullScreenHelper.enterFullScreen(this);
    }

    public void exitFullScreen() {
        fullScreenHelper.exitFullScreen(this);
    }

    public boolean isFullScreen() {
        return fullScreenHelper.isFullScreen();
    }

    public void toggleFullScreen() {
        fullScreenHelper.toggleFullScreen(this);
    }

    public boolean addFullScreenListener(@NonNull YouTubePlayerFullScreenListener fullScreenListener) {
        return fullScreenHelper.addFullScreenListener(fullScreenListener);
    }

    public boolean removeFullScreenListener(@NonNull YouTubePlayerFullScreenListener fullScreenListener) {
        return fullScreenHelper.removeFullScreenListener(fullScreenListener);
    }

    private void addYouTubePlayerListeners(final YouTubePlayer youTubePlayer) {
        if (defaultPlayerUIController != null) youTubePlayer.addListener(defaultPlayerUIController);
        youTubePlayer.addListener(playbackResumer);
        youTubePlayer.addListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady() {
                asyncInitialization = null;
                //youTubePlayer.removeListener(this);
            }
        });
    }
}
