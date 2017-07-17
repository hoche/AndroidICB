package com.grok.androidicb;

import android.os.Handler;
import android.os.Message;

import com.grok.androidicb.protocol.Packet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;

class IcbClient {

    private static final String LOGTAG = "IcbClient";

    // events for communicating with the UI
    public static final int EVT_SOCKET_STOPPED   = 1;
    public static final int EVT_LOGIN_OK         = 2;
    public static final int EVT_OPEN_MSG         = 3;
    public static final int EVT_PERSONAL_MSG     = 4;
    public static final int EVT_STATUS_MSG       = 5;
    public static final int EVT_ERROR_MSG        = 6;
    public static final int EVT_IMPORTANT_MSG    = 7;
    public static final int EVT_EXIT             = 8;
    public static final int EVT_COMMAND_OUTPUT   = 9;
    public static final int EVT_PROTOCOL         = 10;
    public static final int EVT_BEEP             = 11;
    public static final int EVT_PING             = 12;
    public static final int EVT_PONG             = 13;


    public static final int MAX_ICB_PACKET_LENGTH = 255;  // including the command byte, but NOT the length byte

    Handler mAppHandler;
    IcbReadThread mReadThread;
    IcbWriteThread mWriteThread;

    private static final Boolean verbose = false;

    public IcbClient(SocketConnection connection, Handler handler) {
        mAppHandler = handler;

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



    /*****************************************************************************
     * Inbound side
     *****************************************************************************/

    // we're still in the ReadThread's context here. Parse the packet and then either throw it up
    // to the UI or compose a response directly.
    public void dispatch(final String rawPacket) {
        Message msg;
        try {
            Packet pkt = Packet.getInstance(rawPacket);
            switch (pkt.getPacketType()) {
                case PKT_LOGIN:
                    // not really sure what to do with this. Just toss it up to the UI, I guess.
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_LOGIN");
                    msg = mAppHandler.obtainMessage(EVT_LOGIN_OK);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_OPEN:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_OPEN");
                    msg = mAppHandler.obtainMessage(EVT_OPEN_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_PERSONAL:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PERSONAL");
                    msg = mAppHandler.obtainMessage(EVT_PERSONAL_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_STATUS:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_STATUS");
                    msg = mAppHandler.obtainMessage(EVT_STATUS_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_ERROR:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_ERROR");
                    msg = mAppHandler.obtainMessage(EVT_ERROR_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_IMPORTANT:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_IMPORTANT");
                    msg = mAppHandler.obtainMessage(EVT_IMPORTANT_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_EXIT:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_EXIT");
                    msg = mAppHandler.obtainMessage(EVT_EXIT, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_COMMAND:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_COMMAND");
                    break;
                case PKT_COMMAND_OUT:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_COMMAND_OUT");
                    msg = mAppHandler.obtainMessage(EVT_COMMAND_OUTPUT, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_PROTOCOL:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PROTOCOL");
                    msg = mAppHandler.obtainMessage(EVT_PROTOCOL, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_BEEP:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_BEEP");
                    msg = mAppHandler.obtainMessage(EVT_BEEP, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_PING:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PING");
                    msg = mAppHandler.obtainMessage(EVT_PING, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    // also create a new ping messge here.
                    break;
                case PKT_PONG:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PONG");
                    msg = mAppHandler.obtainMessage(EVT_PONG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_NOOP:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_NOOP");
                    break;
                default:
                    LogUtil.INSTANCE.d(LOGTAG, "Unknown packetType");
                    break;
            }
        } catch (ProtocolException e) {
            LogUtil.INSTANCE.d(LOGTAG, "dispatch() IOException: " + e.getMessage());
        }
    }
}
