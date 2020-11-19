package com.orenda.firsttask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
    public void isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
            } else {
                buttonDownload.setEnabled(false);
                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
        else {
            Log.v(TAG,"Permission is granted");
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
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
            
            downloadVideoFromLink("https://sample-videos.com/video123/mp4/720/big_buck_bunny_720p_10mb.mp4");
        }
    }
    
    /**
     * This method takes a video link as a String of the form 'https://xxx.xx/xxxx.mp4' and then starts download.
     * @param link is used to pass video link to the method
     */
    private void downloadVideoFromLink (String link) {
        
        copyLinkToClipboard(link);
    
        Toast.makeText(this, "Download Started!\nLink copied to clipboard.", Toast.LENGTH_SHORT).show();
    
        
        String[] linkProtocolBreakdown = link.split("[/][/]");
        String tempLink = null;
        if (protocolGiven(linkProtocolBreakdown[0]) != null) {
            tempLink = link;
        }
        else {
            tempLink = "https://" + link;
        }
        
        String[] linkBreakdown = tempLink.split("[/]");
        
        buildNotification(linkBreakdown[linkBreakdown.length-1]);
        
        String linkPath = "";
    
        for (int i = 2; i < linkBreakdown.length; i++) {
            linkPath += linkBreakdown[i] + "/";
        }
    
        Retrofit retrofit = new Retrofit.Builder()
                                    .baseUrl(linkBreakdown[0] + "//" + linkBreakdown[1])
                                    .build();
    
        VideoDownloadApi downloadApi = retrofit.create(VideoDownloadApi.class);
    
        Call<ResponseBody> downloadCall = downloadApi.downloadAsync(linkPath);
    
        downloadCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse (Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                
                    new AsyncTask<Void, String, Void>() {
                    
                        @Override
                        protected Void doInBackground (Void... voids) {
                            try {
                                File futureStudioIconFile =
                                        new File(getFilesDir() + File.separator + linkBreakdown[linkBreakdown.length-1]);
                            
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
                                    
                                        publishProgress(""+fileSize, ""+fileSizeDownloaded);
                                    
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
                            Toast.makeText(MainActivity.this, "Video Downloaded.\nSaved as \"Downloaded Video.mp4\"", Toast.LENGTH_LONG).show();
                        
                            buttonDownload.setEnabled(true);
                        
                            notificationFinalStage(linkBreakdown[linkBreakdown.length-1]);
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
     * @param protocol is used to pass the string to method.
     * @return returns passed String if condition is met, otherwise returns null
     */
    private String protocolGiven (String protocol) {
        if (protocol.equalsIgnoreCase("https:")
                || protocol.equalsIgnoreCase("http:")) {
            return protocol;
        }
        else
            return null;
    }
    
    /**
     * This method copies the passed String link to clipboard.
     * @param link is used to pass String link to the method
     */
    private void copyLinkToClipboard (String link) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("link", link);
        clipboard.setPrimaryClip(clip);
    }
    
    /**
     * This method makes a notification and adds the videoName passed through argument to the notification.
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
     * @param progress  is used to pass the download progress
     */
    private void updateNotificationProgress(int progress) {
        builder.setProgress(PROGRESS_MAX, progress, false);
        notificationManager.notify(101, builder.build());
    }
    
    /**
     * This method updates the notification when download is complete, and prompts user that the download is finished.
     * @param videoName is used to pass the download progress
     */
    private void notificationFinalStage(String videoName) {
        builder.setContentText("Video " + videoName + " downloaded.")
                .setProgress(0,0,false);
        notificationManager.notify(101, builder.build());
    }
}