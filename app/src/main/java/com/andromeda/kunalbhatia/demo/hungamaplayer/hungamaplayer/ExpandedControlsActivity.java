package com.andromeda.kunalbhatia.demo.hungamaplayer.hungamaplayer;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.widget.SeekBar;

import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity;

/**
 * Created by raul on 22/12/2016.
 */
public class ExpandedControlsActivity extends ExpandedControllerActivity {

    private SeekBar seekbar;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        seekbar = (SeekBar) this.getSeekBar();
    }

}