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

import android.os.Handler;
import android.os.Message;

import com.grok.androidicb.protocol.CommandPacket;
import com.grok.androidicb.protocol.ICBProtocol;
import com.grok.androidicb.protocol.LoginPacket;
import com.grok.androidicb.protocol.OpenPacket;
import com.grok.androidicb.protocol.Packet;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.Socket;


class IcbClient {

    private static final String LOGTAG = "IcbClient";

    Socket mSocket;
    Handler mAppHandler;
    IcbReadThread mReadThread;
    IcbWriteThread mWriteThread;

    private static final Boolean verbose = false;

    public IcbClient(Socket socket, Handler handler) {
        mSocket = socket;
        mAppHandler = handler;

        LogUtil.INSTANCE.d(LOGTAG, "Starting IcbClient. Launching threads");

        mReadThread = new IcbReadThread(this, socket);
        mWriteThread = new IcbWriteThread(this, socket);

        try {
            new Thread(mReadThread, "IcbReadThread").start();
        } catch (Exception e) {
            LogUtil.INSTANCE.e(LOGTAG, "Couldn't start ReadThread. Unknown Exception error ", e);
        }
        try {
            new Thread(mWriteThread, "IcbWriteThread").start();
        } catch (Exception e) {
            LogUtil.INSTANCE.e(LOGTAG, "Couldn't start WriteThread. Unknown Exception error ", e);
        }
    }

    public void stop()
    {
        // This just sets the stop flag in each. The socket will remain open. Notify them first
        // in case they're in the middle of handling something and want to complete it.
        if (mReadThread != null) {
            mReadThread.notifyStop();
        }
        if (mWriteThread != null) {
            mWriteThread.notifyStop();
        }

        // Now close the socket. This may cause the threads to throw an Exception if they're
        // blocked on a read/write. That's ok.
        try {
            mSocket.close();
            mSocket = null;
        } catch (IOException e) {
            LogUtil.INSTANCE.e(LOGTAG, "Exception closing socket", e);
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
            mAppHandler.sendMessage(mAppHandler.obtainMessage(AppMessages.EVT_SOCKET_STOPPED));
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
            mAppHandler.sendMessage(mAppHandler.obtainMessage(AppMessages.EVT_SOCKET_STOPPED));
        }
    }

    private String removeControlCharacters(String s) {
        StringBuffer buf = new StringBuffer(s.length());
        char c;
        for (int i = 0, n = s.length(); i < n; i++) {
            c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                buf.append(' ');
            } else if (!Character.isISOControl(c)) {
                buf.append((c == '\n') ? ' ' : c);
            }
        }
        return buf.toString();
    }

    public void sendLogin(String id, String nick, String group, String command,
                     String passwd)
    {
        LoginPacket pkt = new LoginPacket(id, nick, group, command, passwd);
        mWriteThread.sendPacket(pkt.toString());
    }

    public void sendCommand(String cmd) {
        int cmdLength = cmd.length();
        if (cmd == null || cmdLength == 0) {
            return;
        }

        if (mWriteThread == null) {
            return;
        }

        // if the command does not begin with the command character, go ahead
        // and send it as an open message
        if (cmd.charAt(0) != '/') {
            sendOpenMessage(cmd);

            // if the command does begin with the command character, but it is
            // escaped by another command character, send it as an open message
        } else if (cmdLength > 1 && cmd.charAt(1) == '/') {
            sendOpenMessage(cmd.substring(1));

            // otherwise, go ahead and send the command to the server
        } else {
            sendPersonalMessage("server", cmd.substring(1));
        }
    }

    /**
     * Sends the provided text string as an open message to the
     * user's current group.
     */
    public void sendOpenMessage(String msg) {
        msg = removeControlCharacters(msg);

        /* send the message in maximum sized chunks */
        String currentMsg;
        String remaining = msg;
        int n;
        do {
            if (remaining.length() > ICBProtocol.MAX_OPEN_MESSAGE_SIZE) {
                currentMsg = remaining.substring(0, ICBProtocol.MAX_OPEN_MESSAGE_SIZE);
                n = currentMsg.lastIndexOf(' ');
                if (n > 0) {
                    currentMsg = currentMsg.substring(0, n + 1);
                }
                remaining = remaining.substring(currentMsg.length());
            } else {
                currentMsg = remaining;
                remaining = "";
            }

            OpenPacket p = new OpenPacket();
            p.setText(currentMsg);
            mWriteThread.sendPacket(p.toString());

        } while (remaining.length() > 0);
    }

    /**
     * Sends the provided text string as a personal message to the
     * specified user.
     */
    public void sendPersonalMessage(String nick, String origMsg) {
        String msg = removeControlCharacters(origMsg);

        // send the message in chunks
        String currentMsg;
        String remaining = msg;
        int n;
        do {
            if (remaining.length() > ICBProtocol.MAX_PERSONAL_MESSAGE_SIZE) {
                currentMsg = remaining.substring(0, ICBProtocol.MAX_PERSONAL_MESSAGE_SIZE);
                n = currentMsg.lastIndexOf(' ');
                if (n > 0) {
                    currentMsg = currentMsg.substring(0, n + 1);
                }
                remaining = remaining.substring(currentMsg.length());
            } else {
                currentMsg = remaining;
                remaining = "";
            }

            StringBuffer buf = new StringBuffer(nick.length() + 1 + currentMsg.length());
            buf.append(nick).append(' ').append(currentMsg);
            sendCommandMessage("m", buf.toString());

        } while (remaining.length() > 0);

    }

    /**
     * Sends the specified command and argument text to the server.
     */
    public void sendCommandMessage(final String command, final String msg) {
        mWriteThread.sendPacket(new CommandPacket(command, msg).toString());
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
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_LOGIN_OK);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_OPEN:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_OPEN");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_OPEN_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_PERSONAL:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PERSONAL");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_PERSONAL_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_STATUS:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_STATUS");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_STATUS_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_ERROR:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_ERROR");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_ERROR_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_IMPORTANT:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_IMPORTANT");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_IMPORTANT_MSG, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_EXIT:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_EXIT");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_EXIT, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_COMMAND:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_COMMAND");
                    break;
                case PKT_COMMAND_OUT:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_COMMAND_OUTPUT");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_COMMAND_OUTPUT, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_PROTOCOL:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PROTOCOL");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_PROTOCOL, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_BEEP:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_BEEP");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_BEEP, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    break;
                case PKT_PING:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PING");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_PING, 0, 0, pkt);
                    mAppHandler.sendMessage(msg);
                    // also create a new ping messge here.
                    break;
                case PKT_PONG:
                    LogUtil.INSTANCE.d(LOGTAG, "PKT_PONG");
                    msg = mAppHandler.obtainMessage(AppMessages.EVT_PONG, 0, 0, pkt);
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
