package com.example.stockscope.data.repo;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.stockscope.BuildConfig;
import com.example.stockscope.data.api.AlphaVantageService;
import com.example.stockscope.data.api.ApiClient;
import com.example.stockscope.data.api.FinnhubService;
import com.example.stockscope.data.api.model.FinnhubNewsItem;
import com.example.stockscope.data.api.model.FinnhubQuote;
import com.example.stockscope.data.api.model.FinnhubSearchResponse;
import com.example.stockscope.data.db.AppDatabase;
import com.example.stockscope.data.db.CachedQuoteEntity;
import com.example.stockscope.data.db.StockDao;
import com.example.stockscope.data.db.WatchlistEntity;
import com.example.stockscope.model.SearchSuggestion;
import com.example.stockscope.model.StockInsight;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public final class StockRepository {

    private final FinnhubService finnhubService;
    private final AlphaVantageService alphaVantageService;
    private final StockDao stockDao;
    private final AiRepository aiRepository;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final DecimalFormat priceFormat = new DecimalFormat("$#,##0.00");
    private final SimpleDateFormat apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public StockRepository(Application application) {
        this.finnhubService = ApiClient.finnhub(application.getCacheDir());
        this.alphaVantageService = ApiClient.alphaVantage(application.getCacheDir());
        this.stockDao = AppDatabase.get(application).stockDao();
        this.aiRepository = new AiRepository(application.getCacheDir());
    }

    public LiveData<List<WatchlistEntity>> watchlist() {
        return stockDao.watchlist();
    }

    public void addToWatchlist(String symbol, String name) {
        ioExecutor.execute(() -> stockDao.upsertWatchlist(new WatchlistEntity(symbol, name)));
    }

    public void removeFromWatchlist(String symbol) {
        ioExecutor.execute(() -> stockDao.deleteWatchlist(symbol));
    }

    public List<SearchSuggestion> search(String query) throws IOException {
        ensureFinnhubConfigured();
        Response<FinnhubSearchResponse> response = finnhubService.search(query.trim()).execute();
        if (!response.isSuccessful() || response.body() == null || response.body().result == null) {
            throw new IOException("Stock search failed.");
        }

        List<SearchSuggestion> suggestions = new ArrayList<>();
        for (FinnhubSearchResponse.Result result : response.body().result) {
            if (result == null || result.symbol == null || result.symbol.trim().isEmpty()) {
                continue;
            }
            suggestions.add(new SearchSuggestion(
                    result.symbol.trim().toUpperCase(Locale.US),
                    result.description == null || result.description.trim().isEmpty()
                            ? result.symbol.trim().toUpperCase(Locale.US)
                            : result.description.trim(),
                    result.type == null ? "" : result.type.trim()
            ));
            if (suggestions.size() == 8) {
                break;
            }
        }
        return suggestions;
    }

    public StockInsight analyze(String rawSymbol, String providedName) throws IOException {
        ensureFinnhubConfigured();

        String symbol = rawSymbol.trim().toUpperCase(Locale.US);
        Response<FinnhubQuote> quoteResponse = finnhubService.quote(symbol).execute();
        if (!quoteResponse.isSuccessful() || quoteResponse.body() == null) {
            throw new IOException("Unable to load the current quote for " + symbol + ".");
        }

        FinnhubQuote quote = quoteResponse.body();
        List<FinnhubNewsItem> news = fetchNews(symbol);
        AiRepository.TrendSnapshot trendSnapshot = fetchTrend(symbol);
        String companyName = resolveCompanyName(symbol, providedName);
        AiRepository.AiDecision decision = aiRepository.summarize(
                symbol,
                companyName,
                quote,
                trendSnapshot,
                news
        );

        ioExecutor.execute(() -> stockDao.upsertCachedQuote(new CachedQuoteEntity(
                symbol,
                quote.c,
                quote.d,
                quote.dp,
                System.currentTimeMillis()
        )));

        return new StockInsight(
                symbol,
                companyName,
                priceFormat.format(quote.c),
                formatMovement(quote),
                trendSnapshot.label,
                decision.summary,
                decision.rationale,
                decision.opinion,
                decision.aiGenerated,
                news
        );
    }

    private List<FinnhubNewsItem> fetchNews(String symbol) {
        try {
            Calendar calendar = Calendar.getInstance();
            Date today = calendar.getTime();
            calendar.add(Calendar.DAY_OF_YEAR, -7);
            Date from = calendar.getTime();
            Response<List<FinnhubNewsItem>> response = finnhubService.companyNews(
                    symbol,
                    apiDateFormat.format(from),
                    apiDateFormat.format(today)
            ).execute();
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }
            List<FinnhubNewsItem> items = new ArrayList<>(response.body());
            items.sort(Comparator.comparingLong((FinnhubNewsItem item) -> item.datetime).reversed());
            return items.size() > 10 ? new ArrayList<>(items.subList(0, 10)) : items;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private AiRepository.TrendSnapshot fetchTrend(String symbol) {
        if (BuildConfig.ALPHAVANTAGE_API_KEY == null || BuildConfig.ALPHAVANTAGE_API_KEY.trim().isEmpty()) {
            return new AiRepository.TrendSnapshot("30-day trend unavailable", 0.0d);
        }

        try {
            Response<JsonObject> response = alphaVantageService
                    .dailyAdjusted("TIME_SERIES_DAILY_ADJUSTED", symbol, "compact")
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                return new AiRepository.TrendSnapshot("30-day trend unavailable", 0.0d);
            }

            JsonObject series = response.body().getAsJsonObject("Time Series (Daily)");
            if (series == null || series.entrySet().size() < 2) {
                return new AiRepository.TrendSnapshot("30-day trend unavailable", 0.0d);
            }

            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(series.entrySet());
            entries.sort(Map.Entry.comparingByKey());

            int startIndex = Math.max(0, entries.size() - 30);
            double first = readClose(entries.get(startIndex));
            double last = readClose(entries.get(entries.size() - 1));
            double percent = first == 0.0d ? 0.0d : ((last - first) / first) * 100.0d;

            String label;
            if (percent >= 5.0d) {
                label = String.format(Locale.US, "Strongly up over 30 days (%.1f%%)", percent);
            } else if (percent >= 1.5d) {
                label = String.format(Locale.US, "Moderately up over 30 days (%.1f%%)", percent);
            } else if (percent <= -5.0d) {
                label = String.format(Locale.US, "Strongly down over 30 days (%.1f%%)", percent);
            } else if (percent <= -1.5d) {
                label = String.format(Locale.US, "Moderately down over 30 days (%.1f%%)", percent);
            } else {
                label = String.format(Locale.US, "Mostly flat over 30 days (%.1f%%)", percent);
            }

            return new AiRepository.TrendSnapshot(label, percent);
        } catch (Exception ignored) {
            return new AiRepository.TrendSnapshot("30-day trend unavailable", 0.0d);
        }
    }

    private double readClose(Map.Entry<String, JsonElement> entry) {
        JsonObject day = entry.getValue().getAsJsonObject();
        if (day.has("5. adjusted close")) {
            return day.get("5. adjusted close").getAsDouble();
        }
        return day.get("4. close").getAsDouble();
    }

    private void ensureFinnhubConfigured() throws IOException {
        if (BuildConfig.FINNHUB_API_KEY == null || BuildConfig.FINNHUB_API_KEY.trim().isEmpty()) {
            throw new IOException("Finnhub API key is missing. Add it to local.properties to enable quotes and news.");
        }
    }

    private String resolveCompanyName(String symbol, String providedName) {
        if (providedName != null && !providedName.trim().isEmpty()) {
            return providedName.trim();
        }
        return symbol;
    }

    private String formatMovement(FinnhubQuote quote) {
        String direction = quote.d >= 0 ? "+" : "";
        return String.format(
                Locale.US,
                "%s%s today (%s%.2f%%)",
                direction,
                priceFormat.format(quote.d).replace("$-", "-$"),
                quote.dp >= 0 ? "+" : "",
                quote.dp
        );
    }
}
