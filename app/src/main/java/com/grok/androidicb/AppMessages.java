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

public class AppMessages {
    // events for communicating with the UI
    public static final int EVT_SOCKET_CONNECTED = 100;
    public static final int EVT_SOCKET_STOPPED   = 101;
    public static final int EVT_LOGIN_OK         = 102;
    public static final int EVT_OPEN_MSG         = 103;
    public static final int EVT_PERSONAL_MSG     = 104;
    public static final int EVT_STATUS_MSG       = 105;
    public static final int EVT_ERROR_MSG        = 106;
    public static final int EVT_IMPORTANT_MSG    = 107;
    public static final int EVT_EXIT             = 108;
    public static final int EVT_COMMAND_OUTPUT   = 109;
    public static final int EVT_PROTOCOL         = 110;
    public static final int EVT_BEEP             = 111;
    public static final int EVT_PING             = 112;
    public static final int EVT_PONG             = 113;
}
