package com.example.stockscope.data.api;

import com.example.stockscope.data.api.model.GeminiRequest;
import com.example.stockscope.data.api.model.GeminiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface GeminiService {

    @POST("v1beta/models/{model}:generateContent")
    Call<GeminiResponse> generateContent(@Path("model") String model, @Body GeminiRequest body);
}
