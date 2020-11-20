package com.orenda.firsttask;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadForegroundService extends Service {
    
    public static final String CHANNEL_ID = "DownloadForegroundService";
    public static final String CHANNEL_ID2 = "DownloadForegroundService2";
    public static final String TAG = "DownloadForegroundService";
    public static final int PROGRESS_MAX = 100;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder builder;
    private PendingIntent pendingIntent;
    private String fileName;
    private File mediaFile;
    private int sentProgress;
    
    @Override
    public void onCreate () {
        super.onCreate();
        Log.wtf(TAG, "onCreate");
    }
    
    @Override
    public void onDestroy () {
        super.onDestroy();
        Log.wtf(TAG, "onDestroy");
    }
    
    @Nullable
    @Override
    public IBinder onBind (Intent intent) {
        Log.wtf(TAG, "onBind");
        return null;
    }
    
    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        Log.wtf(TAG, "service started");
        
        String fileURL = intent.getStringExtra("fileURL");
        createNotificationChannel();
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        
        fileName = fileURL.substring(fileURL.lastIndexOf('/') + 1);
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID2)
                                            .setContentTitle("Download Started!")
                                            .setSmallIcon(R.drawable.ic_baseline_cloud_download_24)
                                            .setContentIntent(pendingIntent)
                                            .build();
        
        startForeground(1, notification);
        
        buildNotification(fileName);
        
        new FileDownloader().execute(fileURL);
        
        return START_NOT_STICKY;
    }
    
    private void createNotificationChannel () {
        Log.wtf(TAG, "createNotificationChannel");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Download Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(serviceChannel);
        }
        else {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
    }
    
    /**
     * This method makes a notification and adds the videoName passed through argument to the notification.
     *
     * @param videoName is used to pass the String video name to the method
     */
    private void buildNotification (String videoName) {
        Log.wtf(TAG, "buildNotification");
        builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setContentTitle(videoName)
                .setSmallIcon(R.drawable.ic_baseline_cloud_download_24)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setNotificationSilent()
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        
        int PROGRESS_CURRENT = 0;
        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
        notificationManager.notify(101, builder.build());
    }
    
    /**
     * This method updates the download progress in the notification.
     *
     * @param progress is used to pass the download progress
     */
    private void updateNotificationProgress (double progress, String fileName) {
        Log.wtf(TAG, "updateNotificationProgress: " + progress);
        if ((int) progress > sentProgress && ((int) progress) % 5 == 0) {
            sentProgress = (int) progress;
            builder.setContentTitle(fileName + ": " + sentProgress + "%")
                    .setProgress(PROGRESS_MAX, sentProgress, false);
            notificationManager.notify(101, builder.build());
        }
    }
    
    /**
     * This method updates the notification when download is complete, and prompts user that the download is finished.
     *
     * @param videoName is used to pass the download progress
     */
    private void notificationFinalStage (String videoName, boolean successful) {
        Log.wtf(TAG, "notificationFinalStage: " + videoName + " " + successful);
        if (successful) {
            Log.wtf(TAG, "successfully downloaded");
            builder.setContentTitle((videoName + " downloaded."))
                    .setProgress(0, 0, false)
            ;
        }
        else {
            Log.wtf(TAG, "not downloaded");
            builder.setContentTitle("Failed " + videoName + " download.")
                    .setProgress(0, 0, false)
            ;
        }
        notificationManager.notify(101, builder.build());
    
        DownloadForegroundService.this.stopSelf();
    }
    
    private class FileDownloader extends AsyncTask<String, Long, Boolean> {
        
        @Override
        protected Boolean doInBackground (String... strings) {
            Log.wtf(TAG, "doInBackground");
            
            sentProgress = 0;
            
            OkHttpClient client = new OkHttpClient();
            String url = strings[0];
            Call call = client.newCall(new Request.Builder().url(url).get().build());
            
            try {
                Response response = call.execute();
                if (response.code() == 200 || response.code() == 201) {
                    
                    Headers responseHeaders = response.headers();
                    for (int i = 0; i < responseHeaders.size(); i++) {
                        Log.d("LOG_TAG", responseHeaders.name(i) + ": " + responseHeaders.value(i));
                    }
                    
                    InputStream inputStream = null;
                    try {
                        inputStream = response.body().byteStream();
                        
                        byte[] buff = new byte[1024 * 4];
                        long downloaded = 0;
                        long target = response.body().contentLength();
                        mediaFile = new File(getFilesDir(), fileName);
                        OutputStream output = new FileOutputStream(mediaFile);
                        
                        publishProgress(0L, target);
                        while (true) {
                            int readed = inputStream.read(buff);
                            
                            if (readed == -1) {
                                break;
                            }
                            output.write(buff, 0, readed);
                            //write buff
                            downloaded += readed;
                            publishProgress(downloaded, target);
                            if (isCancelled()) {
                                return false;
                            }
                        }
                        
                        output.flush();
                        output.close();
                        
                        return downloaded == target;
                    } catch (IOException ignore) {
                        return false;
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    }
                }
                else {
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate (Long... values) {
            super.onProgressUpdate(values);
            Log.wtf(TAG, "onProgressUpdate: " + values[0] + " " + values[1]);
            
            long fileSizeDownloaded = values[0];
            long fileSize = values[1];
            
            double prog = fileSizeDownloaded / (double) fileSize;
            double percent = prog * 100;
            
            updateNotificationProgress(percent, fileName);
        }
        
        @Override
        protected void onPostExecute (Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            Log.wtf(TAG, "onPostExecute: " + aBoolean);
            notificationFinalStage(fileName, aBoolean.booleanValue());
        }
        
    }
    
}
