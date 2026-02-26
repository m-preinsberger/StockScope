package com.example.stockscope.data.api;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface AlphaVantageService {

    @GET("query")
    Call<JsonObject> dailyAdjusted(
            @Query("function") String function,
            @Query("symbol") String symbol,
            @Query("outputsize") String outputSize
    );
}
