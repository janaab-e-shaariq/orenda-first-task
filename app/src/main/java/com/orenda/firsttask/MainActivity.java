package com.orenda.firsttask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    
    private static final String TAG = "MainActivity";
    private Button buttonDownload;
    
    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder builder;
    private int PROGRESS_MAX = 100;
    
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        buttonDownload = findViewById(R.id.button);
        buttonDownload.setOnClickListener(this);
        
        isStoragePermissionGranted();
    }
    
    /**
     * This method checks if the storage permission is granted or not and then asks user to allow storage permission.
     */
    public void isStoragePermissionGranted () {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "Permission is granted");
            }
            else {
                buttonDownload.setEnabled(false);
                Log.v(TAG, "Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
        else {
            Log.v(TAG, "Permission is granted");
        }
    }
    
    @Override
    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Permission: " + permissions[0] + "was " + grantResults[0]);
            buttonDownload.setEnabled(true);
        }
        else {
            Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show();
            MainActivity.this.finishAffinity();
        }
    }
    
    @Override
    public void onClick (View view) {
        if (view.getId() == buttonDownload.getId()) {
            buttonDownload.setEnabled(false);

//            downloadVideoFromLink("https://sample-videos.com/video123/mp4/720/big_buck_bunny_720p_10mb.mp4");
//            beginDownload("https://sample-videos.com/video123/mp4/720/big_buck_bunny_720p_5mb.mp4");
            startDownloadService();
        }
    }
    
    private void startDownloadService () {
        Log.wtf(TAG, "startDownloadService");
        Intent serviceIntent = new Intent(this, DownloadForegroundService.class);
        serviceIntent.putExtra("fileURL", "https://sample-videos.com/video123/mp4/720/big_buck_bunny_720p_5mb.mp4");
    
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    
    private long downloadID;
    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive (Context context, Intent intent) {
            //Fetching the download id received with the broadcast
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //Checking if the received broadcast is for our enqueued download by matching download id
            if (downloadID == id) {
                Toast.makeText(MainActivity.this, "Download Completed.", Toast.LENGTH_SHORT).show();
            }
        }
    };
    
    @Override
    public void onDestroy () {
        super.onDestroy();
        // using broadcast method
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onDownloadComplete);
    }
    
    private void beginDownload (String url) {
        
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        fileName = fileName.substring(0, 1).toUpperCase() + fileName.substring(1);
        File file = new File(getApplicationContext().getFilesDir(), fileName);
        
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                                                  .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)// Visibility of the download
                                                  // Notification
                                                  .setDestinationUri(Uri.fromFile(file))// Uri of the destination file
                                                  .setTitle(fileName)// Title of the Download Notification
                                                  .setDescription("Downloading " + fileName)// Description of the Download Notification
                                                  .setAllowedOverMetered(true)// Set if download is allowed on Mobile network
                                                  .setAllowedOverRoaming(true);// Set if download is allowed on roaming network
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            request.setRequiresCharging(false); // Set if charging is required to begin the download
        }
        
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadID = downloadManager.enqueue(request);// enqueue puts the download request in the queue.
        
        // using query method
        boolean finishDownload = false;
        int progress;
        while (!finishDownload) {
            Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadID));
            Log.wtf("MainActivity", "not finishDownload");
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                Log.wtf("MainActivity", "cursor.moveToFirst() " + status);
                switch (status) {
                    case DownloadManager.STATUS_FAILED: {
                        LocalBroadcastManager.getInstance(this).unregisterReceiver(onDownloadComplete);
                        finishDownload = true;
                        break;
                    }
                    case DownloadManager.STATUS_PAUSED:
                        break;
                    case DownloadManager.STATUS_PENDING:
                        break;
                    case DownloadManager.STATUS_RUNNING: {
                        final long total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        if (total >= 0) {
                            final long downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                            progress = (int) ((downloaded * 100L) / total);
                            // if you use downloadmanger in async task, here you can use like this to display progress.
                            // Don't forget to do the division in long to get more digits rather than double.
                            //  publishProgress((int) ((downloaded * 100L) / total));
                        }
                        break;
                    }
                    case DownloadManager.STATUS_SUCCESSFUL: {
                        LocalBroadcastManager.getInstance(this).unregisterReceiver(onDownloadComplete);
                        progress = 100;
                        // if you use aysnc task
                        // publishProgress(100);
                        finishDownload = true;
                        Toast.makeText(MainActivity.this, "Download Completed!", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * This method takes a video link as a String of the form 'https://xxx.xx/xxxx.mp4' and then starts download.
     *
     * @param link is used to pass video link to the method
     */
    private void downloadVideoFromLink (String link) {

//        copyLinkToClipboard(link);
        
        Toast.makeText(this, "Download Started!\nLink copied to clipboard.", Toast.LENGTH_SHORT).show();
        
        
        Retrofit retrofit = new Retrofit.Builder()
                                    .baseUrl("https://sample-videos.com")
                                    .build();
        
        String fileName = link.substring(link.lastIndexOf('/') + 1);
        
        buildNotification(fileName);
        
        VideoDownloadApi downloadApi = retrofit.create(VideoDownloadApi.class);
        
        Call<ResponseBody> downloadCall = downloadApi.downloadAsync("video123/mp4/720/big_buck_bunny_720p_10mb.mp4");
        
        downloadCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse (Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    
                    new AsyncTask<Void, String, Void>() {
                        
                        @Override
                        protected Void doInBackground (Void... voids) {
                            try {
                                File futureStudioIconFile =
                                        new File(getExternalFilesDir(null) + File.separator + fileName);
                                
                                InputStream inputStream = null;
                                OutputStream outputStream = null;
                                
                                try {
                                    byte[] fileReader = new byte[4096];
                                    
                                    long fileSize = response.body().contentLength();
                                    long fileSizeDownloaded = 0;
                                    
                                    inputStream = response.body().byteStream();
                                    outputStream = new FileOutputStream(futureStudioIconFile);
                                    
                                    while (true) {
                                        int read = inputStream.read(fileReader);
                                        
                                        if (read == -1) {
                                            break;
                                        }
                                        
                                        outputStream.write(fileReader, 0, read);
                                        
                                        fileSizeDownloaded += read;
                                        
                                        publishProgress("" + fileSize, "" + fileSizeDownloaded);
                                        
                                        Log.d(TAG, "file download: " + fileSizeDownloaded + " of " + fileSize);
                                    }
                                    
                                    outputStream.flush();

//                                        Toast.makeText(MainActivity.this, "Done!", Toast.LENGTH_SHORT).show();

//                            return true;
                                } catch (IOException e) {
                                    Log.e(TAG, e.getMessage());
//                            return false;
                                } finally {
                                    if (inputStream != null) {
                                        inputStream.close();
                                    }
                                    
                                    if (outputStream != null) {
                                        outputStream.close();
                                    }
                                }
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
//                        return false;
                            }
                            return null;
                        }
                        
                        @Override
                        protected void onProgressUpdate (String... values) {
                            int fileSize = Integer.parseInt(values[0]);
                            int fileSizeDownloaded = Integer.parseInt(values[1]);
                            
                            float prog = fileSizeDownloaded / (float) fileSize;
                            int percent = (int) (prog * 100);
                            
                            updateNotificationProgress(percent);
                        }
                        
                        @Override
                        protected void onPostExecute (Void aVoid) {
                            Toast.makeText(MainActivity.this, "Video Downloaded.\nSaved as \"" + fileName + "\"", Toast.LENGTH_LONG).show();
                            
                            buttonDownload.setEnabled(true);
                            
                            notificationFinalStage(fileName);
                        }
                    }.execute();
                }
                else {
                    Toast.makeText(MainActivity.this, "Connection failed.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Server connection failed: " + response.message());
                }
            }
            
            @Override
            public void onFailure (Call<ResponseBody> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Something went wrong.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, t.getMessage());
            }
        });
    }
    
    /**
     * This method checks if the given String equals 'https:', 'http:' or not.
     *
     * @param protocol is used to pass the string to method.
     * @return returns passed String if condition is met, otherwise returns null
     */
    private String protocolGiven (String protocol) {
        if (protocol.equalsIgnoreCase("https:")
                    || protocol.equalsIgnoreCase("http:")) {
            return protocol;
        }
        else {
            return null;
        }
    }
    
    /**
     * This method copies the passed String link to clipboard.
     *
     * @param link is used to pass String link to the method
     */
    private void copyLinkToClipboard (String link) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("link", link);
        clipboard.setPrimaryClip(clip);
    }
    
    /**
     * This method makes a notification and adds the videoName passed through argument to the notification.
     *
     * @param videoName is used to pass the String video name to the method
     */
    private void buildNotification (String videoName) {
        notificationManager = NotificationManagerCompat.from(this);
        builder = new NotificationCompat.Builder(this, "ORENDA");
        builder.setContentTitle(videoName)
                .setContentText("Download in progress")
                .setSmallIcon(R.drawable.ic_baseline_cloud_download_24)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        
        int PROGRESS_CURRENT = 0;
        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
        notificationManager.notify(101, builder.build());
    }
    
    /**
     * This method updates the download progress in the notification.
     *
     * @param progress is used to pass the download progress
     */
    private void updateNotificationProgress (int progress) {
        builder.setProgress(PROGRESS_MAX, progress, false);
        notificationManager.notify(101, builder.build());
    }
    
    /**
     * This method updates the notification when download is complete, and prompts user that the download is finished.
     *
     * @param videoName is used to pass the download progress
     */
    private void notificationFinalStage (String videoName) {
        builder.setContentText("Video " + videoName + " downloaded.")
                .setProgress(0, 0, false);
        notificationManager.notify(101, builder.build());
    }
}