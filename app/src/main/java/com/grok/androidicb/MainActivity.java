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
import com.grok.androidicb.protocol.OpenPacket;
import com.grok.androidicb.protocol.PersonalPacket;
import com.grok.androidicb.protocol.StatusPacket;


public class MainActivity extends AppCompatActivity implements Callback {

    Context mContext = null;
    Handler mHandler = null;

    WebView mWebView = null;
    EditText mInput = null;
    Button mSubmit = null;
    StringBuffer mHtmlData;

    SocketConnection mConnection = null;
    IcbClient mClient = null;


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
        mHtmlData = new StringBuffer();

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

    protected void doConnect() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String server = prefs.getString("server_preference", "default.icb.net");
        int port = Integer.parseInt(prefs.getString("port_preference", "7326"));
        mConnection = new SocketConnection(server, port, mHandler);
    }

    protected void doPostConnect() {
        mClient = new IcbClient(mConnection, mHandler);
    }

    protected void doLogin() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String nick = prefs.getString("nick_preference", "andybee");
        String password = prefs.getString("password_preference", "");
        String group = prefs.getString("group_preference","testing");

        mClient.sendLogin("AndroidIcb", nick, group, "login", password);
    }

    protected void doSubmit() {

    }

    @Override
    public boolean handleMessage(Message msg) {
        //LogUtil.INSTANCE.d(LOGTAG, "handleMessage()");
        switch (msg.what) {
            case AppMessages.EVT_SOCKET_CONNECTED:
                doPostConnect();
                break;
            case AppMessages.EVT_LOGIN_OK:
                mSubmit.setText("Submit");
                break;
            case AppMessages.EVT_PROTOCOL:
                doLogin();
                break;
            case AppMessages.EVT_STATUS_MSG: {
                StatusPacket pkt = (StatusPacket) msg.obj;
                mHtmlData.append("[=" + pkt.getStatusHeader() + "=] " + pkt.getStatusText() + "<br>");
                mWebView.loadData(mHtmlData.toString(), "text/html", null);
                break;
            }
            case AppMessages.EVT_OPEN_MSG: {
                OpenPacket pkt = (OpenPacket) msg.obj;
                mHtmlData.append("<" + pkt.getNick() + "> " + pkt.getText() + "<br>");
                mWebView.loadData(mHtmlData.toString(), "text/html", null);
                break;
            }
            case AppMessages.EVT_PERSONAL_MSG: {
                PersonalPacket pkt = (PersonalPacket) msg.obj;
                mHtmlData.append("<*" + pkt.getNick() + "*> " + pkt.getText() + "<br>");
                mWebView.loadData(mHtmlData.toString(), "text/html", null);
                break;
            }

        }
        return true;
    }
}
