package com.grok.androidicb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    private IcbClient mProtocolDispatcher = null;
    private SocketConnection mConnection = null;

    private Boolean mStop = false;

    private class IcbPacket {
        public int len;
        public ByteArrayOutputStream buffer;

        public IcbPacket() {
            len = -1;
            buffer = new ByteArrayOutputStream();
        }
    };

    IcbPacket mIcbPacket;

    long startTime = 0;

    private static final Boolean verbose = false;

    public IcbReadThread(IcbClient pd, SocketConnection connection) {

        LogUtil.INSTANCE.d(LOGTAG, "initializing");
        mProtocolDispatcher = pd;
        mConnection = connection;
        mIcbPacket = null;
    }

    private void parseBuffer(byte[] buffer)
    {
        int bufferIdx = 0;

        if (verbose) {
            LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): got buffer: " + Utilities.hexdump(buffer, 0, 16));
        }

        while (bufferIdx < buffer.length) {
            if (mIcbPacket == null) { // not in a packet

                startTime = System.currentTimeMillis();
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): Looking for new packet at idx " + bufferIdx);
                }

                int idx = bufferIdx;

                mIcbPacket = new IcbPacket();

                mIcbPacket.len = unsignedByteUtilities.bytesToInt32(buffer[idx + AOADefs.AOA_FRAME_PREAMBLE_LEN], buffer[idx + AOADefs.AOA_FRAME_PREAMBLE_LEN + 1],
                        buffer[idx + AOADefs.AOA_FRAME_PREAMBLE_LEN + 2], buffer[idx + AOADefs.AOA_FRAME_PREAMBLE_LEN + 3]);
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): The header says the packet will be " + mAOAPacket.len + " bytes.");
                }
                if (mAOAPacket.len < 0) {
                    LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): Invalid packet len (" + mAOAPacket.len + " bytes). Discarding.");
                    return;
                }
                bufferIdx = idx + (AOADefs.AOA_FRAME_PREAMBLE_LEN + AOADefs.AOA_FRAME_LEN_LEN);
            }

            // If the packet doesn't fill a complete USB block, this should be shorter
            // than the buffer.
            int bytesToRead = mAOAPacket.len - mAOAPacket.buffer.size();
            int bytesAvail = buffer.length - bufferIdx;
            if (bytesAvail < bytesToRead) {
                bytesToRead = bytesAvail;
            }

            if (verbose) {
                LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): Expecting " + mAOAPacket.len + " bytes in the packet.");
                LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): Have collected " + mAOAPacket.buffer.size() + " so far.");
                LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): " + bytesAvail + " are left in this block.");
                LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): Copying " + bytesToRead + " bytes starting at idx " + bufferIdx + " into the packet.");
            }
            mAOAPacket.buffer.write(buffer, bufferIdx, bytesToRead);
            bufferIdx += bytesToRead;

            // Got a complete packet
            if (mAOAPacket.buffer.size() == mAOAPacket.len) {
                long endTime = System.currentTimeMillis();
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "parseBuffer(): Complete packet. Length: " + mAOAPacket.buffer.size() +
                            " Time: " + (endTime - startTime) + "ms");
                }

                // dispatch
                if (mProtocolDispatcher != null) {
                    InputStream hdrstream = new ByteArrayInputStream(mAOAPacket.buffer.toByteArray());
                    mProtocolDispatcher.process(hdrstream);
                }

                // and reset
                mAOAPacket = null;
            }
        }
    }

    public void run() {
        LogUtil.INSTANCE.d(LOGTAG, "running");

        if (mConnection == null) {
            LogUtil.INSTANCE.d(LOGTAG, "run(): No Connection set.");
            if (mProtocolDispatcher != null) {
                mProtocolDispatcher.onReadThreadExit(0);
            }
            return;
        }

        int ret = 0;
        byte[] buffer = new byte[IcbClient.DEFAULT_RECEIVE_BLOCK_SIZE];
        int offset = 0;

        // This outer loop just reads 512 bytes at a time from the connection. If it
        // doesn't get an even 512 byte block, it will spin around until it has one. Once
        // that happens, it feeds it to parseBuffer().
        while (!mStop) {
            try {
                // read until all data read, EOF, or exception thrown. blocks until one of those things happens.
                // returns total number of bytes read or -1 if EOF
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
                    offset = 0;
                    if (mProtocolDispatcher != null) {
                        mProtocolDispatcher.reset();
                    }
                    mIcbPacket = null;

                    continue;
                }

                // this does not block. ret will have a short value if we couldn't read
                // the requested bytes, or -1 if the stream was closed.
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): Trying to read " + (AOADefs.DEFAULT_RECEIVE_BLOCK_SIZE - offset) + " bytes into offset " + offset);
                }
                ret = istream.read(buffer, offset, AOADefs.DEFAULT_RECEIVE_BLOCK_SIZE - offset);
                if (ret == -1) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): Got EOF.");
                    mConnection.notifyReadFailed();

                    if (mStop) {
                        continue;
                    }

                    try {
                        Thread.sleep(100);  // wait a 100mS (this can be made shorter)
                    } catch (InterruptedException e) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): sleep interrupted");
                    }

                    continue;
                }

                offset += ret;
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "run(): Got " + ret + " bytes. Moving offset to " + offset);
                }

                if (offset == AOADefs.DEFAULT_RECEIVE_BLOCK_SIZE) {
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "run(): Got " + offset + " bytes. Parsing buffer and resetting offset to 0.");
                    }

                    if (mStop) {
                        continue;
                    }

                    parseBuffer(buffer);
                    offset = 0;
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
        if (mProtocolDispatcher != null) {
            mProtocolDispatcher.onReadThreadExit(0);
        }
    }

    public void notifyStop()
    {
        mStop = true;
    }

}
