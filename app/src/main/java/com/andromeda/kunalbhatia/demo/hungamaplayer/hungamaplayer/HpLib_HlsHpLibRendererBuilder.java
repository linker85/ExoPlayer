package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.extractor.ts.PtsTimestampAdjuster;
import com.google.android.exoplayer.hls.DefaultHlsTrackSelector;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.PtsTimestampAdjusterProvider;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;

import java.io.IOException;

/**
 * Created by linke_000 on 21/12/2016.
 */
public class HpLib_HlsHpLibRendererBuilder implements HpLib_RendererBuilder {
    private static final int BUFFER_SEGMENT_SIZE   = 64 * 1024;
    private static final int MAIN_BUFFER_SEGMENTS  = 254;
    private static final int AUDIO_BUFFER_SEGMENTS = 54;
    private static final int TEXT_BUFFER_SEGMENTS  = 2;

    private final Context context;
    private final String userAgent;
    private final String url;
    private AsyncRendererBuilder currentAsyncBuilder;


    public HpLib_HlsHpLibRendererBuilder(VideoPlayer player, String userAgent, String video_url) {
        this.context   = player;
        this.userAgent = userAgent;
        this.url       = video_url;
    }

    @Override
    public void buildRenderers(VideoPlayer player) {
        // context, userAgent, url and activity
        currentAsyncBuilder = new AsyncRendererBuilder(context, userAgent, url, player);
        // Performs a single manifest load.
        currentAsyncBuilder.init();
    }

    @Override
    public void cancel() {
        if (currentAsyncBuilder != null) {
            currentAsyncBuilder.cancel();
            currentAsyncBuilder = null;
        }
    }

    private static final class AsyncRendererBuilder implements ManifestFetcher.ManifestCallback<HlsPlaylist> {

        private final Context context;
        private final String userAgent;
        private final String url;
        private final VideoPlayer player;
        private final ManifestFetcher<HlsPlaylist> playlistFetcher;
        private boolean canceled;

        public AsyncRendererBuilder(Context context, String userAgent, String url, VideoPlayer player) {
            this.context = context;
            this.userAgent = userAgent;
            this.url = url;
            this.player = player;
            // init HlsPlaylistParser
            HlsPlaylistParser parser = new HlsPlaylistParser();
            // Performs both single and repeated loads of media manifests.
            playlistFetcher = new ManifestFetcher<>(url, new DefaultUriDataSource(context, userAgent),
                    parser);
        }

        // Performs a single manifest load, and calls onSingleManifest if there is a manifest
        public void init() {
            playlistFetcher.singleLoad(player.getMainHandler().getLooper(), this);
        }
        public void cancel() {
            canceled = true;
        }

        @Override
        public void onSingleManifest(HlsPlaylist manifest) {
            if (canceled) {
                return;
            }
            // Get handler from the activity
            Handler mainHandler = player.getMainHandler();
            LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
            /**
             * Counts transferred bytes while transfers are open and creates a bandwidth sample and updated
             * bandwidth estimate each time a transfer ends.
             */
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();

            boolean haveSubtitles = false;
            boolean haveAudios    = false;
            if (manifest instanceof HlsMasterPlaylist) {
                HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) manifest;
                haveSubtitles = !masterPlaylist.subtitles.isEmpty();

            }
            // Build the video/id3 renderers.
            /**
             * A component that provides media data.
             *
             * The SampleSource implementations uses DataSource instances for loading media data.
             * The most commonly used implementations are:
             *  DefaultUriDataSource – For playing media that can be either local or loaded over the network.
             *  AssetDataSource – For playing media stored in the assets folder of the application’s apk.
             *
             */
            DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);

            /**
             * @param isMaster True if this is the master source for the playback. False otherwise. Each
             *     playback must have exactly one master source, which should be the source providing video
             *     chunks (or audio chunks for audio only playbacks).
             * @param dataSource A {@link DataSource} suitable for loading the media data.
             * @param playlist The HLS playlist.
             * @param trackSelector Selects tracks to be exposed by this source.
             * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
             * @param timestampAdjusterProvider A provider of {@link PtsTimestampAdjuster} instances. If
             *     multiple {@link HlsChunkSource}s are used for a single playback, they should all share the
             *     same provider.
             * @param adaptiveMode The mode for switching from one variant to another. One of
             *     {@link #ADAPTIVE_MODE_NONE}, {@link #ADAPTIVE_MODE_ABRUPT} and
             *     {@link #ADAPTIVE_MODE_SPLICE}.
             */
            HlsChunkSource chunkSource = new HlsChunkSource(true /* isMaster */, dataSource, manifest,
                    DefaultHlsTrackSelector.newDefaultInstance(context), bandwidthMeter,
                    timestampAdjusterProvider, HlsChunkSource.ADAPTIVE_MODE_SPLICE);

            /*
            * A SampleSource object provides format information and media samples to be rendered.
            * SampleSource object is required to be inserted in the TrackRenderer constructors.
            * There are different types of concrete SampleSource implementations as per the use cases
            *   ExtractorSampleSource – For formats such as MP3, M4A, MP4, WebM, MPEG-TS and AAC.
            *   ChunkSampleSource – For DASH and SmoothStreaming playbacks.
            *   HlsSampleSource – For HLS playbacks.
            * */
            HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, loadControl,
                    MAIN_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, VideoPlayer.TYPE_VIDEO);

            // Construct video renderers.
            MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context, sampleSource,
                    MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            // Construct audio renderers.
            MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                    MediaCodecSelector.DEFAULT);

            TrackRenderer[] renderers = new TrackRenderer[2];
            renderers[VideoPlayer.TYPE_VIDEO] = videoRenderer;
            renderers[VideoPlayer.TYPE_AUDIO] = audioRenderer;
            //renderers[2] = textRenderer;
            // Plays the video
            player.onRenderers(renderers, bandwidthMeter);
        }

        @Override
        public void onSingleManifestError(IOException e) {
            if (canceled) {
                return;
            }

            player.onRenderersError(e);
        }
    }

}