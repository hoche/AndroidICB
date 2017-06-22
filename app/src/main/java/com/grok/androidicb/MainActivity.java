package com.grok.androidicb;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    Context mContext = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        LogUtil.INSTANCE.SetLogFile("log.txt", mContext);

        setContentView(R.layout.activity_main);
    }
}
