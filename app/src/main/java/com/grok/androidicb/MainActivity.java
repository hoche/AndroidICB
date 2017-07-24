package com.grok.androidicb;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.grok.androidicb.protocol.ErrorPacket;
import com.grok.androidicb.protocol.OpenPacket;
import com.grok.androidicb.protocol.PersonalPacket;
import com.grok.androidicb.protocol.StatusPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.regex.Pattern;


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
    private AlertDialog mDisconnectAlert = null;

    private ListView mOutputListView = null;
    private EditText mInputEditText = null;

    private ArrayList<String> mOutputArrayList;
    private SpannedAdapter mOutputArrayListAdapter;

    private MenuItem mConnectMenuItem;

    IcbClient mClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        LogUtil.INSTANCE.SetLogFile("log.txt", mContext);

        mHandler = new Handler(this);


        mOutputArrayList = new ArrayList<String>();
        mOutputArrayListAdapter = new SpannedAdapter(this, mOutputArrayList);

        setupLayout();

        /*
        new PatternEditableBuilder().
            addPattern(Pattern.compile("&lt;(\\w+)&gt;"), Color.BLUE,
                new PatternEditableBuilder.SpannableClickedListener() {

                    @Override
                    public void onSpanClicked(String text) {
                        Toast.makeText(MainActivity.this, "Clicked nickname: " + text,
                                Toast.LENGTH_SHORT).show();
                    }

                }).into(mInputEditText);
        */

        buildDisconnectAlert();

        // Ok, now we need to check the preferences. If "autoconnect on launch" is created,
        // AND the host, port, username, password, and group are set, just quietly connect.
        // If the username, password, or group aren't set, pop up a "set me" dialog.
        doConnect();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        mConnectMenuItem = menu.findItem(R.id.connect);
        updateConnectionMenuItemStatus();
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.connect:
                if (mClient != null) {
                    mDisconnectAlert.show();
                } else {
                    doConnect();
                }
                return true;
            case R.id.preferences:
                startActivity(new Intent(this, PrefActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onStart() {
        LogUtil.INSTANCE.d(LOGTAG, "onStart()");
        super.onStart();
    }

    @Override
    public void onResume() {
        LogUtil.INSTANCE.d(LOGTAG, "onResume()");
        super.onResume();
    }

    @Override
    public void onPause() {
        LogUtil.INSTANCE.d(LOGTAG, "onPause()");
        super.onPause();
    }

    @Override
    public void onStop() {
        LogUtil.INSTANCE.d(LOGTAG, "onStop()");
        super.onStop();
    }

    // Ordinarily I wouldn't use this but sometimes when you're running in the debugger and you
    // replace the package, the normal onStop doesn't get called.
    @Override
    public void onDestroy() {
        LogUtil.INSTANCE.d(LOGTAG, "onDestroy()");
        doDisconnect();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        LogUtil.INSTANCE.d(LOGTAG, "onBackPressed()");
        // If we're disconnected, allow the back button to kill us.
        if (mClient == null) {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        setupLayout();
    }

    protected void setupLayout()
    {
        LogUtil.INSTANCE.d(LOGTAG, "setUpScreen()");
        setContentView(R.layout.mainactivity);
        mOutputListView = (ListView) findViewById(R.id.output);
        mOutputListView.setAdapter(mOutputArrayListAdapter);

        String savedText = null;
        if (mInputEditText != null) {
            savedText = mInputEditText.getText().toString();
        }
        mInputEditText = (EditText) findViewById(R.id.input);
        if (savedText != null && savedText.length() > 0) {
            mInputEditText.setText(savedText);
            mInputEditText.setSelection(savedText.length());
        }
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
    }

    private class ConnectTask extends AsyncTask<Void, Void, Integer> {
        private Socket mSocket = null;
        private String mServer = null;
        private int mPort;

        @Override
        protected void onPreExecute() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            mServer = prefs.getString("server_preference", "default.icb.net");
            mPort = Integer.parseInt(prefs.getString("port_preference", "7326"));
            Toast toast =  Toast.makeText(getApplicationContext(), "Connecting to " + mServer + ":" + mPort, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
            toast.show();
        }

        @Override
        protected Integer doInBackground(Void...params) {
            InetAddress serverAddr = null;
            try {
                // The host name can either be a machine name, such as "java.sun.com", or a textual representation of its IP address
                serverAddr = InetAddress.getByName(mServer);
            } catch (Exception e) {
                LogUtil.INSTANCE.e(LOGTAG, "Couldn't get IP address for " + mServer, e);
                return -1;
            }

            LogUtil.INSTANCE.d(LOGTAG, "run() Connecting to " + serverAddr.toString() + "(" + mServer + ") port " + mPort);

            try {
                InetSocketAddress sockaddr = new InetSocketAddress(mServer, mPort);
                mSocket = new Socket();
                mSocket.connect(sockaddr, 5000);
            } catch (java.net.UnknownHostException e) {
                LogUtil.INSTANCE.d(LOGTAG, "Socket() UnknownHostException: " + e.getMessage());
                return -1;
            } catch (IOException e) {
                LogUtil.INSTANCE.d(LOGTAG, "Socket() IOException: " + e.getMessage());
                return -1;
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 0) {
                updateConnectionMenuItemStatus();
                mClient = new IcbClient(mSocket, mHandler);
            } else {
                Toast toast =  Toast.makeText(getApplicationContext(), "Couldn't connect to " + mServer + ":" + mPort, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
                toast.show();
            }
        }
    }


    protected void doConnect() {
        new ConnectTask().execute();
    }


    // Check mClient, and call its stop. It'll send a message to the Read/Write threads to die.
    // We release our reference to it here so when that's all done the gc should destroy it.
    // The Read/Write threads will maintain a reference to the mConnection while they go through
    // their stopping routines. Again, we release our reference here and when that's done the
    // gc should just take care of everything. I hope.
    protected synchronized void doDisconnect()
    {
        LogUtil.INSTANCE.d(LOGTAG, "doDisconnect()");
        if (mClient == null) {
            return;
        }

        mClient.stop();
        updateConnectionMenuItemStatus();
        addMessageToOutput("[=Disconnected=]");
    }

    protected void buildDisconnectAlert() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setMessage("Really disconnect?");
        alertBuilder.setCancelable(true);

        alertBuilder.setPositiveButton(
                "Yes",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        doDisconnect();
                        dialog.cancel();
                    }
                });

        alertBuilder.setNegativeButton(
                "No",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

        mDisconnectAlert = alertBuilder.create();
    }

    protected synchronized void updateConnectionMenuItemStatus()
    {
        if (mConnectMenuItem == null) {
            // menu hasn't been created yet
            return;
        }
        if (mClient != null) {
            mConnectMenuItem.setTitle("Disconnect");
        } else {
            mConnectMenuItem.setTitle("Connect");
        }
    }

    protected void doLogin() {
        if (mClient == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String nick = prefs.getString("nick_preference", "andybee");
        String password = prefs.getString("password_preference", "");
        String group = prefs.getString("group_preference","testing");
        mClient.sendLogin("AndroidIcb", nick, group, "login", password);
    }

    protected void addMessageToOutput(String message) {
        mOutputArrayList.add(message);
        mOutputArrayListAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case AppMessages.EVT_SOCKET_STOPPED:
                LogUtil.INSTANCE.d(LOGTAG, "Got EVT_SOCKET_STOPPED");
                mClient = null;
                updateConnectionMenuItemStatus();
                addMessageToOutput("[=Disconnected=]");
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
