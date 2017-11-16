/*
 * This code modified for AndroidICB by hoche@grok.com. Original copyright
 * notice below:
 *
 * IcyBee - http://www.nuclearbunny.org/icybee/
 * A client for the Internet CB Network - http://www.icb.net/
 *
 * Copyright (C) 2000-2009 David C. Gibbons
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

/*
Packet Type: 'a' (Login)
Fields: Minimum: 5, Maximum: 7
    Field 0: Login id of user. Required.
    Field 1: Nickname to use upon login into ICB. Required.
    Field 2: Default group to log into in ICB, or do group who of. A null string for who listing will show all groups. Required.
    Field 3: Login command. Required. Currently one of the following:
    "login" log into ICB
    "w" just show who is currently logged into ICB
    Field 4: Password to authenticate the user to ICB. Required, but often blank.
    Field 5: If when logging in, default group (field 2) does not exist, create it with this status. Optional.
    Field 6: Protocol level. Optional. Deprecated.

Thus the ICB Login Packet has the following layout:

aLoginId^ANickname^ADefaultGroup^ACommand^APassword^AGroupStatus^AProtocolLevel
 */

package com.grok.androidicb.protocol;

import java.net.ProtocolException;


public class LoginPacket extends Packet {
    public LoginPacket(final String rawPacket) throws ProtocolException {
        super(rawPacket);
    }

    public LoginPacket(String id, String nick, String group, String command,
                       String passwd) {
        setPacketType(ICBProtocol.PKT_LOGIN);
        setField(0, id);
        setField(1, nick);
        setField(2, group);
        setField(3, command);
        setField(4, passwd);
    }
}
