package com.grok.androidicb;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

import static com.grok.androidicb.Utilities.hexdump;
import static com.grok.androidicb.protocol.ICBProtocol.MAX_OPEN_MESSAGE_SIZE;

/**
 *
 * The thread that actually does the writing to the connection.
 * Created and managed by the IcbClient.
 *
 * Original Author: hoche@grok.com
 * Creation Date: 6/2/2017
 *
 */
public class IcbWriteThread implements Runnable {

    private static final String LOGTAG = "IcbWriteThread";
    private static final Boolean verbose = true;

    private IcbClient mIcbClient = null;
    private Socket mSocket = null;

    private Boolean mStop = false;

    private ArrayList<byte[]> mPacketList = null;

    public IcbWriteThread(IcbClient client, Socket socket) {
        LogUtil.INSTANCE.d(LOGTAG, "initializing");
        mIcbClient = client;
        mSocket = socket;
        mPacketList = new ArrayList<>();
    }

    public void sendPacket(String data) {

        int dataLen = data.length();

        // For now, truncate if the message is too long. Throw an exception or something - this
        // should never happen at this level.
        if (dataLen > MAX_OPEN_MESSAGE_SIZE) {
            dataLen = MAX_OPEN_MESSAGE_SIZE;
        }

        byte[] pkt = new byte[dataLen + 1];
        pkt[0] = (byte)(dataLen & 0xFF);
        try {
            System.arraycopy(data.getBytes("UTF8"), 0, pkt, 1, dataLen);
        } catch (java.io.UnsupportedEncodingException e) {
            LogUtil.INSTANCE.d(LOGTAG, "Can't convert data to UTF8 : " + data);
            return;
        }

        synchronized(this) {
            mPacketList.add(pkt);
        }
    }

    private synchronized byte[] getNextMessage() {
        if (mPacketList.isEmpty()) {
            return null;
        }
        return mPacketList.remove(0);
    }

    public void run() {
        LogUtil.INSTANCE.d(LOGTAG, "running");

        if (mSocket == null || mIcbClient == null) {
            LogUtil.INSTANCE.d(LOGTAG, "No socket or client.");
            if (mIcbClient != null) {
                mIcbClient.onWriteThreadExit(0);
            }
            return;
        }

        OutputStream ostream = null;
        try {
            ostream = mSocket.getOutputStream();
        } catch (IOException e) {
            LogUtil.INSTANCE.d(LOGTAG, "write() IOException: " + e.getMessage());
            mIcbClient.onWriteThreadExit(0);
            return;
        }

        while (!mStop) {
            try {
                byte[] msg = getNextMessage();

                if (msg == null) {
                    //LogUtil.INSTANCE.d(LOGTAG, "message stack empty. Sleeping.");
                    if (mStop) {
                        continue;
                    }
                    try {
                        Thread.sleep(100); // milliseconds
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                    continue;
                }

                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "Sending message: " + Utilities.hexdumpAlpha(msg, 0, msg.length));
                }
                ostream.write(msg); // blocks until write complete.
                LogUtil.INSTANCE.d(LOGTAG, "ostream.write() complete");

            } catch (IOException e) {
                LogUtil.INSTANCE.d(LOGTAG, "write() IOException: " + e.getMessage());
                mStop = true;
            }
        }

        LogUtil.INSTANCE.d(LOGTAG, "Write thread stopping..");
        mIcbClient.onWriteThreadExit(0);
    }

    public void notifyStop()
    {
        mStop = true;
    }
}
