package com.ryanmitchener.batchdownload;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Copyright (c) 2014 Ryan Mitchener
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * NOTES
 * -------------------------------------------------------------------------------------------------
 * + Requires Internet permission to use <uses-permission android:name="android.permission.INTERNET" />
 * + When creating a BroadcastReceiver, remember to unregister on onDestroy() so you do not get duplicate onReceive() calls
 */


// Class for downloading files concurrently on multiple threads
public class BatchDownload {
    // Misc variables
    private long total_downloaded = 0;
    private long total_bytes = 0;
    private int error_count = 0;
    private static LocalBroadcastManager bm = null;
    private static final ProgressThread progressThread = new ProgressThread();

    // File/folder variables
    private static String CACHE_PATH;
    private static String DEFAULT_PATH;
    private static File cache;

    // Thread pool/work queue variables
    private final BlockingQueue<Runnable> downloadWorkQueue;
    private final BlockingQueue<Runnable> sizeWorkQueue;
    private DownloaderThreadPoolExecutor downloadThreadPool;
    private SizeCalculatorThreadPoolExecutor sizeThreadPool;
    private final Object total_downloaded_lock = new Object();
    private final Object total_bytes_lock = new Object();
    private final Object error_count_lock = new Object();
    private final int CORE_POOL_SIZE = 4;
    private final int MAX_POOL_SIZE = 5;
    private final int KEEP_ALIVE_TIME = 10;
    private final int PROGRESS_INTERVAL = 32;
    private boolean sizeCalculated = false;

    // Broadcast Intent Actions
    public final static String ACTION_CALCULATING = "com.ryanmitchener.batchdownload.ACTION_CALCULATING";
    public final static String ACTION_PROGRESS = "com.ryanmitchener.batchdownload.ACTION_PROGRESS";
    public final static String ACTION_ERROR = "com.ryanmitchener.batchdownload.ACTION_ERROR";
    public final static String ACTION_FILE_DOWNLOADED = "com.ryanmitchener.batchdownload.ACTION_FILE_DOWNLOADED";
    public final static String ACTION_COMPLETE = "com.ryanmitchener.batchdownload.ACTION_COMPLETE";
    public final static String ACTION_CANCELLED = "com.ryanmitchener.batchdownload.ACTION_CANCELLED";

    // Broadcast Intent extras
    public final static String EXTRA_BYTES_DOWNLOADED = "com.ryanmitchener.batchdownload.EXTRA_TOTAL_DOWNLOADED";
    public final static String EXTRA_TOTAL_BYTES = "com.ryanmitchener.batchdownload.EXTRA_TOTAL_BYTES";
    public final static String EXTRA_ERROR_COUNT = "com.ryanmitchener.batchdownload.EXTRA_ERROR_COUNT";
    public final static String EXTRA_FILES_REMAINING = "com.ryanmitchener.batchdownload.FILES_REMAINING";
    public final static String EXTRA_FILENAME = "com.ryanmitchener.batchdownload.EXTRA_FILENAME";  // Only available in ACTION_FILE_DOWNLOADED
    public final static String EXTRA_FILEPATH = "com.ryanmitchener.batchdownload.EXTRA_FILEPATH";  // Only available in ACTION_FILE_DOWNLOADED
    public final static String EXTRA_ERROR_URL = "com.ryanmitchener.batchdownload.EXTRA_ERROR_URL";  // Only available in ACTION_ERROR

    // Create Singleton pattern
    private final static BatchDownload sInstance = new BatchDownload();


    /**
     * Constructor
     * ---------------------------------------------------------------------------------------------
     */

