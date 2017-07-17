package com.grok.androidicb;

import java.io.IOException;
import java.io.InputStream;

import static com.grok.androidicb.IcbClient.MAX_ICB_PACKET_LENGTH;

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

    private IcbClient mIcbClient = null;
    private SocketConnection mConnection = null;

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

    private static final Boolean verbose = false;

    public IcbReadThread(IcbClient pd, SocketConnection connection) {

        LogUtil.INSTANCE.d(LOGTAG, "initializing");
        mIcbClient = pd;
        mConnection = connection;
    }

    public void run() {
        LogUtil.INSTANCE.d(LOGTAG, "running");

        if (mConnection == null) {
            LogUtil.INSTANCE.d(LOGTAG, "run(): No Connection set.");
            if (mIcbClient != null) {
                mIcbClient.onReadThreadExit(0);
            }
            return;
        }

        int ret = 0;

        IcbPacketBuffer pb = null;

        while (!mStop) {
            try {
                // Make sure we have a valid input stream. Otherwise, ditch everything and start
                // over. Eventually the mConnection should reestablish (or just give up and kill
                // everything).
                InputStream istream = mConnection.getInputStream();
                if (istream == null) {
                    if (mStop) {
                        continue;
                    }
                    LogUtil.INSTANCE.d(LOGTAG, "run(): No Connection or InputStream. Sleeping.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                    }

                    if (mStop) {
                        continue;
                    }

                    // reset everything
                    pb = null;
                    if (mIcbClient != null) {
                        mIcbClient.reset();
                    }

                    continue;
                }

                // Note: java's istream.read() does not block. ret will have a short value if
                // it couldn't read the requested bytes, or -1 if the stream was closed.
                if (pb == null) {
                    // need the length byte
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Trying to read length byte");
                    }
                    int packetSize = istream.read(); // reads one byte and casts it as an int in the range 0 - 255 (so no unsigned conversion is necessary)
                    if (ret == -1) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got EOF.");
                        mConnection.notifyReadFailed();

                        if (mStop) {
                            continue;
                        }

                        try {
                            Thread.sleep(100);  // wait 100mS (this can be made shorter)
                        } catch (InterruptedException e) {
                            LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                        }

                        continue;
                    }

                    pb = new IcbPacketBuffer(packetSize);

                } else {
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Trying to read " + (pb.mSize - pb.mOffset) + " bytes into offset " +  pb.mOffset);
                    }
                    ret = istream.read(pb.mBuffer, pb.mOffset, pb.mSize - pb.mOffset);
                    if (ret == -1) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got EOF.");
                        mConnection.notifyReadFailed();

                        if (mStop) {
                            continue;
                        }

                        try {
                            Thread.sleep(100);  // wait 100mS (this can be made shorter)
                        } catch (InterruptedException e) {
                            LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                        }

                        continue;
                    }

                    pb.mOffset += ret;
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got " + ret + " bytes. Moving offset to " + pb.mOffset);
                    }
                }

                if (pb.mSize == pb.mSize) {
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got " + pb.mSize + " bytes. Parsing buffer and resetting offset to 0.");
                    }

                    if (mStop) {
                        continue;
                    }

                    if (mIcbClient != null) {
                        mIcbClient.dispatch(pb.mBuffer);
                    }
                    pb = null;
                }

            } catch (IOException e) {
                // We may get this we get disconnected
                LogUtil.INSTANCE.d(LOGTAG, "run(): getInputStream() IOException: " + e.getMessage());
                mConnection.notifyReadFailed();

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

        LogUtil.INSTANCE.d(LOGTAG, "Read thread stopping..");
        if (mIcbClient != null) {
            mIcbClient.onReadThreadExit(0);
        }
    }

    public void notifyStop()
    {
        mStop = true;
    }

}
