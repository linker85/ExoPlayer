package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteButton;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/*
* Just display HLS video surface view
* Implementing play/pause, next, previous, forward 30 sec and reverse 30 sec, mp4, currentTrackIndex
* */
public class VideoPlayer extends AppCompatActivity implements HlsSampleSource.EventListener, View.OnClickListener {

    private static final String TAG = "VideoPlayer";

    public static final int RENDERER_COUNT = 2;
    public static final int TYPE_AUDIO = 1;
    private ExoPlayer player;
    private SurfaceView surface;
    private String[] video_url, video_type, video_title;
    private int currentTrackIndex;
    private Handler mainHandler;
    private HpLib_RendererBuilder hpLibRendererBuilder;
    private TrackRenderer videoRenderer;
    private LinearLayout root;
    public static final int TYPE_VIDEO = 0;

    private View decorView;
    private int uiImmersiveOptions;
    private RelativeLayout loadingPanel;
    private Runnable updatePlayer;

    //Implementing the top bar
    private ImageButton btn_back;
    private TextView txt_title;

    //Implementing Chromecast
    public MediaRouteButton mMediaRouteButton;
    private CastContext mCastContext;
    private CastSession mCastSession;
    private PlaybackState mPlaybackState;
    private SessionManager mSessionManager;
    private MediaItem mSelectedMedia;

    //Implementing current time, total time and seekbar
    private TextView txt_ct,txt_td;
    private SeekBar seekBar;
    private PlayerControl playerControl;
    public enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    private ImageButton btn_play;
    private ImageButton btn_pause;
    private ImageButton btn_fwd;
    private ImageButton btn_rev;
    private ImageButton btn_next;
    private ImageButton btn_prev;

    // Session manager for the chrome cast
    private final SessionManagerListener<CastSession> mSessionManagerListener = new SessionManagerListenerImpl();

    private class SessionManagerListenerImpl implements SessionManagerListener<CastSession> {
        @Override
        public void onSessionStarting(CastSession session) {

        }

        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            // For the chrome cast -> When connecting to a tv
            onApplicationConnected(session);
        }

        @Override
        public void onSessionStartFailed(CastSession session, int i) {

        }

        @Override
        public void onSessionEnding(CastSession session) {

        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            // For the chrome cast -> When connection is broken and re-connected
            onApplicationConnected(session);
        }

        @Override
        public void onSessionResumeFailed(CastSession session, int i) {

        }

        @Override
        public void onSessionSuspended(CastSession session, int i) {

        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            finish();
        }

