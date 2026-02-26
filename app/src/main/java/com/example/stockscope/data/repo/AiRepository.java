package com.example.stockscope.data.repo;

import android.app.Application;

import androidx.lifecycle.MutableLiveData;

import com.example.stockscope.data.api.ApiClient;
import com.example.stockscope.data.api.GeminiService;
import com.example.stockscope.data.api.model.GeminiRequest;
import com.example.stockscope.data.api.model.GeminiResponse;
import com.example.stockscope.util.Resource;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class AiRepository {

    private final GeminiService gemini;

    public AiRepository(Application app) {
        this.gemini = ApiClient.gemini(app.getCacheDir());
    }

    public MutableLiveData<Resource<String>> insight(String symbol, double price, double changePct, String trendSummary) {
        MutableLiveData<Resource<String>> live = new MutableLiveData<>();
        live.setValue(Resource.loading(null));

        String prompt =
                "You are a finance assistant for a minimalist retail trading app.\n" +
                        "Write a short, neutral insight (max 5 sentences) about the stock.\n" +
                        "No hype, no advice. Include: trend, volatility hint, and one risk factor.\n\n" +
                        "Symbol: " + symbol + "\n" +
                        "Price: " + price + "\n" +
                        "Change% (1D): " + changePct + "\n" +
                        "30D trend: " + trendSummary + "\n";

        GeminiRequest body = GeminiRequest.ofUserText(prompt);

        // Use a fast model name; adjust if your account lists different models.
        gemini.generateContent("gemini-1.5-flash-001", body).enqueue(new Callback<>() {
            @Override public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String text = resp.body().firstTextOrNull();
                    if (text == null || text.trim().isEmpty()) live.setValue(Resource.error("Empty AI response", null));
                    else live.setValue(Resource.success(text.trim()));
                } else {
                    live.setValue(Resource.error("AI failed: " + resp.code(), null));
                }
            }

            @Override public void onFailure(Call<GeminiResponse> call, Throwable t) {
                live.setValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return live;
    }
}
