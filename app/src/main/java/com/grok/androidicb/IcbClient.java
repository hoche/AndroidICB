package com.grok.androidicb;

import android.os.Handler;
import android.os.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class IcbClient {

    private static final String LOGTAG = "IcbClient";

    public static final int EVT_SOCKET_STOPPED   = 1;
    public static final int EVT_ICB_MESSAGE   = 2;


    Handler mAppHandler;
    IcbReadThread mReadThread;
    IcbWriteThread mWriteThread;

    private class IcbMessage {
        public int length;
        public int action;
        public boolean haveFullPkt;

        public ByteArrayOutputStream buffer;

        public IcbMessage() {
            length = -1;
            action = -1;
            buffer = new ByteArrayOutputStream();
        }
    };

    IcbMessage mIcbMessage;

    private static final Boolean verbose = false;

    public IcbClient(SocketConnection connection, Handler handler) {
        mAppHandler = handler;

        mIcbMessage = null;

        LogUtil.INSTANCE.d(LOGTAG, "Starting IcbClient. Launching threads");

        mReadThread = new IcbReadThread(this, connection);
        mWriteThread = new IcbWriteThread(this, connection);

        try {
            new Thread(mReadThread, "IcbReadThread").start();
        } catch (Exception e) {
            LogUtil.INSTANCE.d(LOGTAG, "Couldn't start mReadThread. Unknown Exception error " + e.getMessage() + "\n" + e.getMessage());
        }
        try {
            new Thread(mWriteThread, "IcbWriteThread").start();
        } catch (Exception e) {
            LogUtil.INSTANCE.d(LOGTAG, "Couldn't start mWriteThread. Unknown Exception error " + e.getMessage() + "\n" + e.getMessage());
        }
    }

    public void stop()
    {
        if (mReadThread != null) {
            mReadThread.notifyStop();
        }
        if (mWriteThread != null) {
            mWriteThread.notifyStop();
        }
    }

    // called from within the ReadThread just before it stops.
    public synchronized void onReadThreadExit(int err)
    {
        LogUtil.INSTANCE.d(LOGTAG, "onReadThreadExit");
        mIcbMessage = null;
        mReadThread = null;
        if (mWriteThread != null) {
            mWriteThread.notifyStop();
        } else {
            LogUtil.INSTANCE.d(LOGTAG, "Sending stop message to main app");
            mAppHandler.sendMessage(mAppHandler.obtainMessage(IcbClient.EVT_SOCKET_STOPPED));
        }
    }

    // called from within the WriteThread just before it stops.
    public synchronized void onWriteThreadExit(int err)
    {
        LogUtil.INSTANCE.d(LOGTAG, "onWriteThreadExit");
        mWriteThread = null;
        if (mReadThread != null) {
            mReadThread.notifyStop();
        } else {
            LogUtil.INSTANCE.d(LOGTAG, "Sending stop message to main app");
            mAppHandler.sendMessage(mAppHandler.obtainMessage(IcbClient.EVT_SOCKET_STOPPED));
        }
    }

    private static byte[] readBuffer(InputStream istream, int len) throws IOException {
        byte readBuffer[] = new byte[len];
        int index = 0;
        int bytesToRead = len;
        while (bytesToRead > 0) {
            int amountRead = istream.read(readBuffer, index, bytesToRead);
            if (amountRead == -1) {
                throw new RuntimeException("End of stream reached.");
            }
            bytesToRead -= amountRead;
            index += amountRead;
        }
        return readBuffer;
    }

    public synchronized void reset()
    {
        mIcbMessage = null;
    }

    // handle input typed into the input box by the user
    public void processInput(String line)
    {
        if (line.charAt(0) == '/') {

            if (!line.contains(" ")) {
                LogUtil.INSTANCE.d(LOGTAG, "Wrong number of args. No target name.");
                return;
            }

            String[] holder = line.split(" ", 3);
            if (holder.length < 3) {

                // handle 2-part commands here - /beep, /kick, /p etc
                if (holder[0].equalsIgnoreCase("/beep")) {
                    sendBeep(holder[1]);
                    return;
                }

                LogUtil.INSTANCE.d(LOGTAG, "Unknown command." + holder[0]);
                return;
            }

            // handle 3-part commands here
            if (holder[0].equalsIgnoreCase("/m")) {
                sendPrivateMessage(holder[1], holder[2]);
                return;
            }

            LogUtil.INSTANCE.d(LOGTAG, "Unknown command." + holder[0]);
            return;
        }
        sendOpenMessage(line);
    }

    private void sendOpenMessage(String line) {
        int maxdatalen = 254 - 1; // extra room for null byte
        while (line.length() > maxdatalen) {
            String sendPart = line.substring(0, maxdatalen) + '\0';
            mWriteThread.addMessage('b', sendPart);
            line = line.substring(maxdatalen + 1);
        }
        if (line.length() > 0) {
            mWriteThread.addMessage('b', line + '\0');
        }
    }

    // hm\001nick message
    private void sendPrivateMessage(String who, String line)
    {
        int maxdatalen = 253 - who.length() - 4;
        while (line.length() > maxdatalen) {
            String sendPart = 'm' + '\001' + who + ' ' + line.substring(0, maxdatalen) + '\0';
            mWriteThread.addMessage('h', sendPart);
            line = line.substring(maxdatalen + 1);
        }
        if (line.length() > 0) {
            mWriteThread.addMessage('h', 'm' + '\001' + who + ' ' + line + '\0');
        }
    }

    // hbeep\001nick
    private void sendBeep(String who)
    {
        mWriteThread.addMessage('h', "beep" + '\001' + who + '\0');
    }


    // XXX problem:
    // When the lower level connects, it will search for a lower-level packet and assure that
    // that's on a proper boundary, but it does not guarantee that that will be on the bounds
    // of an upper-layer packet (i.e. this layer).
    // So, we may need to make a marker or something to search for in the upper-layer, just
    // like we do for the lower layer.
    public synchronized void process(InputStream istream) {
        int avail = 0;
        try {
            while ((avail = istream.available()) > 0) {
                if (mAOAMessage == null || !mAOAMessage.haveFullHeader) { // not in a message
                    if (mAOAMessage == null) {
                        if (verbose) {
                            LogUtil.INSTANCE.d(LOGTAG, "Starting new packet");
                        }
                        mAOAMessage = new AOAMessage();
                    } else {
                        if (verbose) {
                            LogUtil.INSTANCE.d(LOGTAG, "Resuming previously started packet.");
                        }
                    }

                    if (mAOAMessage.destPort == -1 && istream.available() < 2) {
                        return;
                    }
                    mAOAMessage.destPort = Utilities.readInt16(istream);

                    if (mAOAMessage.srcPort == -1 && istream.available() < 2) {
                        return;
                    }
                    mAOAMessage.srcPort = Utilities.readInt16(istream);

                    if (mAOAMessage.len == -1 && istream.available() < 4) {
                        return;
                    }
                    mAOAMessage.len = Utilities.readInt32(istream);

                    if (mAOAMessage.version == -1 && istream.available() < 1) {
                        return;
                    }
                    mAOAMessage.version = Utilities.readByte(istream);

                    if (mAOAMessage.opCode == -1 && istream.available() < 1) {
                        return;
                    }
                    mAOAMessage.opCode = Utilities.readByte(istream);

                    mAOAMessage.haveFullHeader = true;
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "destPort: " + mAOAMessage.destPort);
                        LogUtil.INSTANCE.d(LOGTAG, "srcPort: " + mAOAMessage.srcPort);
                        LogUtil.INSTANCE.d(LOGTAG, "len: " + mAOAMessage.len);
                        LogUtil.INSTANCE.d(LOGTAG, "version: " + mAOAMessage.version);
                        LogUtil.INSTANCE.d(LOGTAG, "opCode: " + mAOAMessage.opCode);
                    }

                }

                // The lower level just guarantees that it got a complete lower-level packet. This
                // may not be enough to make an upper-level packet. So, if len is shorter than
                // bytes available, we need to spin around and gather more.
                int bytesToRead = mAOAMessage.len - mAOAMessage.buffer.size();
                if (verbose) {
                    LogUtil.INSTANCE.d(LOGTAG, "Need to read " + bytesToRead + " bytes to fill this packet.");
                }
                avail = istream.available();
                if (avail < bytesToRead) {
                    if (verbose) {
                        LogUtil.INSTANCE.d(LOGTAG, "Only " + avail + " bytes available for reading.");
                    }
                    bytesToRead = avail;
                }

                mAOAMessage.buffer.write(readBuffer(istream, bytesToRead));

                if (mAOAMessage.buffer.size() == mAOAMessage.len) {
                    LogUtil.INSTANCE.d(LOGTAG, "Packet is complete, with " + mAOAMessage.buffer.size() + " bytes.");
                    // check and dispatch
                    if (verbose) {
                        int dumpLen = (mAOAMessage.len > 32 ? 32 : mAOAMessage.len);
                        if (dumpLen < 32) {
                            LogUtil.INSTANCE.d(LOGTAG, "data: " + Utilities.hexdump(mAOAMessage.buffer.toByteArray(), 0, dumpLen));
                        } else {
                            byte[] tmpbuf = mAOAMessage.buffer.toByteArray();
                            LogUtil.INSTANCE.d(LOGTAG, "data: ");
                            LogUtil.INSTANCE.d(LOGTAG, Utilities.hexdump(tmpbuf, 0, 16));
                            LogUtil.INSTANCE.d(LOGTAG, "...");
                            LogUtil.INSTANCE.d(LOGTAG, Utilities.hexdump(tmpbuf, tmpbuf.length - 16, 16));
                        }
                    }

                    if (mAOAMessage.version != AOADefs.AOA_MESSAGE_VERSION) {
                        LogUtil.INSTANCE.d(LOGTAG, "Unknown version. Skipping");
                    } else if (!isValidOpCode(mAOAMessage.opCode)) {
                        LogUtil.INSTANCE.d(LOGTAG, "Invalid opCode. Skipping");
                    } else {
                        if (verbose) {
                            LogUtil.INSTANCE.d(LOGTAG, "DISPATCHING");
                        }
                        dispatch(mAOAMessage.opCode, mAOAMessage.destPort, mAOAMessage.buffer.toByteArray());
                    }

                    // and reset
                    mAOAMessage = null;
                }
            }
        } catch (IOException e) {
            LogUtil.INSTANCE.d(LOGTAG, "process() IOException error " + e.getMessage() + "\n" + e.getMessage());
        } catch (Exception e) {
            LogUtil.INSTANCE.d(LOGTAG, "process() Unknown Exception error " + e.getMessage() + "\n" + e.getMessage());
        }
    }

    boolean isValidOpCode(int opCode) {
        return ((opCode >= AOAProtocolDefs.CMD_PROTO_VERSION) && (opCode < AOAProtocolDefs.CMD_NULL));
    }

    private void dispatch(int opCode, int destId, byte[] data) {
        Message msg;
        switch (opCode) {
            case AOAProtocolDefs.CMD_TEXT:
                LogUtil.INSTANCE.d(LOGTAG, "Opcode CMD_TEXT");
                String text = new String(data); // convert to a readable string. Just using toString won't do it
                msg = mAppHandler.obtainMessage(AOADefs.EVT_AOA_MESSAGE, AOAProtocolDefs.CMD_TEXT, destId, text);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case AOAProtocolDefs.CMD_IMAGE:
                LogUtil.INSTANCE.d(LOGTAG, "Opcode CMD_IMAGE");
                msg = mAppHandler.obtainMessage(AOADefs.EVT_AOA_MESSAGE, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            default:
                LogUtil.INSTANCE.d(LOGTAG, "Unhandled OpCode: " + opCode);
                // do nothing
                break;
        }
    }


    /*****************************************************************************
     * Outbound side
     *****************************************************************************/

    // On the outbound side, we can just send whatever we want. We don't need to worry
    // about packet coalescing since the android will just send it when it has it.
    public void sendCommand(int opCode, int destPort, int srcPort, byte[] payload)
    {
        if (payload.length == 0) {
            return;
        }

        LogUtil.INSTANCE.d(LOGTAG, "Creating stream");
        InputStream istream = new ByteArrayInputStream(payload);

        int maxDataLen = AOADefs.DEFAULT_SEND_BLOCK_SIZE - AOADefs.AOA_MSG_HEADER_LEN;

        int len = 0;
        try {
            while ( (len = istream.available()) > 0) {
                if (len > maxDataLen) {
                    len = maxDataLen;
                }

                byte[] buffer = new byte[AOADefs.AOA_MSG_HEADER_LEN + len];

                LogUtil.INSTANCE.d(LOGTAG, "sendCommand() created a message of " + (AOADefs.AOA_MSG_HEADER_LEN + len) + " bytes");

                // set up the header
                buffer[0] = (byte)((destPort & 0xFF00) >> 8);
                buffer[1] = (byte)(destPort & 0x00FF);
                buffer[2] = (byte)((srcPort & 0xFF00) >> 8);
                buffer[3] = (byte)(srcPort & 0x00FF);
                buffer[4] = (byte)((len & 0xFF000000) >> 24);
                buffer[5] = (byte)((len & 0x00FF0000) >> 16);
                buffer[6] = (byte)((len & 0x0000FF00) >> 8);
                buffer[7] = (byte)(len & 0x000000FF);
                buffer[8] = AOADefs.AOA_MESSAGE_VERSION;
                buffer[9] = (byte)(opCode & 0x00FF);

                // copy in the data from the istream
                LogUtil.INSTANCE.d(LOGTAG, "sendCommand(): copying data");
                istream.read(buffer, AOADefs.AOA_MSG_HEADER_LEN, len);

                if (mWriteThread == null) {
                    LogUtil.INSTANCE.d(LOGTAG, "sendCommand(): no mWriteThread!");
                    return;
                }
                LogUtil.INSTANCE.d(LOGTAG, "sendCommand(): adding message to WriteThread");
                mWriteThread.addMessage(buffer);
            }
        } catch (IOException e) {
            LogUtil.INSTANCE.d(LOGTAG, "sendCommand() IOException: " + e.getMessage());
        } catch (Exception e) {
            LogUtil.INSTANCE.d(LOGTAG, "sendCommand() Exception: " + e.getMessage());
        }

    }
}
