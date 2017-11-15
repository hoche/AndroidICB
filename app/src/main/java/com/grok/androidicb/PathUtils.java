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

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PathUtils {
    public static final String DATE_STRING="yyyy-MM-dd-HH_mm_ss";

    public static String createPath(String fileName)
    {
        long dateTaken = System.currentTimeMillis();
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_STRING);
        Date date = new Date(dateTaken);
        String filepart = dateFormat.format(date);
        return filepart + "_" + fileName;
    }
    public static String createPath(String fileName, Context ctx)
    {
        long dateTaken = System.currentTimeMillis();
        File filesDir = getStorageDir(ctx);
        String filesDirPath = filesDir.getAbsolutePath();
        filesDir.mkdirs();
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_STRING);
        Date date = new Date(dateTaken);
        String filepart = dateFormat.format(date);
        return filesDirPath + "/" + filepart + "_" + fileName;
    }

    public static String createPath(String fileType,String suffix, Context ctx) {
        long dateTaken = System.currentTimeMillis();
        File filesDir = getStorageDir(ctx);
        String filesDirPath = filesDir.getAbsolutePath();
        filesDir.mkdirs();
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_STRING);
        Date date = new Date(dateTaken);
        String filepart = dateFormat.format(date);
        return filesDirPath + "/" + filepart + "_" + fileType + suffix;
    }

    public static File getStorageDir(Context ctx) {

        String filesDirPath = Environment.getExternalStorageDirectory().toString() +
                "/" + ctx.getResources().getString(R.string.app_name);

        File ret = ctx.getExternalFilesDir(null); // this can be null
        if (ret == null) {
            ret = ctx.getFilesDir(); // never null (supposedly)
        }
        if (!ret.exists()) {
          ret.mkdirs();
        }
        return ret;
    }

}
