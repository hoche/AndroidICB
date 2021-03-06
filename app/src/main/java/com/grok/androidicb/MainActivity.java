/*
 * AndroidICB - https://github.com/hoche/AndroidICB
 * A client for the Internet CB Network - http://www.icb.net/
 *
 * Copyright (C) 2017 Michel Hoche-Mong
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.grok.androidicb;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.grok.androidicb.protocol.CommandOutputPacket;
import com.grok.androidicb.protocol.ErrorPacket;
import com.grok.androidicb.protocol.OpenPacket;
import com.grok.androidicb.protocol.PersonalPacket;
import com.grok.androidicb.protocol.StatusPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity implements Callback {

    private static final String LOGTAG = "MainActivity";

    // Custom adapter to manipulate Spanned text in ListView.
    private class SpannedAdapter extends ArrayAdapter<String>  {

        public SpannedAdapter(Context context, ArrayList<String> messageList) {
            super(context, R.layout.message, messageList);
        }

        // returns the actual view used as a row within the ListView at a
        // particular position
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            // Get the data item for this position
            String message = getItem(position);

            // Check if an existing view is being reused, otherwise inflate the view
            ViewHolder viewHolder; // view lookup cache stored in tag

            final View result;

            if (convertView == null) {

                viewHolder = new ViewHolder();
                LayoutInflater inflater = LayoutInflater.from(getContext());
                convertView = inflater.inflate(R.layout.message, parent, false);
                viewHolder.text = convertView.findViewById(R.id.output_message);

                result = convertView;

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
                result = convertView;
            }

            //viewHolder.text.setText(Html.fromHtml(message));
            viewHolder.text.setText(message);

            // See https://gist.github.com/nesquena/f2504c642c5de47b371278ee61c75124
            // Make anything at the start of a line between < and > a link.
            new PatternEditableBuilder()
                .addPattern(Pattern.compile("^<\\*(\\w+)\\*> "), Color.BLUE,
                    new PatternEditableBuilder.SpannableClickedListener() {

                        @Override
                        public void onSpanClicked(String text) {
                            Pattern p = Pattern.compile("<\\*(\\w+)\\*>");
                            Matcher m = p.matcher(text);
                            m.find();
                            if (m.groupCount() > 0) {
                                String nick = m.group(1);
                                if (nick != null) {
                                    doPersonalMessageDialog(nick);
                                }
                            }
                        }

                    })
                .addPattern(Pattern.compile("^<(\\w+)> "), Color.BLUE,
                    new PatternEditableBuilder.SpannableClickedListener() {

                        @Override
                        public void onSpanClicked(String text) {
                            Pattern p = Pattern.compile("<(\\w+)>");
                            Matcher m = p.matcher(text);
                            m.find();
                            if (m.groupCount() > 0) {
                                String nick = m.group(1);
                                if (nick != null) {
                                    doPersonalMessageDialog(nick);
                                }
                            }
                        }

                    })
                .into(viewHolder.text);

            return result;
        }

        class ViewHolder {
            TextView text;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mScaleFactor *= detector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 10.0f));

            LogUtil.INSTANCE.d(LOGTAG, "scaling to " + mScaleFactor);

            int fontSize = (int)(12.0f * mScaleFactor);

            for (int i = 0; i < mOutputListView.getCount(); i++) {
                View v = mOutputListView.getAdapter().getView(i, null, null);
                SpannedAdapter.ViewHolder vh = (SpannedAdapter.ViewHolder) v.getTag();
                vh.text.setTextSize(fontSize);
            }

            return true;
        }
    }

    private Handler mHandler = null;
    private AlertDialog mDisconnectAlert = null;

    private ArrayList<String> mOutputArrayList;
    private ListView mOutputListView;
    private SpannedAdapter mOutputArrayListAdapter;

    private ScaleGestureDetector mScaleDetector;
    private float mScaleFactor = 1.f;

    private MenuItem mConnectMenuItem;

    IcbClient mClient = null;

    static final int PREFS_SET_BEFORE_CONNECT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String filesDirPath = PathUtils.getStorageDir(getApplicationContext()).getAbsolutePath();

        LogUtil.INSTANCE.SetLogFile(filesDirPath, "log.txt");

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mHandler = new Handler(this);

        mOutputArrayList = new ArrayList<>();
        mOutputArrayListAdapter = new SpannedAdapter(this, mOutputArrayList);

        mScaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        setupLayout();

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            Toast toast = Toast.makeText(MainActivity.this,
                    getString(R.string.app_name) + " " + pInfo.versionName,
                    Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
            toast.show();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        buildDisconnectAlert();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean autologin = prefs.getBoolean("autologin", true);
        String nick = prefs.getString("nick", "");
        if (autologin && nick.length() > 0) {
            new ConnectTask().execute();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        LogUtil.INSTANCE.d(LOGTAG, "onCreateOptionsMenu()");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        mConnectMenuItem = menu.findItem(R.id.connect);
        updateConnectionMenuItemStatus();
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        LogUtil.INSTANCE.d(LOGTAG, "onOptionsItemSelected()");
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        LogUtil.INSTANCE.d(LOGTAG, "onTouchEvent");
        // Let the ScaleGestureDetector inspect all events.s
        mScaleDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    protected void setupLayout()
    {
        LogUtil.INSTANCE.d(LOGTAG, "setUpScreen()");
        setContentView(R.layout.main_activity);

        mOutputListView = findViewById(R.id.output);
        mOutputListView.setAdapter(mOutputArrayListAdapter);

        EditText inputEditText = findViewById(R.id.input);
        // The following two lines, in conjunction with
        //   android:inputType="text"
        //   android:imeOptions="actionSend"
        // are needed to give us multiline input along with a "Send" button
        // in the popup keyboard. Just setting it to inputType="textMultiLine" disables
        // the keyboard's "Send" button and reverts it back to a linefeed. Most irritating.
        inputEditText.setHorizontallyScrolling(false);
        inputEditText.setLines(Integer.MAX_VALUE);

        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);
        }

        inputEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                boolean handled = false;

                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                    // key-up event on the return key (hard keyboard?)
                    handled = true;
                } else if (actionId == EditorInfo.IME_ACTION_SEND) {
                    // The soft keyboard was up and they hit send.
                    InputMethodManager imm = (InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                    handled = true;
                }

                if (handled) {
                    String message = view.getText().toString();
                    addMessageToOutput(message);
                    if (mClient != null) {
                        mClient.sendCommand(message);
                        view.setText(null);
                    }
                }

                return handled;
            }
        });
    }

    // XXX ToDo
    // This AsyncTask class should be static or leaks might occur.
    // Problem is, it need to get preferences from the AppContext and update the outer class's
    // mClient member. Essentially, I'm doing it wrong and need to rewrite this.
    private class ConnectTask extends AsyncTask<Void, Void, Integer> {
        private Socket mSocket = null;
        private String mServer = null;
        private int mPort;

        @Override
        protected void onPreExecute() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            mServer = prefs.getString("server", getString(R.string.default_server));
            mPort = Integer.parseInt(prefs.getString("port", getString(R.string.default_port)));
            String nick = prefs.getString("nick", "UNSET");
            String group = prefs.getString("group", getString(R.string.default_group));
            Toast toast =  Toast.makeText(getApplicationContext(),
                    "Connecting as nick " + nick + " to " + mServer + ":" + mPort + ", group " + group,
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
            toast.show();
        }

        @Override
        protected Integer doInBackground(Void...params) {
            InetAddress serverAddr;
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
                mSocket.setKeepAlive(true);
                mSocket.setSoTimeout(Integer.MAX_VALUE);
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
                mClient = new IcbClient(mSocket, mHandler);
                updateConnectionMenuItemStatus();
            } else {
                Toast toast =  Toast.makeText(getApplicationContext(), "Couldn't connect to " + mServer + ":" + mPort, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL, 0, 0);
                toast.show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogUtil.INSTANCE.d(LOGTAG, "onActivityResult()");
        if (requestCode == PREFS_SET_BEFORE_CONNECT) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            String nick = prefs.getString("nick", "");
            if (nick.length() > 0) {
                new ConnectTask().execute();
            }
        }
    }

    protected void doConnect() {
        LogUtil.INSTANCE.d(LOGTAG, "doConnect()");
        // Check the prefs. If we don't have "nick" set, launch the prefs activity. That will
        // come back to onActivityResult, and if it's ok, we'll do the connect there.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String nick = prefs.getString("nick", "");
        if (nick.length() == 0) {
            startActivityForResult(new Intent(this, PrefActivity.class), PREFS_SET_BEFORE_CONNECT);
            return;
        }

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

    protected void doPersonalMessageDialog(String sendToNick)
    {
        final String nick = sendToNick;

        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.personal_message_entry_dialog);
        dialog.setTitle(nick);

        final TextView inputEditText = dialog.findViewById(R.id.input);
        inputEditText.setHorizontallyScrolling(false);
        inputEditText.setLines(Integer.MAX_VALUE);

        Button sendButton = dialog.findViewById(R.id.dialogButtonSend);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = inputEditText.getText().toString();
                addMessageToOutput("/m " + nick + " " + message);
                if (mClient != null) {
                    mClient.sendPersonalMessage(nick, message);
                }
                dialog.dismiss();
            }
        });

        Button cancelButton = dialog.findViewById(R.id.dialogButtonCancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    protected synchronized void updateConnectionMenuItemStatus()
    {
        if (mConnectMenuItem == null) {
            // menu hasn't been created yet
            return;
        }
        if (mClient != null) {
            mConnectMenuItem.setTitle(getString(R.string.disconnect));
        } else {
            mConnectMenuItem.setTitle(getString(R.string.connect));
        }
    }

    protected void doLogin() {
        if (mClient == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String nick = prefs.getString("nick", "");
        String group = prefs.getString("group","");
        String password = prefs.getString("password", "");
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
                String message = "<" + pkt.getNick() + "> " + pkt.getText();
                addMessageToOutput(message);
                break;
            }
            case AppMessages.EVT_PERSONAL_MSG: {
                PersonalPacket pkt = (PersonalPacket) msg.obj;
                String message = "<*" + pkt.getNick() + "*> " + pkt.getText();
                addMessageToOutput(message);
                break;
            }
            case AppMessages.EVT_COMMAND_OUTPUT: {
                CommandOutputPacket pkt = (CommandOutputPacket) msg.obj;
                addMessageToOutput(pkt.getCommandOutput());
                break;
            }

        }
        return true;
    }
}
