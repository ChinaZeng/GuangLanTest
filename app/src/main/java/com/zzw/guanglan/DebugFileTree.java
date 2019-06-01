package com.zzw.guanglan;

import android.os.Environment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import timber.log.Timber;

/**
 * Created by zzw on 2019-05-30
 * Des:
 */
public class DebugFileTree extends Timber.Tree {
    @Override
    protected void log(int priority, @Nullable String tag, @NotNull String message, @Nullable Throwable t) {
        if (tag != null && tag.equals("SocketService")) {
            writeTxtFile(message, new File(Environment.getExternalStorageDirectory() + File.separator + "logService.txt"));
        }
    }

    /**
     * 60    * @description 写文件
     * 61    * @param args
     * 62    * @throws UnsupportedEncodingException
     * 63    * @throws IOException
     * 64
     */
    public static boolean writeTxtFile(String content, File file) {
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream o = new FileOutputStream(file, true);
            o.write(content.getBytes("UTF-8"));
            o.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

}