        @Override
        public void onSessionResuming(CastSession session, String s) {

        }
    }

    private void onApplicationConnected(CastSession castSession) {
        // Get session from the listener
        mCastSession = castSession;
        // Load the url from a remote server
        loadRemoteMedia(0,true);
    }

    private MediaInfo buildMediaInfo() {
        // Meta data from the video for the chromecast
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);

        mSelectedMedia = new MediaItem();
        mSelectedMedia.setUrl(video_url[currentTrackIndex]);
        mSelectedMedia.setContentType(video_type[currentTrackIndex]);
        mSelectedMedia.setTitle(video_title[currentTrackIndex]);

        movieMetadata.putString(MediaMetadata.KEY_TITLE, mSelectedMedia.getTitle());

        return new MediaInfo.Builder(mSelectedMedia.getUrl())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("hls")
                .setMetadata(movieMetadata)
                .setStreamDuration(mSelectedMedia.getDuration() * 1000)
                .build();
    }

    // For the chrome cast
    private void loadRemoteMedia(int position, boolean autoPlay) {
        // If there is no session in the chromecast dont do anything
        if (mCastSession == null) {
            return;
        }
        // Get RemoteMediaClient from the session
        final RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        // Listen the client
        remoteMediaClient.addListener(new RemoteMediaClient.Listener() {
            // When chromecast accepts the video
            @Override
            public void onStatusUpdated() {
                // Start another activity with the controls of the chromecast
                Intent intent = new Intent(VideoPlayer.this, ExpandedControlsActivity.class);
                startActivityForResult(intent,200);
                // Remove the listener, because we don´t require it
                remoteMediaClient.removeListener(this);
                if (playerControl.isPlaying()) {
                    playerControl.pause();
                }
            }

            @Override
            public void onMetadataUpdated() {
            }

            @Override
            public void onQueueStatusUpdated() {
            }

            @Override
            public void onPreloadStatusUpdated() {
            }

            @Override
            public void onSendingRemoteMediaRequest() {
            }
        });
        // Loads the media client (MediaInfoObject[infor about the video], should autoPlay, from where to start the video)
        remoteMediaClient.load(buildMediaInfo(), autoPlay, position);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // check if the request code is same as what is passed  here it is 2
        if (requestCode==200) {
            int currTime = data.getIntExtra("currTime",0);
            player.seekTo(currTime);
        }
    }

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

                // Get total duration
                String totDur = String.format("%02d.%02d.%02d",
                        TimeUnit.MILLISECONDS.toHours(player.getDuration()),
                        TimeUnit.MILLISECONDS.toMinutes(player.getDuration()) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(player.getDuration())), // The change is in this line
                        TimeUnit.MILLISECONDS.toSeconds(player.getDuration()) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(player.getDuration())));
                // Get current duration
                String curDur = String.format("%02d.%02d.%02d",
                        TimeUnit.MILLISECONDS.toHours(player.getCurrentPosition()),
                        TimeUnit.MILLISECONDS.toMinutes(player.getCurrentPosition()) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(player.getCurrentPosition())), // The change is in this line
                        TimeUnit.MILLISECONDS.toSeconds(player.getCurrentPosition()) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(player.getCurrentPosition())));
                // Set current duration
                txt_ct.setText(curDur);
                // Set total duration
                txt_td.setText(totDur);
                // Set max duration in the seekbar
                seekBar.setMax((int) player.getDuration());
                // Set progress in the seekbar
                seekBar.setProgress((int) player.getCurrentPosition());


                // Updates player every 200 ms
                mainHandler.postDelayed(updatePlayer, 200);
            }
        };
    }

    // Top bar (Back button, video title, chrome cast) and Seekbar (Current duration, total duration)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        //Chromecast
        LinearLayout cast_container = (LinearLayout) findViewById(R.id.cast_container);
        mMediaRouteButton = new MediaRouteButton(this);
        // Add button of chrome cast to the layout
        cast_container.addView(mMediaRouteButton);
        // Initiate the button
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), mMediaRouteButton);
        // Initiate the context
        mCastContext = CastContext.getSharedInstance(this);
        // Get the session manager
        mCastContext.getSessionManager().addSessionManagerListener(
                mSessionManagerListener, CastSession.class);

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

        // Bottom bar (Seek bar)
        txt_ct = (TextView) findViewById(R.id.txt_currentTime);
        txt_td = (TextView) findViewById(R.id.txt_totalDuration);
        seekBar = (SeekBar) findViewById(R.id.seekbar);
        // Changes position of the seekbar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Changes position of the video
                player.seekTo(seekBar.getProgress());
            }
        });

        // Buttons
        btn_back  = (ImageButton) findViewById(R.id.btn_back);
        btn_play  = (ImageButton) findViewById(R.id.btn_play);
        btn_pause = (ImageButton) findViewById(R.id.btn_pause);
        btn_fwd   = (ImageButton) findViewById(R.id.btn_fwd);
        btn_rev   = (ImageButton) findViewById(R.id.btn_rev);
        btn_prev  = (ImageButton) findViewById(R.id.btn_prev);
        btn_next  = (ImageButton) findViewById(R.id.btn_next);

        // Listeners
        btn_back.setOnClickListener(this);
        btn_play.setOnClickListener(this);
        btn_pause.setOnClickListener(this);
        btn_fwd.setOnClickListener(this);
        btn_rev.setOnClickListener(this);
        btn_prev.setOnClickListener(this);
        btn_next.setOnClickListener(this);

        txt_title = (TextView) findViewById(R.id.txt_title);

        root = (LinearLayout) findViewById(R.id.root);
        root.setVisibility(View.VISIBLE);

        // Reference to the surface where we display the video
        surface = (SurfaceView) findViewById(R.id.surface_view);

        currentTrackIndex = 0;

        // Type of video
        video_type = new String[]{"hls", "others"};
        // Video url
        video_url = new String[]{"http://playertest.longtailvideo.com/adaptive/bbbfull/bbbfull.m3u8","http://player.hungama.com/mp3/91508493.mp4"};
        // Set video title
        video_title = new String[]{"Big Buck Bunny","Movie Trailer"};
        txt_title.setText(video_title[currentTrackIndex]);

        // To display the video we will need a handler
        mainHandler = new Handler();

        execute();
    }

    private void execute() {
        // RENDERER_COUNT = 2 -> audio and video
        player        = ExoPlayer.Factory.newInstance(RENDERER_COUNT);
        // Add the player to the control
        playerControl = new PlayerControl(player);
        // Manipulate the currenTrackIndex
        if (currentTrackIndex >= video_title.length) {
            currentTrackIndex = (video_title.length-1);
        } else if(currentTrackIndex <= 0) {
            currentTrackIndex = 0;
        }
        // Update the title
        txt_title.setText(video_title[currentTrackIndex]);

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

    @Override
    public void onClick(View v) {
        int i1 = v.getId();
        // If btn back is pressed, kill the player and finish the activity
        if (i1 == R.id.btn_back) {
            killPlayer();
            finish();
        }
        // If pause was pressed
        if (i1 == R.id.btn_pause) {
            if (playerControl.isPlaying()) {
                playerControl.pause();
                btn_pause.setVisibility(View.GONE);
                btn_play.setVisibility(View.VISIBLE);
            }
        }
        // If play was pressed
        if (i1 == R.id.btn_play) {
            if (!playerControl.isPlaying()) {
                playerControl.start();
                btn_pause.setVisibility(View.VISIBLE);
                btn_play.setVisibility(View.GONE);
            }
        }
        // If forward was pressed
        if (i1 == R.id.btn_fwd) {
            player.seekTo(player.getCurrentPosition() + 30000);
        }
        // If rewind was pressed
        if (i1 == R.id.btn_rev) {
            player.seekTo(player.getCurrentPosition() - 30000);
        }
        // If next was pressed
        if (i1 == R.id.btn_next) {
            player.release();
            currentTrackIndex++;
            // Create new instance of the player, download and play
            execute();
        }
        // If previous was pressed
        if (i1 == R.id.btn_prev) {
            player.release();
            currentTrackIndex--;
            // Create new instance of the player, download and play
            execute();
        }
    }

    private HpLib_RendererBuilder getHpLibRendererBuilder() {
        // Fill user agent
        String userAgent = Util.getUserAgent(this, "HpLib");
        // So we can support various types of video
        // Search type according to the current track
        switch (video_type[currentTrackIndex]){
            case "hls":
                // Context, user_agent (required for hls) and video_url
                return new HpLib_HlsHpLibRendererBuilder(this, userAgent, video_url[currentTrackIndex]);
            case "others":
                // Context, user_agent (required for hls) and video_url
                return new HpLib_ExtractorHpLibRendererBuilder(this, userAgent, Uri.parse(video_url[currentTrackIndex]));
            default:
                throw new IllegalStateException("Unsupported type: " + video_url[currentTrackIndex]);
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