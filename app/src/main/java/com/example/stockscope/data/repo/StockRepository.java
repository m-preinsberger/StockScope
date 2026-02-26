package com.example.stockscope.data.repo;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.stockscope.data.api.AlphaVantageService;
import com.example.stockscope.data.api.ApiClient;
import com.example.stockscope.data.api.FinnhubService;
import com.example.stockscope.data.api.model.FinnhubQuote;
import com.example.stockscope.data.api.model.FinnhubSearchResponse;
import com.example.stockscope.data.db.AppDatabase;
import com.example.stockscope.data.db.CachedQuoteEntity;
import com.example.stockscope.data.db.StockDao;
import com.example.stockscope.data.db.WatchlistEntity;
import com.example.stockscope.util.Resource;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class StockRepository {

    public static final class ChartData {
        public final List<Float> prices;
        public final List<String> labels;
        public ChartData(List<Float> prices, List<String> labels) {
            this.prices = prices; this.labels = labels;
        }
    }

    private final FinnhubService finnhub;
    private final AlphaVantageService alpha;
    private final StockDao dao;
    private final ExecutorService io = Executors.newFixedThreadPool(2);

    public StockRepository(Application app) {
        this.finnhub = ApiClient.finnhub(app.getCacheDir());
        this.alpha = ApiClient.alphaVantage(app.getCacheDir());
        this.dao = AppDatabase.get(app).stockDao();
    }

    public LiveData<List<WatchlistEntity>> watchlist() { return dao.watchlist(); }

    public LiveData<CachedQuoteEntity> cachedQuoteLive(String symbol) { return dao.cachedQuoteLive(symbol); }

    public void addToWatchlist(String symbol, String name) {
        io.execute(() -> dao.upsertWatchlist(new WatchlistEntity(symbol, name)));
    }

    public void removeFromWatchlist(String symbol) {
        io.execute(() -> dao.deleteWatchlist(symbol));
    }

    public MutableLiveData<Resource<FinnhubSearchResponse>> search(String q) {
        MutableLiveData<Resource<FinnhubSearchResponse>> live = new MutableLiveData<>();
        live.setValue(Resource.loading(null));
        finnhub.search(q).enqueue(new Callback<>() {
            @Override public void onResponse(Call<FinnhubSearchResponse> call, Response<FinnhubSearchResponse> resp) {
                if (resp.isSuccessful() && resp.body() != null) live.setValue(Resource.success(resp.body()));
                else live.setValue(Resource.error("Search failed: " + resp.code(), null));
            }
            @Override public void onFailure(Call<FinnhubSearchResponse> call, Throwable t) {
                live.setValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return live;
    }

    public MutableLiveData<Resource<FinnhubQuote>> refreshQuote(String symbol) {
        MutableLiveData<Resource<FinnhubQuote>> live = new MutableLiveData<>();
        live.setValue(Resource.loading(null));
        finnhub.quote(symbol).enqueue(new Callback<>() {
            @Override public void onResponse(Call<FinnhubQuote> call, Response<FinnhubQuote> resp) {
                if (resp.isSuccessful() && resp.body() != null) {
                    FinnhubQuote q = resp.body();
                    live.setValue(Resource.success(q));
                    io.execute(() -> dao.upsertCachedQuote(new CachedQuoteEntity(
                            symbol, q.c, q.d, q.dp, System.currentTimeMillis()
                    )));
                } else {
                    live.setValue(Resource.error("Quote failed: " + resp.code(), null));
                }
            }
            @Override public void onFailure(Call<FinnhubQuote> call, Throwable t) {
                live.setValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });
        return live;
    }

    public MutableLiveData<Resource<ChartData>> loadDailyChart30(String symbol) {
        MutableLiveData<Resource<ChartData>> live = new MutableLiveData<>();
        live.setValue(Resource.loading(null));

        // TIME_SERIES_DAILY_ADJUSTED is widely available; compact returns ~100 points.
        alpha.dailyAdjusted("TIME_SERIES_DAILY_ADJUSTED", symbol, "compact").enqueue(new Callback<>() {
            @Override public void onResponse(Call<JsonObject> call, Response<JsonObject> resp) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    live.setValue(Resource.error("Chart failed: " + resp.code(), null));
                    return;
                }
                try {
                    ChartData cd = parseAlphaDaily(resp.body(), 30);
                    live.setValue(Resource.success(cd));
                } catch (Exception ex) {
                    live.setValue(Resource.error("Parse error: " + ex.getMessage(), null));
                }
            }

            @Override public void onFailure(Call<JsonObject> call, Throwable t) {
                live.setValue(Resource.error(t.getMessage() != null ? t.getMessage() : "Network error", null));
            }
        });

        return live;
    }

    private static ChartData parseAlphaDaily(JsonObject root, int maxPoints) throws ParseException {
        JsonObject series = root.getAsJsonObject("Time Series (Daily)");
        if (series == null) throw new IllegalStateException("Missing Time Series (Daily)");

        // Alpha returns newest-to-oldest by JSON iteration order (not guaranteed). We sort by date.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        List<Date> dates = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : series.entrySet()) dates.add(sdf.parse(e.getKey()));

        dates.sort(Date::compareTo); // oldest -> newest

        int start = Math.max(0, dates.size() - maxPoints);
        List<Float> prices = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = start; i < dates.size(); i++) {
            String key = sdf.format(dates.get(i));
            JsonObject day = series.getAsJsonObject(key);
            if (day == null) continue;
            String closeStr = pick(day, "4. close", "5. adjusted close");
            float close = Float.parseFloat(closeStr);
            prices.add(close);
            labels.add(key.substring(5)); // MM-dd
        }

        return new ChartData(prices, labels);
    }

    private static String pick(JsonObject obj, String a, String b) {
        if (obj.has(b)) return obj.get(b).getAsString();
        if (obj.has(a)) return obj.get(a).getAsString();
        throw new IllegalStateException("Missing close field");
    }
}
