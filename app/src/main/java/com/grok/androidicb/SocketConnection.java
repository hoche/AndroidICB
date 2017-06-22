package com.grok.androidicb;

/**
 *
 * Generic wrapper around a TCP socket connection. It runs in a background thread and
 * and continually will try to reconnect to the dstHost.
 *
 * dstHost can be either a hostname or the string form of an ip address.
 *
 * Original Author: hoche@grok.com
 * Creation Date: 6/1/2017
 *
 */

import android.app.Activity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class SocketConnection implements Runnable {
    private static final String LOGTAG = "SocketConnection";

    Activity mActivity;
    String mDstHost;
    int mDstPort;
    InputStream mInputStream = null;
    OutputStream mOutputStream = null;
    Socket mSocket = null;
    boolean mIOFailed;


    public SocketConnection(Activity activity, String dstHost, int dstPort) {

        mActivity = activity;
        mDstHost = dstHost;
        mDstPort = dstPort;
        mIOFailed = false;

        // This can't run on the main thread or Android bitches about doing network stuff
        // on the UI thread and throws an exception. So launch it in the background.
        // There's no real functional reason to have this running in the background otherwise
        // since both the read and write threads are already in the background.
        new Thread(this, "SocketConnection").start();
    }

    @Override
    public void run() {
        LogUtil.INSTANCE.d(LOGTAG, "running");

        InetAddress serverAddr = null;
        try {
            // The host name can either be a machine name, such as "java.sun.com", or a textual representation of its IP address
            serverAddr = InetAddress.getByName(mDstHost);
        } catch (Exception e) {
            LogUtil.INSTANCE.e(LOGTAG, "Couldn't get IP address for " + mDstHost, e);
            return;
        }

        LogUtil.INSTANCE.d(LOGTAG, "run() Connecting to " + serverAddr.toString() + "(" + mDstHost + ") port " + mDstPort);

        // Note: Socket.isConnected() will still say "true" unless the remote has very cleanly
        // disconnected. If it just sends a RST, then isConnected will not change state. The
        // only way to change state is to try to read from or write to the socket. Unfortunately,
        // we don't do that in this class, so we have to use a callback.
        while (true) {
            if (mSocket == null || !mSocket.isConnected() || mIOFailed == true) {
                LogUtil.INSTANCE.d(LOGTAG, "SocketConnection() Socket not connected.");
                try {
                    mSocket = null;
                    mInputStream = null;
                    mOutputStream = null;

                    mSocket = new Socket(serverAddr, mDstPort);

                    LogUtil.INSTANCE.d(LOGTAG, "Got a connection to " + mDstHost + ":" + mDstPort);
                    mInputStream = mSocket.getInputStream();
                    mOutputStream = mSocket.getOutputStream();

                    LogUtil.INSTANCE.d(LOGTAG, "mInputStream: " + mInputStream + "   mOutputStream: " + mOutputStream);

                    mIOFailed = false;
                } catch (java.net.UnknownHostException e) {
                    LogUtil.INSTANCE.d(LOGTAG, "SocketConnection() UnknownHostException: " + e.getMessage());
                } catch (IOException e) {
                    LogUtil.INSTANCE.d(LOGTAG, "SocketConnection() IOException: " + e.getMessage());
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LogUtil.INSTANCE.d(LOGTAG, "SocketConnection() Sleep Interrupted");
            }
        }
    }

    public void notifyReadFailed()
    {
        mIOFailed = true;
    }

    public void notifyWriteFailed()
    {
        mIOFailed = true;
    }

    public InputStream getInputStream() {
        return mInputStream;
    }

    public OutputStream getOutputStream() {
        return mOutputStream;
    }

    public void close() throws IOException {
        if (mSocket != null) {
            mSocket.close();
        }
        mInputStream = null;
        mOutputStream = null;
    }

}
