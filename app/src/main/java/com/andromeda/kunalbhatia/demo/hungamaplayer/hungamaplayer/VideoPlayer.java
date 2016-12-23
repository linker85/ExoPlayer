package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteButton;
import android.support.v7.widget.PopupMenu;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.util.MimeTypes;
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
* Screen touch swipes, swipes will control brightness and volume
* */
public class VideoPlayer extends AppCompatActivity implements HlsSampleSource.EventListener, View.OnClickListener {

    private static final String TAG = "VideoPlayer";

    public static final int RENDERER_COUNT = 2;
    public static final int TYPE_AUDIO     = 1;
    private ExoPlayer player;
    private SurfaceView surface;
    private String[] video_url, video_type, video_title;
    private int currentTrackIndex;
    private Handler mainHandler;
    private HpLib_RendererBuilder hpLibRendererBuilder;
    private TrackRenderer videoRenderer;
    private LinearLayout root, unlock_panel;
    public static final int TYPE_VIDEO = 0;

    private View decorView;
    private int uiImmersiveOptions;
    private RelativeLayout loadingPanel;
    private Runnable updatePlayer, hideControls;

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
    // For the player controls feature
    private PlayerControl playerControl;
    public enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    // For the lock and unlock feature
    public enum ControlsMode {
        LOCK, FULLCONTORLS
    }
    private ControlsMode controlsState;

    // Player buttons
    private ImageButton btn_play;
    private ImageButton btn_pause;
    private ImageButton btn_fwd;
    private ImageButton btn_rev;
    private ImageButton btn_next;
    private ImageButton btn_prev;

    // Lock/Unlock buttons
    private ImageButton btn_lock;
    private ImageButton btn_unlock;

    // Settings button
    private ImageButton btn_settings;

    // Volume and brigthness controls
    private Boolean tested_ok = false;
    private int sWidth, sHeight;
    private boolean immersiveMode, intLeft, intRight, intTop, intBottom, finLeft, finRight, finTop, finBottom;
    private long diffX, diffY;
    private int calculatedTime;
    private String seekDur;
    private float baseX, baseY;
    private Boolean screen_swipe_move = false;
    private static final int MIN_DISTANCE = 150;
    private ContentResolver cResolver;
    private Window window;
    private int brightness, mediavolume, device_height, device_width;
    private LinearLayout volumeBarContainer, brightnessBarContainer,brightness_center_text, vol_center_text;
    private ProgressBar volumeBar, brightnessBar;
    private ImageView volIcon, brightnessIcon, vol_image, brightness_image;
    private TextView vol_perc_center_text, brigtness_perc_center_text,txt_seek_secs,txt_seek_currTime;
    private AudioManager audioManager;
    private Display display;
    private Point size;

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
    {
        // We need a thread to be able to hide the control
        hideControls = new Runnable() {
            @Override
            public void run() {
                hideAllControls();
            }
        };
    }

    private void hideAllControls() {
        // Depending on the controlState I lock or unlock
        if (controlsState == ControlsMode.FULLCONTORLS) {
            // Hide controls
            if(root.getVisibility()==View.VISIBLE) {
                root.setVisibility(View.GONE);
            }
        } else if (controlsState == ControlsMode.LOCK) {
            // Hide unlock panel
            if (unlock_panel.getVisibility() == View.VISIBLE) {
                unlock_panel.setVisibility(View.GONE);
            }
        }
        // Hide Android bars
        decorView.setSystemUiVisibility(uiImmersiveOptions);
    }
    private void showControls() {
        // Depending on the controlState
        if (controlsState == ControlsMode.FULLCONTORLS) {
            // Show controls
            if(root.getVisibility() == View.GONE){
                root.setVisibility(View.VISIBLE);
            }
        } else if (controlsState == ControlsMode.LOCK) {
            // Show unlock panel
            if(unlock_panel.getVisibility() == View.GONE) {
                unlock_panel.setVisibility(View.VISIBLE);
            }
        }
        // Remove previous callbacks to the hideControls
        mainHandler.removeCallbacks(hideControls);
        // Re-init new callback to hide controls after 3 seconds of no activity
        mainHandler.postDelayed(hideControls, 3000);
    }

