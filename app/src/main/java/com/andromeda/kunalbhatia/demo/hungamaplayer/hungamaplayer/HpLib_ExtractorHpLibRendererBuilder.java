package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;

import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;

/**
 * Created by raul on 22/12/2016.
 */
public class HpLib_ExtractorHpLibRendererBuilder implements HpLib_RendererBuilder {

    private static final int BUFFER_SEGMENT_SIZE  = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private final Context context;
    private final String userAgent;
    private final Uri uri;

    // Context, UserAgent, Uri
    public HpLib_ExtractorHpLibRendererBuilder(Context context, String userAgent, Uri uri) {
        this.context = context;
        this.userAgent = userAgent;
        this.uri = uri;
    }

    @Override
    public void buildRenderers(VideoPlayer player) {
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
        Handler mainHandler = player.getMainHandler();

        // Build the video and audio renderers.
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler, null);
        DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
        /**
         * A {@link SampleSource} that extracts sample data using an {@link Extractor}.
         *
         * <p>If no {@link Extractor} instances are passed to the constructor, the input stream container
         * format will be detected automatically from the following supported formats:
         *
         * <ul>
         * <li>MP4, including M4A ({@link com.google.android.exoplayer.extractor.mp4.Mp4Extractor})</li>
         * <li>fMP4 ({@link com.google.android.exoplayer.extractor.mp4.FragmentedMp4Extractor})</li>
         * <li>Matroska and WebM ({@link com.google.android.exoplayer.extractor.webm.WebmExtractor})</li>
         * <li>Ogg Vorbis/FLAC ({@link com.google.android.exoplayer.extractor.ogg.OggExtractor}</li>
         * <li>MP3 ({@link com.google.android.exoplayer.extractor.mp3.Mp3Extractor})</li>
         * <li>AAC ({@link com.google.android.exoplayer.extractor.ts.AdtsExtractor})</li>
         * <li>MPEG TS ({@link com.google.android.exoplayer.extractor.ts.TsExtractor})</li>
         * <li>MPEG PS ({@link com.google.android.exoplayer.extractor.ts.PsExtractor})</li>
         * <li>FLV ({@link com.google.android.exoplayer.extractor.flv.FlvExtractor})</li>
         * <li>WAV ({@link com.google.android.exoplayer.extractor.wav.WavExtractor})</li>
         * <li>FLAC (only available if the FLAC extension is built and included)</li>
         * </ul>
         *
         * <p>Seeking in AAC, MPEG TS and FLV streams is not supported.
         *
         * <p>To override the default extractors, pass one or more {@link Extractor} instances to the
         * constructor. When reading a new stream, the first {@link Extractor} that returns {@code true}
         * from {@link Extractor#sniff(ExtractorInput)} will be used.
         */
        ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context,
                sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000);
        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,MediaCodecSelector.DEFAULT,null,true);
        // Invoke the callback.
        TrackRenderer[] renderers = new TrackRenderer[VideoPlayer.RENDERER_COUNT];
        renderers[VideoPlayer.TYPE_VIDEO] = videoRenderer;
        renderers[VideoPlayer.TYPE_AUDIO] = audioRenderer;
        player.onRenderers(renderers, bandwidthMeter);
    }

    @Override
    public void cancel() {
        // Do nothing.
    }

}