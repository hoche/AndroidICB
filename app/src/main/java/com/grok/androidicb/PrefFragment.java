package com.grok.androidicb;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by hoche on 2/28/2016.
 */

public class PrefFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
