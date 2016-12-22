package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;

/*
* Just display HLS video surface view
*
* */
public class VideoPlayer extends AppCompatActivity implements HlsSampleSource.EventListener, View.OnClickListener {

    private static final String TAG = "VideoPlayer";

    public static final int RENDERER_COUNT = 2;
    public static final int TYPE_AUDIO = 1;
    private ExoPlayer player;
    private SurfaceView surface;
    private String video_url, video_type;
    private Handler mainHandler;
    private HpLib_RendererBuilder hpLibRendererBuilder;
    private TrackRenderer videoRenderer;
    private LinearLayout root;
    public static final int TYPE_VIDEO = 0;

    private View decorView;
    private int uiImmersiveOptions;
    private RelativeLayout loadingPanel;
    private Runnable updatePlayer;

    {
        /*
        * Show or hide the loadingPanel, depending on the state of the video
        * */
        updatePlayer = new Runnable() {
            @Override
            public void run() {
                switch (player.getPlaybackState()) {
                    case ExoPlayer.STATE_BUFFERING:
                        loadingPanel.setVisibility(View.VISIBLE);
                        break;
                    case ExoPlayer.STATE_ENDED:
                        finish();
                        break;
                    case ExoPlayer.STATE_IDLE:
                        loadingPanel.setVisibility(View.GONE);
                        break;
                    case ExoPlayer.STATE_PREPARING:
                        loadingPanel.setVisibility(View.VISIBLE);
                        break;
                    case ExoPlayer.STATE_READY:
                        loadingPanel.setVisibility(View.GONE);
                        break;
                    default:
                        break;
                }
                // Updates player every 200 ms
                mainHandler.postDelayed(updatePlayer, 200);
            }
        };
    }

    // Hide bars, show preloader, stop video on back press
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        // This bars appears at the beggining, if there is no activity; these bars get hidden
        uiImmersiveOptions = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        // We get the current decorview
        decorView = getWindow().getDecorView();
        // Based on the flags and the decor view we see if we need to hide or show the bars
        decorView.setSystemUiVisibility(uiImmersiveOptions);
        // Get reference to the load panel
        loadingPanel = (RelativeLayout) findViewById(R.id.loadingVPanel);

        root = (LinearLayout) findViewById(R.id.root);
        root.setVisibility(View.GONE);

        // Reference to the surface where we display the video
        surface = (SurfaceView) findViewById(R.id.surface_view);

        // Type of video
        video_type = "hls";
        // Video url
        video_url =  "http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8";
        // To display the video we will need a handler
        mainHandler = new Handler();

        execute();
    }

    private void execute() {
        // RENDERER_COUNT = 2 -> audio and video
        player = ExoPlayer.Factory.newInstance(RENDERER_COUNT);
        if (player != null) {
            hpLibRendererBuilder = getHpLibRendererBuilder();
            // the builder calls AsyncRendererBuilder
            hpLibRendererBuilder.buildRenderers(this);
            // Show loading panel at the beginning
            loadingPanel.setVisibility(View.VISIBLE);
            // Init runnable every 200 ms
            mainHandler.postDelayed(updatePlayer, 200);
        }
    }

    private HpLib_RendererBuilder getHpLibRendererBuilder() {
        // Fill user agent
        String userAgent = Util.getUserAgent(this, "HpLib");
        // So we can support various types of video
        switch (video_type){
            case "hls":
                // activity, user_agent (required for hls) and video_url
                return new HpLib_HlsHpLibRendererBuilder(this, userAgent, video_url);
            default:
                throw new IllegalStateException("Unsupported type: " + video_url);
        }
    }

    Handler getMainHandler() {
        return mainHandler;
    }

    void onRenderersError(Exception e) {
    }

    // Called from onSingleManifest inside HpLib_HlsHpLibRendererBuilder.AsyncRendererBuilder
    void onRenderers(TrackRenderer[] renderers, BandwidthMeter bandwidthMeter) {
        for (int i = 0; i < renderers.length; i++) {
            if (renderers[i] == null) {
                renderers[i] = new DummyTrackRenderer();
            }
        }
        // Complete preparation: Pushes video to surface and plays it inmediatly
        this.videoRenderer = renderers[TYPE_VIDEO];
        pushSurface(false);
        // Inject the renderers through prepare.
        player.prepare(renderers);
        player.setPlayWhenReady(true);
    }

    // Puts the video on the surface
    private void pushSurface(boolean blockForSurfacePush) {
        Log.d(TAG, "Thread_pushSurface: " + Thread.currentThread());
        if (videoRenderer == null) {return;}
        if (blockForSurfacePush) {
            /**
             * Blocking variant of {@link #sendMessage(ExoPlayer.ExoPlayerComponent, int, Object)} that does not return
             * until after the message has been delivered.
             *
             * @param target The target to which the message should be delivered.
             * @param messageType An integer that can be used to identify the type of the message.
             * @param message The message object.
             */
            player.blockingSendMessage(
                    videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface.getHolder().getSurface());
        } else {
            /**
             * Sends a message to a specified component. The message is delivered to the component on the
             * playback thread. If the component throws a {@link ExoPlaybackException}, then it is
             * propagated out of the player as an error.
             *
             * @param target The target to which the message should be delivered.
             * @param messageType An integer that can be used to identify the type of the message.
             * @param message The message object.
             */
            // 4. Pass the surface to the video renderer.
            player.sendMessage(
                    videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface.getHolder().getSurface());
        }
    }

    // Kills player
    private void killPlayer(){
        if (player != null) {
            player.release();
        }
    }

    // Kills player after activity has been closed
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        killPlayer();
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs) {

    }

    @Override
    public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {

    }

    @Override
    public void onLoadCanceled(int sourceId, long bytesLoaded) {

    }

    @Override
    public void onLoadError(int sourceId, IOException e) {

    }

    @Override
    public void onUpstreamDiscarded(int sourceId, long mediaStartTimeMs, long mediaEndTimeMs) {

    }

    @Override
    public void onDownstreamFormatChanged(int sourceId, Format format, int trigger, long mediaTimeMs) {

    }
}