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

package com.grok.androidicb.protocol;

import java.net.ProtocolException;


public class PersonalPacket extends Packet {
    public PersonalPacket(final String rawPacket) throws ProtocolException {
        super(rawPacket);
    }

    public String getNick() {
        return getField(0);
    }

    public String getText() {
        return getField(1);
    }
}
