package com.grok.androidicb;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import static com.grok.androidicb.IcbClient.MAX_ICB_PACKET_LENGTH;
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

    private IcbClient mProtocolDispatcher;
    private SocketConnection mConnection;

    private Boolean mStop = false;

    private ArrayList<byte[]> mMessageList = null;

    public IcbWriteThread(IcbClient pd, SocketConnection connection) {
        LogUtil.INSTANCE.d(LOGTAG, "initializing");
        mProtocolDispatcher = pd;
        mConnection = connection;
        mMessageList = new ArrayList<>();
    }

    public void addMessage(char cmd, String data) {

        int dataLen = data.length();
        byte[] msg = new byte[dataLen + 2];

        // For now, truncate if the message is too long. In the future, we should
        // generate multiple packets.
        if (dataLen > MAX_OPEN_MESSAGE_SIZE) {
            dataLen = MAX_OPEN_MESSAGE_SIZE;
        }

        // copy in the data. arraycopy(src, srcPos, dst, dstPos, numElem)
        try {
            System.arraycopy(data.getBytes("UTF8"), 0, msg, 2, dataLen);
        } catch (java.io.UnsupportedEncodingException e) {
            LogUtil.INSTANCE.d(LOGTAG, "Can't convert data to UTF8 : " + data);
            return;
        }

        // set packet stuff
        msg[0] = (byte)((dataLen + 1) & 0xFF);
        msg[1] = (byte)(cmd & 0xFF);

        synchronized(this) {
            mMessageList.add(msg);
        }
    }

    private synchronized byte[] getNextMessage() {
        if (mMessageList.isEmpty()) {
            return null;
        }
        return mMessageList.remove(0);
    }

    public void run() {
        LogUtil.INSTANCE.d(LOGTAG, "running");

        if (mConnection == null) {
            LogUtil.INSTANCE.d(LOGTAG, "No Connection set.");
            if (mProtocolDispatcher != null) {
                mProtocolDispatcher.onReadThreadExit(0);
            }
            return;
        }

        int ret = 0;

        while (!mStop) {

            try {
                OutputStream ostream = mConnection.getOutputStream();
                if (ostream == null) {
                    LogUtil.INSTANCE.d(LOGTAG, "No Connection or OutputStream. Sleeping.");
                    if (mStop) {
                        continue;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                    }
                    continue;
                }

                byte[] msg = getNextMessage();

                if (msg == null) {
                    //LogUtil.INSTANCE.d(LOGTAG, "message stack empty. Sleeping.");
                    if (mStop) {
                        continue;
                    }
                    try {
                        Thread.sleep(20); // milliseconds
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                    continue;
                }

                LogUtil.INSTANCE.d(LOGTAG, "Sending message.");
                if (ostream == null) {
                    if (mStop) {
                        continue;
                    }
                    LogUtil.INSTANCE.d(LOGTAG, "No Connection or OutputStream. Sleeping.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                    }
                    continue;
                }

                if (mStop) {
                    continue;
                }
                LogUtil.INSTANCE.d(LOGTAG, "ostream.write()");
                ostream.write(msg); // blocks until write complete.
                LogUtil.INSTANCE.d(LOGTAG, "ostream.write() complete");

            } catch (IOException e) {
                LogUtil.INSTANCE.d(LOGTAG, "write() IOException: " + e.getMessage());
                mConnection.notifyWriteFailed();

                if (mStop) {
                    continue;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException eInterrupted) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                }
            }
        }

        LogUtil.INSTANCE.d(LOGTAG, "Write thread stopping..");
        if (mProtocolDispatcher != null) {
            mProtocolDispatcher.onWriteThreadExit(0);
        }
    }

    public void notifyStop()
    {
        mStop = true;
    }
}
