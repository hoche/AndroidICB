package com.grok.androidicb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;

import com.grok.androidicb.protocol.Packet;
import com.grok.androidicb.IcbClient;


public class MainActivity extends AppCompatActivity implements Callback {

    Context mContext = null;
    Handler mHandler = null;

    WebView mWebView = null;
    EditText mInput = null;
    Button mSubmit = null;
    StringBuffer mHtmlData;

    SocketConnection mConnection = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        LogUtil.INSTANCE.SetLogFile("log.txt", mContext);

        mHandler = new Handler(this);

        setContentView(R.layout.activity_main);

        mWebView = (WebView) findViewById(R.id.output);
        mInput = (EditText) findViewById(R.id.input);
        mSubmit = (Button) findViewById(R.id.submit);

        mSubmit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mConnection == null) {
                    doConnect();
                } else {
                    doSubmit();
                }
            }
        });

    }

    public void doConnect() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String server = prefs.getString("server_preference", "");
        int port = Integer.parseInt(prefs.getString("port_preference", ""));
        mConnection = new SocketConnection(server, port, mHandler);
    }

    public void doLogin() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String nick = prefs.getString("nick_preference", "");
        String password = prefs.getString("password_preference", "");
        String group = prefs.getString("group_preference","1");
    }

    public void doSubmit() {

    }

    @Override
    public boolean handleMessage(Message msg) {
        //LogUtil.INSTANCE.d(LOGTAG, "handleMessage()");
        Packet pkt;
        switch (msg.what) {
            case AppMessages.EVT_SOCKET_CONNECTED:
                doLogin();
                break;
            case AppMessages.EVT_OPEN_MSG:
                pkt = (Packet)msg.obj;
                break;
            case AppMessages.EVT_PERSONAL_MSG:
                pkt = (Packet)msg.obj;
                mHtmlData.append(pkt.toString());
                mWebView.loadData(mHtmlData.toString(), "text/html", null);
                break;

        }
        return true;
    }
}