    /*
    * User is watching the video, after sometime the controls get hidden.
    * If controls are hidden and user wants to pause the video, user touches screen after releasing the touch the controls reappear
    * */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()){
            // User touches the screen
            case MotionEvent.ACTION_DOWN:
                tested_ok = false;
                // We pressed on the left
                if (event.getX() < (sWidth / 2)) {
                    intLeft = true;
                    intRight = false;
                    // We pressed on the right
                } else if (event.getX() > (sWidth / 2)) {
                    intLeft = false;
                    intRight = true;
                }
                int upperLimit = (sHeight / 4) + 100;
                int lowerLimit = ((sHeight / 4) * 3) - 150;
                // We pressed on the top
                if (event.getY() < upperLimit) {
                    intBottom = false;
                    intTop = true;
                // We pressed in the bottom
                } else if (event.getY() > lowerLimit) {
                    intBottom = true;
                    intTop = false;
                } else {
                    intBottom = false;
                    intTop = false;
                }
                diffX = 0;
                calculatedTime = 0;
                seekDur = String.format("%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(diffX) -
                                TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diffX)),
                        TimeUnit.MILLISECONDS.toSeconds(diffX) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diffX)));

                //TOUCH STARTED
                baseX = event.getX();
                baseY = event.getY();
                break;
            // User moves the finger
            case MotionEvent.ACTION_MOVE:
                // The finge is now moving
                screen_swipe_move = true;
                // Controls are currently visible
                if (controlsState == ControlsMode.FULLCONTORLS) {
                    // Dissapear controls while finger is moving
                    root.setVisibility(View.GONE);
                    diffX = (long) (Math.ceil(event.getX() - baseX));
                    diffY = (long) Math.ceil(event.getY() - baseY);
                    // Speed in which brightness can increase or deacrease
                    double brightnessSpeed = 0.05;
                    if (Math.abs(diffY) > MIN_DISTANCE) {
                        tested_ok = true;
                    }
                    // Its vertical movement
                    if (Math.abs(diffY) > Math.abs(diffX)) {
                        // Tap in left increases/decreases brightness
                        if (intLeft) {
                            cResolver = getContentResolver();
                            window    = getWindow();
                            // Get the current brightness
                            try {
                                Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                                brightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
                            } catch (Settings.SettingNotFoundException e) {
                                e.printStackTrace();
                            }
                            // Define new brightness
                            int new_brightness = (int) (brightness - (diffY * brightnessSpeed));
                            // Keep brightness in bounds
                            if (new_brightness > 250) {
                                new_brightness = 250;
                            } else if (new_brightness < 1) {
                                new_brightness = 1;
                            }
                            // Calculate brightness inside the bar container
                            double brightPerc = Math.ceil((((double) new_brightness / (double) 250) * (double) 100));
                            brightnessBarContainer.setVisibility(View.VISIBLE);
                            brightness_center_text.setVisibility(View.VISIBLE);
                            brightnessBar.setProgress((int) brightPerc);
                            // Change brightness icons
                            if (brightPerc < 30) {
                                brightnessIcon.setImageResource(R.drawable.hplib_brightness_minimum);
                                brightness_image.setImageResource(R.drawable.hplib_brightness_minimum);
                            } else if (brightPerc > 30 && brightPerc < 80) {
                                brightnessIcon.setImageResource(R.drawable.hplib_brightness_medium);
                                brightness_image.setImageResource(R.drawable.hplib_brightness_medium);
                            } else if (brightPerc > 80) {
                                brightnessIcon.setImageResource(R.drawable.hplib_brightness_maximum);
                                brightness_image.setImageResource(R.drawable.hplib_brightness_maximum);
                            }
                            // Set brightness text
                            brigtness_perc_center_text.setText(" " + (int) brightPerc);

                            // Set new brightness in the device
                            Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, (new_brightness));
                            WindowManager.LayoutParams layoutpars = window.getAttributes();
                            layoutpars.screenBrightness = brightness / (float) 255;
                            window.setAttributes(layoutpars);
                        } else if (intRight) {
                            // Tap in left increases/decreases volume
                            // Show volume text
                            vol_center_text.setVisibility(View.VISIBLE);
                            // Get current volume
                            mediavolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                            // Get max volume
                            int maxVol  = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            double cal  = (double) diffY * ((double)maxVol/(double)(device_height * 4));
                            // Get new volume
                            int newMediaVolume = mediavolume - (int) cal;
                            // Set volume in bounds
                            if (newMediaVolume > maxVol) {
                                newMediaVolume = maxVol;
                            } else if (newMediaVolume < 1) {
                                newMediaVolume = 0;
                            }
                            // Set new volume
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newMediaVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                            // Calculate volume to show to the screen
                            double volPerc = Math.ceil((((double) newMediaVolume / (double) maxVol) * (double) 100));
                            // Set volume on the screen
                            vol_perc_center_text.setText(" " + (int) volPerc);
                            // Volume was muted, show appropiate icons
                            if (volPerc < 1) {
                                volIcon.setImageResource(R.drawable.hplib_volume_mute);
                                vol_image.setImageResource(R.drawable.hplib_volume_mute);
                                vol_perc_center_text.setVisibility(View.GONE);
                            } else if (volPerc >= 1) {
                                // There is volume, show appropiate icons
                                volIcon.setImageResource(R.drawable.hplib_volume);
                                vol_image.setImageResource(R.drawable.hplib_volume);
                                vol_perc_center_text.setVisibility(View.VISIBLE);
                            }
                            // Show volume bar container
                            volumeBarContainer.setVisibility(View.VISIBLE);
                            // Set new progress for the volume container
                            volumeBar.setProgress((int) volPerc);
                        }
                    } else {
                        // Its horizontal movement
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                // Releases the finger
            case MotionEvent.ACTION_UP:
                // User removed finger from phone
                screen_swipe_move = false;
                tested_ok         = false;

                // Hide volume and brigthness controls
                brightness_center_text.setVisibility(View.GONE);
                vol_center_text.setVisibility(View.GONE);
                brightnessBarContainer.setVisibility(View.GONE);
                volumeBarContainer.setVisibility(View.GONE);

                showControls();
                break;
        }
        return super.onTouchEvent(event);
    }

    // Top bar (Back button, video title, chrome cast) and Seekbar (Current duration, total duration)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        // Get the screen size
        display = getWindowManager().getDefaultDisplay();
        size    = new Point();
        display.getSize(size);
        sWidth  = size.x;
        sHeight = size.y;

        // Get device size: Gets display metrics that describe the size and density of this display.
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        device_height = displaymetrics.heightPixels;
        device_width  = displaymetrics.widthPixels;

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
        txt_ct  = (TextView) findViewById(R.id.txt_currentTime);
        txt_td  = (TextView) findViewById(R.id.txt_totalDuration);
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

        // Control buttons
        btn_back  = (ImageButton) findViewById(R.id.btn_back);
        btn_play  = (ImageButton) findViewById(R.id.btn_play);
        btn_pause = (ImageButton) findViewById(R.id.btn_pause);
        btn_fwd   = (ImageButton) findViewById(R.id.btn_fwd);
        btn_rev   = (ImageButton) findViewById(R.id.btn_rev);
        btn_prev  = (ImageButton) findViewById(R.id.btn_prev);
        btn_next  = (ImageButton) findViewById(R.id.btn_next);

        // Lock/Unlock button
        btn_lock = (ImageButton) findViewById(R.id.btn_lock);
        btn_unlock = (ImageButton) findViewById(R.id.btn_unlock);

        // Settings button
        btn_settings = (ImageButton) findViewById(R.id.btn_settings);

        // Volume and brigthness button
        vol_perc_center_text       = (TextView) findViewById(R.id.vol_perc_center_text);
        brigtness_perc_center_text = (TextView) findViewById(R.id.brigtness_perc_center_text);
        volumeBar                  = (ProgressBar) findViewById(R.id.volume_slider);
        brightnessBar              = (ProgressBar) findViewById(R.id.brightness_slider);
        volumeBarContainer         = (LinearLayout) findViewById(R.id.volume_slider_container);
        brightnessBarContainer     = (LinearLayout) findViewById(R.id.brightness_slider_container);
        brightness_center_text     = (LinearLayout) findViewById(R.id.brightness_center_text);
        vol_center_text            = (LinearLayout) findViewById(R.id.vol_center_text);
        volIcon                    = (ImageView) findViewById(R.id.volIcon);
        brightnessIcon             = (ImageView) findViewById(R.id.brightnessIcon);
        vol_image                  = (ImageView) findViewById(R.id.vol_image);
        brightness_image           = (ImageView) findViewById(R.id.brightness_image);
        audioManager               = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Listeners
        btn_back.setOnClickListener(this);
        btn_play.setOnClickListener(this);
        btn_pause.setOnClickListener(this);
        btn_fwd.setOnClickListener(this);
        btn_rev.setOnClickListener(this);
        btn_prev.setOnClickListener(this);
        btn_next.setOnClickListener(this);

        btn_lock.setOnClickListener(this);
        btn_unlock.setOnClickListener(this);

        btn_settings.setOnClickListener(this);

        // Unlock panel
        unlock_panel = (LinearLayout) findViewById(R.id.unlock_panel);

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
            mainHandler.postDelayed(hideControls, 3000);
            controlsState = ControlsMode.FULLCONTORLS;
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
        // If lock was pressed -> hide controls, allow to unlock
        if (i1 == R.id.btn_lock) {
            controlsState = ControlsMode.LOCK;
            root.setVisibility(View.GONE);
            unlock_panel.setVisibility(View.VISIBLE);
        }
        // If unlock was pressed -> show controls, allow to lock
        if (i1 == R.id.btn_unlock) {
            controlsState = ControlsMode.FULLCONTORLS;
            root.setVisibility(View.VISIBLE);
            unlock_panel.setVisibility(View.GONE);
        }
        // Click on settings: Select quality of HLS
        if (i1 == R.id.btn_settings) {
            // Show popup
            PopupMenu popup = new PopupMenu(VideoPlayer.this, v);
            // If user clicks on a menu item
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    /**
                     * Selects a track for the specified renderer.
                     *
                     * @param rendererIndex The index of the renderer.
                     * @param trackIndex The index of the track. A negative value or a value greater than or equal to
                     *     the renderer's track count will disable the renderer.
                     */
                    // Changes the quality
                    player.setSelectedTrack(0, (item.getItemId() - 1));
                    return false;
                }
            });
            // Menu inside the popup
            Menu menu = popup.getMenu();
            // Add items inside the menu
            menu.add(Menu.NONE, 0, 0, "Video Quality");
            for (int i = 0; i < player.getTrackCount(0); i++) {
                // Get the qualities from the video
                /**
                 * Returns the format of a track.
                 *
                 * @param rendererIndex The index of the renderer.
                 * @param trackIndex The index of the track.
                 * @return The format of the track.
                 */
                MediaFormat format = player.getTrackFormat(0, i);
                if (MimeTypes.isVideo(format.mimeType)) {
                    /**
                     * Whether the format represents an adaptive track, meaning that the format of the actual media
                     * data may change (e.g. to adapt to network conditions).
                     */
                    if (format.adaptive) {
                        menu.add(1, (i + 1), (i + 1), "Auto");
                    } else {
                        /**
                         * The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
                         */
                        menu.add(1, (i + 1), (i + 1), format.width + "p");
                    }
                }
            }
            menu.setGroupCheckable(1, true, true);
            // Select the first item
            menu.findItem((player.getSelectedTrack(0) + 1)).setChecked(true);
            // Show popup
            popup.show();
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