package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

/**
 * Created by raul on 21/12/2016.
 * The interface is so we can support in the future dash video and mp4
 */
public interface HpLib_RendererBuilder {
    void buildRenderers(VideoPlayer player);
    void cancel();
}