package com.example.stockscope.data.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.example.stockscope.BuildConfig;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    private static Retrofit finnhubRetrofit;
    private static Retrofit alphaRetrofit;
    private static Retrofit geminiRetrofit;

    private static Gson gson() {
        return new GsonBuilder().create();
    }

    private static OkHttpClient baseClient(File cacheDir) {
        Cache cache = new Cache(new File(cacheDir, "http_cache"), 10L * 1024L * 1024L);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        return new OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public static FinnhubService finnhub(File cacheDir) {
        if (finnhubRetrofit == null) {
            OkHttpClient client = baseClient(cacheDir).newBuilder()
                    .addInterceptor(addQueryParam("token", BuildConfig.FINNHUB_API_KEY))
                    .build();

            finnhubRetrofit = new Retrofit.Builder()
                    .baseUrl("https://finnhub.io/api/v1/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson()))
                    .build();
        }
        return finnhubRetrofit.create(FinnhubService.class);
    }

    public static AlphaVantageService alphaVantage(File cacheDir) {
        if (alphaRetrofit == null) {
            OkHttpClient client = baseClient(cacheDir).newBuilder()
                    .addInterceptor(addQueryParam("apikey", BuildConfig.ALPHAVANTAGE_API_KEY))
                    .build();

            alphaRetrofit = new Retrofit.Builder()
                    .baseUrl("https://www.alphavantage.co/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson()))
                    .build();
        }
        return alphaRetrofit.create(AlphaVantageService.class);
    }

    public static GeminiService gemini(File cacheDir) {
        if (geminiRetrofit == null) {
            OkHttpClient client = baseClient(cacheDir).newBuilder()
                    .addInterceptor(addQueryParam("key", BuildConfig.GEMINI_API_KEY))
                    .build();

            geminiRetrofit = new Retrofit.Builder()
                    .baseUrl("https://generativelanguage.googleapis.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create(gson()))
                    .build();
        }
        return geminiRetrofit.create(GeminiService.class);
    }

    private static Interceptor addQueryParam(String key, String value) {
        return chain -> {
            Request req = chain.request();
            HttpUrl url = req.url().newBuilder().addQueryParameter(key, value).build();
            return chain.proceed(req.newBuilder().url(url).build());
        };
    }

    private ApiClient() {}
}
