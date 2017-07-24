package com.grok.androidicb;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 *
 * The thread that actually does the reading from the Connection.
 * Created and managed by the IcbClient.
 *
 * Original Author: hoche@grok.com
 * Creation Date: 6/2/2017
 *
 */
public class IcbReadThread implements Runnable {
    private static final String LOGTAG = "IcbReadThread";
    private static final Boolean verbose = true;

    private IcbClient mIcbClient = null;
    private Socket mSocket = null;

    private Boolean mStop = false;

    private class IcbPacketBuffer {
        public int mSize;
        public int mOffset;
        public byte mBuffer[];

        public IcbPacketBuffer(int size) {
            mSize = size;
            mOffset = 0;
            mBuffer = new byte[size];
        }
    }

    public IcbReadThread(IcbClient client, Socket socket) {

        LogUtil.INSTANCE.d(LOGTAG, "initializing");
        mIcbClient = client;
        mSocket = socket;
    }

    public void run() {
        LogUtil.INSTANCE.d(LOGTAG, "running");

        if (mSocket == null || mIcbClient == null) {
            LogUtil.INSTANCE.d(LOGTAG, "run(): No socket or client.");
            if (mIcbClient != null) {
                mIcbClient.onReadThreadExit(0);
            }
            return;
        }

        InputStream istream = null;
        try {
            istream = mSocket.getInputStream();
        } catch (IOException e) {
            LogUtil.INSTANCE.d(LOGTAG, "run() IOException: " + e.getMessage());
            mIcbClient.onReadThreadExit(0);
            return;
        }

        IcbPacketBuffer pb = null;

        while (!mStop) {
            try {
                if (pb == null) {
                    // need the length byte
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Trying to read length byte");
                    }
                    // read one byte. This automatically casts it as an int in the range 0 - 255 so
                    // no unsigned conversion is necessary. Blocks.
                    int packetSize = istream.read();
                    if (packetSize == -1) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got EOF.");
                        mStop = true;
                        continue;
                    }

                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got packet length of " + packetSize);
                    }
                    pb = new IcbPacketBuffer(packetSize);
                }

                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): Trying to read " + (pb.mSize - pb.mOffset) + " bytes into offset " +  pb.mOffset);
                }
                // blocks.
                int bytesRead = istream.read(pb.mBuffer, pb.mOffset, pb.mSize - pb.mOffset);
                if (bytesRead == -1) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): Got EOF.");
                    mStop = true;
                    continue;
                }

                pb.mOffset += bytesRead;
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): Got " + bytesRead + " bytes. Moving offset to " + pb.mOffset);
                }

                if (pb.mOffset == pb.mSize) {
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got " + pb.mSize + " bytes. Parsing buffer and resetting offset to 0.");
                    }

                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, Utilities.hexdumpAlpha(pb.mBuffer, 0, pb.mSize));
                    }
                    mIcbClient.dispatch(new String(pb.mBuffer));
                    pb = null;
                }

            } catch (IOException e) {
                LogUtil.INSTANCE.d(LOGTAG, "run(): getInputStream() IOException: " + e.getMessage());
                mStop = true;
            }
        }

        LogUtil.INSTANCE.d(LOGTAG, "Read thread stopping..");
        mIcbClient.onReadThreadExit(0);
    }

    public void notifyStop()
    {
        mStop = true;
    }

}
