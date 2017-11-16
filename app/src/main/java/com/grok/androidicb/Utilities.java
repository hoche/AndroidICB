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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.InflaterInputStream;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.preference.PreferenceManager;

public class Utilities {
	public static void centerAround(int x, int y, Drawable d) {
		int w = d.getIntrinsicWidth();
		int h = d.getIntrinsicHeight();
		int left = x - w / 2;
		int top = y - h / 2;
		int right = left + w;
		int bottom = top + h;
		d.setBounds(left, top, right, bottom);
	}

	public static int indexOf(int searchArray[], int itemToFind) {
		for (int i = 0; i < searchArray.length; ++i) {
			if (itemToFind == searchArray[i]) {
				return i;
			}
		}
		return -1;
	}

	public static int indexOf(String searchArray[], String itemToFind) {
		for (int i = 0; i < searchArray.length; ++i) {
			if (itemToFind.equals(searchArray[i])) {
				return i;
			}
		}
		return -1;
	}

	public static int stringToFourCC(String stringCode) {
		int theCode = 0;
		int count = Math.max(stringCode.length(), 4);
		for (int i = 0; i < count; ++i) {
			char c = stringCode.charAt(i);
			theCode <<= 8;
			theCode |= c;
		}
		return theCode;
	}

	public static String fourCCToString(int fourCC) {
        return "'" +
                ((fourCC & 0xFF000000) >> 24) +
                ((fourCC & 0x00FF0000) >> 16) +
                ((fourCC & 0x0000FF00) >> 8) +
                (fourCC & 0x000000FF) +
                '\'';
	}

	public static void enablePreference(PreferenceManager preferenceMgr,
			String prefKey, Boolean isEnabled) {
		Preference pref = preferenceMgr.findPreference(prefKey);
		if (pref != null) {
			pref.setEnabled(isEnabled);
		}
	}

	public static String formatTime(Context context, Date dateToFormat) {
		java.text.DateFormat df = android.text.format.DateFormat
				.getTimeFormat(context);
		return df.format(dateToFormat);
	}

	public static String formatTime(Context context, int timeValue) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.HOUR_OF_DAY, timeValue / 60);
		cal.set(Calendar.MINUTE, timeValue % 60);
		Date date = cal.getTime();
		return formatTime(context, date);
	}

	public static int dateToTimeValue(int hours, int minutes) {
		return hours * 60 + minutes;
	}

    /* getHours/GetMinutes is deprecated
	public static int dateToTimeValue(Date date) {
		return dateToTimeValue(date.getHours(), date.getMinutes());
	}
	*/

	private static final byte[] HEX_CHAR = new byte[] { '0', '1', '2', '3',
			'4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	/**
	 * Helper function that dump an array of bytes in hex form
	 * 
	 * @param buffer
	 *            The bytes array to dump
	 * @return A string representation of the array of bytes
	 */
	public static String hexdump(byte[] buffer, int offset, int amountToDump) {
		if (buffer == null) {
			return "";
		}

		StringBuilder sb = new StringBuilder();

		amountToDump = Math.min(offset + amountToDump, buffer.length);

		for (int i = offset; i < amountToDump; i++) {
			sb.append("0x")
					.append((char) (HEX_CHAR[(buffer[i] & 0x00F0) >> 4]))
					.append((char) (HEX_CHAR[buffer[i] & 0x000F]))
					.append(" ");
		}

		return sb.toString();
	}

    public static String hexdumpAlpha(byte[] buffer, int offset, int amountToDump) {
        if (buffer == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        amountToDump = Math.min(offset + amountToDump, buffer.length);

        for (int i = offset; i < amountToDump; i++) {
            sb.append("0x")
                    .append((char) (HEX_CHAR[(buffer[i] & 0x00F0) >> 4]))
                    .append((char) (HEX_CHAR[buffer[i] & 0x000F]))
                    .append(" ");
            if (buffer[i] >= 0x20 & buffer[i] < 0x7F) {
                sb.append("'")
					.append((char)buffer[i])
					.append("' ");
            } else {
                sb.append("'.' ");
            }
        }

        return sb.toString();
    }

	public static int unsignedByte(byte b) {
		return b & 0xff;
	}

	public static int[] byteArrayToIntArray(byte[] byteArray) {
		int[] intArray = new int[byteArray.length];
		for (int i = 0; i < intArray.length; ++i) {
			intArray[i] = byteArray[i] & 0xff;
		}
		return intArray;
	}


	public static int byteArrayToInt(byte[] b)
	{
		return  ( b[3] & 0xFF |
				(b[2] & 0xFF) << 8 |
				(b[1] & 0xFF) << 16 |
				(b[0] & 0xFF) << 24);
	}

	public static int bytesToInt16(byte high, byte low)
	{
		return  ((low & 0xFF) | ((high & 0xFF) << 8));
	}

	public static int bytesToInt32(byte hwhb, byte hwlb, byte lwhb, byte lwlb)
	{
		return  ((lwlb & 0xFF) | ((lwhb & 0xFF) << 8) | ((hwlb & 0xFF) << 16) | ((hwhb & 0xFF) << 24));
	}

	public static int readByte(InputStream istream) throws IOException {
		int retVal = istream.read();
		if (retVal == -1) {
			throw new RuntimeException("End of stream reached.");
		}
		return retVal;
	}

	// Reads a 16 bit (in network order)
	public static int readInt16(InputStream istream) throws IOException {
		int high = readByte(istream);
		int low = readByte(istream);
		return low | (high << 8);
	}

	// Reads a 32 bit (in network order)
	public static int readInt32(InputStream istream) throws IOException {
		int hwhb = readByte(istream);
		int hwlb = readByte(istream);
		int lwhb = readByte(istream);
		int lwlb = readByte(istream);
		return lwlb | (lwhb << 8) | (hwlb << 16) | (hwhb << 24);
	}


	public static byte[] zipInflate(final byte[] compressed) throws IOException {
		ByteArrayInputStream byteIn = new ByteArrayInputStream(compressed);
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream(); InflaterInputStream inflaterIn = new InflaterInputStream(byteIn)) {
			int read;
			byte[] buffer = new byte[512];
			do {
				read = inflaterIn.read(buffer);
				if (read > 0) {
					byteOut.write(buffer, 0, read);
				}
			} while (read >= 0);
			return byteOut.toByteArray();
		}
	}

}
