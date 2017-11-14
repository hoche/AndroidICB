package com.grok.androidicb;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Created by Hoche on 11/13/2017.
 */

public class EditTextPreference extends android.preference.EditTextPreference {
    public EditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        return this.getText();
    }
}
