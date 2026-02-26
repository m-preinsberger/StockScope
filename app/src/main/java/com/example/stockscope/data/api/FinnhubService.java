package com.example.stockscope.data.api;

import com.example.stockscope.data.api.model.FinnhubQuote;
import com.example.stockscope.data.api.model.FinnhubSearchResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface FinnhubService {

    @GET("quote")
    Call<FinnhubQuote> quote(@Query("symbol") String symbol);

    @GET("search")
    Call<FinnhubSearchResponse> search(@Query("q") String query);
}
