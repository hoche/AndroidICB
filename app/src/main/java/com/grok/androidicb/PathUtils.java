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
        String filename = filepart + "_" + fileName;
        return filename;
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
        String filename = filesDirPath + "/" + filepart + "_" + fileName;
        return filename;
    }

    public static String createPath(String fileType,String suffix, Context ctx) {
        long dateTaken = System.currentTimeMillis();
        File filesDir = getStorageDir(ctx);
        String filesDirPath = filesDir.getAbsolutePath();
        filesDir.mkdirs();
        SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_STRING);
        Date date = new Date(dateTaken);
        String filepart = dateFormat.format(date);
        String filename = filesDirPath + "/" + filepart + "_" + fileType + suffix;
        return filename;
    }

    public static File getStorageDir(Context ctx) {

        String filesDirPath = Environment.getExternalStorageDirectory().toString() +
                "/" + ctx.getResources().getString(R.string.app_name);

        File ret = ctx.getExternalFilesDir(null);
        if(!ret.exists()) {
            ret.mkdirs();
        }
        return ret;
    }

}
