package com.orenda.firsttask;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface VideoDownloadApi {
    
    @Streaming
    @GET
    Call<ResponseBody> downloadAsync(@Url String videoUrl);
    
}
