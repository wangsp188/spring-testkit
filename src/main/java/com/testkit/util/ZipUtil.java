package com.testkit.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ZipUtil {



    /**
     * 验证文件是否为有效的 tar.gz 压缩包
     */
    public static boolean isValidTarGzFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        // 检查文件大小，tar.gz 文件至少应该有一些字节
        if (file.length() < 100) {
            return false;
        }

        GZIPInputStream gzipInputStream = null;
        try {
            // 尝试打开 gzip 文件，如果能成功打开并读取数据，则认为是有效的
            gzipInputStream = new GZIPInputStream(new FileInputStream(file));
            byte[] buffer = new byte[1024];
            int read = gzipInputStream.read(buffer);
            // 能读取到数据说明是有效的 gzip 文件
            return read > 0;
        } catch (IOException e) {
            // 格式错误或读取失败
            System.err.println("Invalid tar.gz file format: " + file.getAbsolutePath() + ", error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // 其他异常
            System.err.println("Error validating tar.gz file: " + file.getAbsolutePath() + ", error: " + e.getMessage());
            return false;
        } finally {
            if (gzipInputStream != null) {
                try {
                    gzipInputStream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }


    /**
     * 验证文件是否为有效的 zip 压缩包
     */
    public static boolean isValidZipFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        // 检查文件大小，zip 文件至少应该有几百字节
        if (file.length() < 100) {
            return false;
        }

        ZipFile zipFile = null;
        try {
            // 尝试打开 zip 文件，如果能成功打开且至少包含一个条目，则认为是有效的
            zipFile = new ZipFile(file);
            int entries = zipFile.size();
            return entries > 0; // 至少应该包含一个文件
        } catch (ZipException e) {
            // zip 格式错误
            System.err.println("Invalid zip file format: " + file.getAbsolutePath() + ", error: " + e.getMessage());
            return false;
        } catch (Exception e) {
            // 其他异常
            System.err.println("Error validating zip file: " + file.getAbsolutePath() + ", error: " + e.getMessage());
            return false;
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
