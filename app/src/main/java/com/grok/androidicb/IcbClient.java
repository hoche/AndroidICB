package com.grok.androidicb;

import android.os.Handler;
import android.os.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    // cmds for communicating with the server
    private static final byte CMD_LOGIN          = 'a';
    private static final byte CMD_OPEN_MSG       = 'b';
    private static final byte CMD_PERSONAL_MSG   = 'c';
    private static final byte CMD_STATUS_MSG     = 'd';
    private static final byte CMD_ERROR_MSG      = 'e';
    private static final byte CMD_IMPORTANT_MSG  = 'f';
    private static final byte CMD_EXIT           = 'g';
    private static final byte CMD_COMMAND        = 'h';
    private static final byte CMD_COMMAND_OUTPUT = 'i';
    private static final byte CMD_PROTOCOL       = 'j';
    private static final byte CMD_BEEP           = 'k';
    private static final byte CMD_PING           = 'l';
    private static final byte CMD_PONG           = 'm';
    private static final byte CMD_NOOP           = 'n';


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


    boolean isValidOpCode(int opCode) {
        return ((opCode >= AOAProtocolDefs.CMD_PROTO_VERSION) && (opCode < AOAProtocolDefs.CMD_NULL));
    }

    // we're still in the ReadThread's context here, so to get things to the app we'll have to
    // get a message. Mostly we'll do all the parsing here and just send a limited subset of
    // messages up to the app when we're done.
    private void dispatch(byte[] packetData) {
        Message msg;
        switch (packetData[0]) {
            case CMD_LOGIN:
                // not really sure what to do with this. Just toss it up to the UI, I guess.
                LogUtil.INSTANCE.d(LOGTAG, "CMD_LOGIN");
                msg = mAppHandler.obtainMessage(EVT_LOGIN_OK);
                mAppHandler.sendMessage(msg);
                break;
            case CMD_OPEN_MSG:
                LogUtil.INSTANCE.d(LOGTAG, "Opcode CMD_OPEN_MSG");
                msg = mAppHandler.obtainMessage(EVT_OPEN_MSG, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_PERSONAL_MSG:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_PERSONAL_MSG");
                msg = mAppHandler.obtainMessage(EVT_PERSONAL_MSG, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_STATUS_MSG:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_STATUS_MSG");
                msg = mAppHandler.obtainMessage(EVT_STATUS_MSG, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_ERROR_MSG:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_ERROR_MSG");
                msg = mAppHandler.obtainMessage(EVT_ERROR_MSG, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_IMPORTANT_MSG:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_IMPORTANT_MSG");
                msg = mAppHandler.obtainMessage(EVT_IMPORTANT_MSG, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_EXIT:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_EXIT");
                msg = mAppHandler.obtainMessage(EVT_EXIT, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_COMMAND_OUTPUT:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_COMMAND_OUTPUT");
                msg = mAppHandler.obtainMessage(EVT_COMMAND_OUTPUT, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_PROTOCOL:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_PROTOCOL");
                msg = mAppHandler.obtainMessage(EVT_PROTOCOL, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_BEEP:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_BEEP");
                msg = mAppHandler.obtainMessage(EVT_BEEP, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_PING:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_PING");
                msg = mAppHandler.obtainMessage(EVT_PING, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_PONG:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_PONG");
                msg = mAppHandler.obtainMessage(EVT_PONG, AOAProtocolDefs.CMD_IMAGE, destId, data);  // what, arg1, arg2, obj
                mAppHandler.sendMessage(msg);
                break;
            case CMD_NOOP:
                LogUtil.INSTANCE.d(LOGTAG, "CMD_NOOP");
                break;
            case CMD_COMMAND:
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