    // Constructor width list of DownloadRequests
    private BatchDownload() {
        this.downloadWorkQueue = new LinkedBlockingQueue<Runnable>();
        this.downloadThreadPool = new DownloaderThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, downloadWorkQueue);
        this.sizeWorkQueue = new LinkedBlockingQueue<Runnable>();
        this.sizeThreadPool = new SizeCalculatorThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, sizeWorkQueue);
    }


    // Singleton pattern instance
    public static BatchDownload getInstance(Context context) {
        // If BatchDownload hasn't been initialized, initialize it.
        if (DEFAULT_PATH == null) {
            bm = LocalBroadcastManager.getInstance(context);

            // Create the cache directory
            DEFAULT_PATH = context.getFilesDir().getAbsolutePath() + "/";
            CACHE_PATH = DEFAULT_PATH + "com.ryanmitchener.batchdownload.cache/";
            cache = new File(CACHE_PATH);

            // Start the progress thread
            progressThread.start();

            if (!cache.exists()) {
                cache.mkdir();
            }
        }

        // Return singleton
        return sInstance;
    }


    /**
     * Misc Methods
     * ---------------------------------------------------------------------------------------------
     */

    // Add a request
    public void add(Request request) {
        // Send calculating size broadcast
        if (!isRunning()) {
            sendBroadcast(ACTION_CALCULATING, null);
            progressThread.mHandler.post(new ProgressUpdateTask());
        }

        // Add to thread pool
        downloadThreadPool.execute(new DownloaderTask(request));
        sizeThreadPool.execute(new SizeCalculateTask(request));
    }


    // Add a list of requests
    public void add(ArrayList<Request> requests) {
        // Send calculating size broadcast
        if (!isRunning()) {
            sendBroadcast(ACTION_CALCULATING, null);
            progressThread.mHandler.post(new ProgressUpdateTask());
        }

        // Populate thread pool
        for (Request request : requests) {
            downloadThreadPool.execute(new DownloaderTask(request));
            sizeThreadPool.execute(new SizeCalculateTask(request));
        }
    }


    // Cancel Method
    public void cancel() {
        downloadThreadPool.shutdownNow();
        sizeThreadPool.shutdownNow();
    }


    // Checks if the downloader is running or not
    public boolean isRunning() {
        return downloadThreadPool.getActiveCount() > 0;
    }


    // Reset variables
    private void resetVars() {
        sizeCalculated = false;
        synchronized (error_count_lock) {
            error_count = 0;
        }
        synchronized (total_downloaded_lock) {
            total_downloaded = 0;
        }
        synchronized (total_bytes_lock) {
            total_bytes = 0;
        }

        // Remove the cache folder
        File[] cacheFiles = cache.listFiles();
        for (File file : cacheFiles) {
            file.delete();
        }
    }


    // Sends broadcast
    private void sendBroadcast(String type, Bundle extras) {
        if (extras == null) {
            extras = new Bundle();
        }
        synchronized (total_downloaded_lock) {
            extras.putLong(EXTRA_BYTES_DOWNLOADED, total_downloaded);
        }
        synchronized (total_bytes_lock) {
            extras.putLong(EXTRA_TOTAL_BYTES, total_bytes);
        }
        synchronized (error_count_lock) {
            extras.putInt(EXTRA_ERROR_COUNT, error_count);
        }
        if (type.equals(ACTION_COMPLETE)) {
            extras.putInt(EXTRA_FILES_REMAINING, 0);
        } else {
            extras.putInt(EXTRA_FILES_REMAINING, downloadWorkQueue.size() + downloadThreadPool.getActiveCount());
        }
        Intent intent = new Intent(type);
        intent.putExtras(extras);
        bm.sendBroadcast(intent);
    }


    /**
     * Progress Checker
     * ---------------------------------------------------------------------------------------------
     */

    // Runs at an interval and sends progress broadcasts
    private class ProgressUpdateTask implements Runnable {
        @Override
        public void run() {
            if (isRunning()) {
                if (sizeCalculated) {
                    sendBroadcast(ACTION_PROGRESS, null);
                }
                progressThread.mHandler.postDelayed(this, PROGRESS_INTERVAL);
            }
        }
    }


    /**
     * Downloader
     * ---------------------------------------------------------------------------------------------
     */

    // Download task for downloading urls
    private class DownloaderTask implements Runnable {
        private Request request;

        DownloaderTask(Request request) {
            this.request = request;
        }

        @Override
        public void run() {
            try {
                // Connect to the URL with a GET request this time
                HttpURLConnection con = (HttpURLConnection) new URL(request.url).openConnection();
                if (con.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    con.disconnect();
                    throw new BadHTTPResponseException();
                }

                // Get the filename
                String filename = Uri.decode(request.url);
                filename = filename.substring(filename.lastIndexOf("/") + 1);
                if (request.filename != null) {
                    String extension = filename.substring(filename.lastIndexOf("."));
                    filename = request.filename + extension;
                }

                // Create directory and file
                InputStream is = new BufferedInputStream(con.getInputStream());
                File file = new File(cache, filename);

                // Write data to file
                OutputStream os = new FileOutputStream(file);
                byte[] buffer = (is.available() > 4000) ? new byte[is.available()] : new byte[4000];
                Integer bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    // Cancel the runnable if the thread is interrupted by shutdownNow()
                    if (Thread.currentThread().isInterrupted()) {
                        is.close();
                        os.close();
                        con.disconnect();
                        return;
                    }

                    synchronized (total_downloaded_lock) {
                        total_downloaded += bytesRead;
                    }
                    os.write(buffer, 0, bytesRead);
                }

                // Close streams
                is.close();
                os.close();
                con.disconnect();

                // Move file to specified location
                File movedFile;
                if (request.downloadFolder != null) {
                    if (!request.downloadFolder.isDirectory()) {
                        throw new NotADirectoryException();
                    }
                    movedFile = new File(request.downloadFolder, filename);
                } else {
                    movedFile = new File(DEFAULT_PATH, filename);
                }

                // Attempt to move the file
                if (!file.renameTo(movedFile)) {
                    throw new FailedFileWriteException();
                }

                // Send FILE_DOWNLOADED broadcast
                Bundle bundle = new Bundle();
                bundle.putString(EXTRA_FILENAME, filename);
                bundle.putString(EXTRA_FILEPATH, movedFile.getAbsolutePath());
                sendBroadcast(ACTION_FILE_DOWNLOADED, bundle);
            } catch (Exception e) {
                synchronized (error_count_lock) {
                    error_count++;
                }
                Bundle bundle = new Bundle();
                bundle.putString(EXTRA_ERROR_URL, request.url);
                sendBroadcast(ACTION_ERROR, bundle);
                e.printStackTrace();
            }
        }
    }


    // Extension of ThreadPoolExecutor for download tasks
    private class DownloaderThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {
        public DownloaderThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);

            // If current thread is the last in the pool
            if (downloadThreadPool.getActiveCount() == 1) {
                resetVars();
                sendBroadcast(ACTION_COMPLETE, null);
            }
        }

        @Override
        protected void terminated() {
            super.terminated();
            downloadThreadPool = new DownloaderThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, downloadWorkQueue);
            resetVars();
            sendBroadcast(ACTION_CANCELLED, null);
        }
    }


    /**
     * Size Calculator
     * ---------------------------------------------------------------------------------------------
     */

    // Runnable for calculating the total size of all requests
    private class SizeCalculateTask implements Runnable {
        private String url;

        SizeCalculateTask(Request request) {
            this.url = request.url;
        }

        @Override
        public void run() {
            try {
                // Cancel the runnable if the thread is interrupted by shutdownNow()
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("HEAD");
                if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    synchronized (total_bytes_lock) {
                        total_bytes += con.getContentLength();
                    }
                }
                con.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // Extension of ThreadPoolExecutor for size request tasks
    private class SizeCalculatorThreadPoolExecutor extends ThreadPoolExecutor {
        public SizeCalculatorThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            super.afterExecute(r, t);

            // If current thread is the last in the pool
            if (sizeThreadPool.getActiveCount() == 1) {
                sizeCalculated = true;
            }
        }

        @Override
        protected void terminated() {
            super.terminated();
            sizeThreadPool = new SizeCalculatorThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, sizeWorkQueue);
            resetVars();
        }
    }


    /**
     * Container Classes
     * ---------------------------------------------------------------------------------------------
     */

    // Container class for requests
    public static class Request {
        private String url = null;
        private String filename = null;
        private File downloadFolder = null;

        public Request(String url) {
            this.url = url;
        }

        public Request(String url, String filename) {
            this.url = url;
            this.filename = filename;
        }

        public Request(String url, String filename, File downloadFolder) {
            this.url = url;
            this.filename = filename;
            this.downloadFolder = downloadFolder;
        }
    }


    // Thread that runs the progress looper
    private static class ProgressThread extends Thread {
        private Handler mHandler;

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler();
            Looper.loop();
        }
    }


    /**
     * Exception Classes
     * ---------------------------------------------------------------------------------------------
     */

    // Exception for the specified download folder not being a directory
    private class NotADirectoryException extends Exception {
        @Override
        public String getMessage() {
            return "The download folder specified is not a directory.";
        }
    }


    // Exception for the specified download folder not being a directory
    private class BadHTTPResponseException extends Exception {
        @Override
        public String getMessage() {
            return "The URL has replied with a bad response.";
        }
    }


    // Exception for not being able to move download to download folder
    private class FailedFileWriteException extends Exception {
        @Override
        public String getMessage() {
            return "The download failed to be written to the requested folder.";
        }
    }
}