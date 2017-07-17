package com.grok.androidicb;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    Context mContext = null;

    WebView mOutput = null;
    EditText mInput = null;
    Button mSubmit = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        LogUtil.INSTANCE.SetLogFile("log.txt", mContext);

        setContentView(R.layout.activity_main);

        mOutput = (WebView) findViewById(R.id.output);
        mInput = (EditText) findViewById(R.id.input);
        mSubmit = (Button) findViewById(R.id.submit);
    }
}
