package com.grok.androidicb;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.grok.androidicb.protocol.ErrorPacket;
import com.grok.androidicb.protocol.Packet;
import com.grok.androidicb.protocol.OpenPacket;
import com.grok.androidicb.protocol.PersonalPacket;
import com.grok.androidicb.protocol.StatusPacket;

import java.io.IOException;
import java.util.ArrayList;

import static android.text.Html.FROM_HTML_MODE_COMPACT;


public class MainActivity extends AppCompatActivity implements Callback {

    private static final String LOGTAG = "MainActivity";

    // Custom adapter to display Spanned (HTML) data in ListView.
    private static class SpannedAdapter extends ArrayAdapter<String>  {
        private Context mContext;
        private ArrayList<String> mMessageList;

        public SpannedAdapter(Context context, ArrayList<String> messageList) {
            super(context, R.layout.message, messageList);
            mContext = context;
            mMessageList = messageList;
        }

        // returns the actual view used as a row within the ListView at a
        // particular position
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            String message = getItem(position);

            // Check if an existing view is being reused, otherwise inflate the view
            ViewHolder viewHolder; // view lookup cache stored in tag

            final View result;

            if (convertView == null) {

                viewHolder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.message, parent, false);
                viewHolder.text = (TextView) convertView.findViewById(R.id.output_message);

                result=convertView;

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
                result=convertView;
            }

            viewHolder.text.setText(Html.fromHtml(message));

            return convertView;
        }

        static class ViewHolder {
            TextView text;
        }
    }


    private Context mContext = null;
    private Handler mHandler = null;

    private ListView mOutputListView = null;
    private EditText mInputEditText = null;

    private ArrayList<String> mOutputArrayList;
    private SpannedAdapter mOutputArrayListAdapter;

    SocketConnection mConnection = null;
    IcbClient mClient = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        LogUtil.INSTANCE.SetLogFile("log.txt", mContext);

        mHandler = new Handler(this);

        setContentView(R.layout.activity_main);

        mOutputArrayList = new ArrayList<String>();
        mOutputListView = (ListView) findViewById(R.id.output);
        mOutputArrayListAdapter = new SpannedAdapter(this, mOutputArrayList);
        mOutputListView.setAdapter(mOutputArrayListAdapter);

        mInputEditText = (EditText) findViewById(R.id.input);
        mInputEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
             public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                 // If the action is a key-up event on the return key, send the message
                 if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                     String message = view.getText().toString();
                     addMessageToOutput(message);
                     if (mClient != null) {
                         mClient.sendCommand(message);
                         mInputEditText.getText().clear();
                     }
                 }
                 return true;
             }
        });

        doConnect();
    }

    @Override
    public void onStop() {
        if (mClient != null) {
            mClient.stop();
            try {
                if (mConnection != null) {
                    mConnection.close();
                }
            } catch (IOException e) {
                LogUtil.INSTANCE.e(LOGTAG, "Exception closing mConnection", e);
            }
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        try {
            if (mConnection != null) {
                mConnection.close();
            }
        } catch (IOException e) {
            LogUtil.INSTANCE.e(LOGTAG, "Exception closing mConnection", e);
        }
        super.onDestroy();
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
        String message = mInputEditText.getText().toString();
        mOutputArrayList.add(message);
        mClient.sendCommand(message);
    }

    protected void addMessageToOutput(String message) {
        mOutputArrayList.add(message);
        mOutputArrayListAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean handleMessage(Message msg) {
        //LogUtil.INSTANCE.d(LOGTAG, "handleMessage()");
        switch (msg.what) {
            case AppMessages.EVT_SOCKET_CONNECTED:
                doPostConnect();
                break;
            case AppMessages.EVT_PROTOCOL:
                doLogin();
                break;
            case AppMessages.EVT_LOGIN_OK:
                LogUtil.INSTANCE.d(LOGTAG, "Login OK");
                break;
            case AppMessages.EVT_ERROR_MSG: {
                ErrorPacket pkt = (ErrorPacket) msg.obj;
                String message = "[*Error*] " + pkt.getErrorText();
                addMessageToOutput(message);
                break;
            }
            case AppMessages.EVT_STATUS_MSG: {
                StatusPacket pkt = (StatusPacket) msg.obj;
                String message = "[=" + pkt.getStatusHeader() + "=] " + pkt.getStatusText();
                addMessageToOutput(message);
                break;
            }
            case AppMessages.EVT_OPEN_MSG: {
                OpenPacket pkt = (OpenPacket) msg.obj;
                String message = "&lt;" + pkt.getNick() + "&gt; " + pkt.getText();
                addMessageToOutput(message);
                break;
            }
            case AppMessages.EVT_PERSONAL_MSG: {
                PersonalPacket pkt = (PersonalPacket) msg.obj;
                String message = "&lt;*" + pkt.getNick() + "*&gt; " + pkt.getText();
                addMessageToOutput(message);
                break;
            }

        }
        return true;
    }
}
