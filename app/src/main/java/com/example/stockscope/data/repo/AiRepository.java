package com.example.stockscope.data.repo;

import androidx.annotation.NonNull;

import com.example.stockscope.BuildConfig;
import com.example.stockscope.data.api.ApiClient;
import com.example.stockscope.data.api.GeminiService;
import com.example.stockscope.data.api.model.FinnhubNewsItem;
import com.example.stockscope.data.api.model.FinnhubQuote;
import com.example.stockscope.data.api.model.GeminiRequest;
import com.example.stockscope.data.api.model.GeminiResponse;
import com.example.stockscope.model.StockInsight;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

public final class AiRepository {

    public static final class AiDecision {
        public final String summary;
        public final String rationale;
        public final StockInsight.Opinion opinion;
        public final boolean aiGenerated;

        public AiDecision(String summary, String rationale, StockInsight.Opinion opinion, boolean aiGenerated) {
            this.summary = summary;
            this.rationale = rationale;
            this.opinion = opinion;
            this.aiGenerated = aiGenerated;
        }
    }

    public static final class TrendSnapshot {
        public final String label;
        public final double percent;

        public TrendSnapshot(String label, double percent) {
            this.label = label;
            this.percent = percent;
        }
    }

    private final GeminiService geminiService;
    private final Gson gson = new Gson();

    public AiRepository(File cacheDir) {
        this.geminiService = ApiClient.gemini(cacheDir);
    }

    public AiDecision summarize(
            String symbol,
            String companyName,
            FinnhubQuote quote,
            TrendSnapshot trendSnapshot,
            List<FinnhubNewsItem> newsItems
    ) {
        AiDecision fallback = heuristicDecision(companyName, quote, trendSnapshot, newsItems);
        if (BuildConfig.GEMINI_API_KEY == null || BuildConfig.GEMINI_API_KEY.trim().isEmpty()) {
            return fallback;
        }

        try {
            String payload = buildPrompt(symbol, companyName, quote, trendSnapshot, newsItems);
            Response<GeminiResponse> response = geminiService
                    .generateContent("gemini-1.5-flash", GeminiRequest.ofUserText(payload))
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                return fallback;
            }

            String text = response.body().firstTextOrNull();
            if (text == null || text.trim().isEmpty()) {
                return fallback;
            }

            JsonObject json = extractJson(text);
            if (json == null) {
                return fallback;
            }

            String summary = optString(json, "summary", fallback.summary);
            String rationale = optString(json, "rationale", fallback.rationale);
            StockInsight.Opinion opinion = parseOpinion(optString(json, "opinion", fallback.opinion.name()));
            return new AiDecision(summary, rationale, opinion, true);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String buildPrompt(
            String symbol,
            String companyName,
            FinnhubQuote quote,
            TrendSnapshot trendSnapshot,
            List<FinnhubNewsItem> newsItems
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("You analyze stock news for an educational Android app.\n");
        builder.append("Return strict JSON with keys summary, rationale, opinion.\n");
        builder.append("Opinion must be one of RISE, FALL, WATCH.\n");
        builder.append("The summary must be 2 sentences max and clear for retail users.\n");
        builder.append("The rationale must mention why the news could support that opinion.\n");
        builder.append("Do not give financial advice and do not mention certainty.\n\n");
        builder.append("Company: ").append(companyName).append(" (").append(symbol).append(")\n");
        builder.append(String.format(Locale.US, "Price: %.2f\n", quote.c));
        builder.append(String.format(Locale.US, "Daily change: %.2f%%\n", quote.dp));
        builder.append("30-day trend: ").append(trendSnapshot.label).append("\n");
        builder.append("News:\n");
        int limit = Math.min(newsItems.size(), 8);
        for (int i = 0; i < limit; i++) {
            FinnhubNewsItem item = newsItems.get(i);
            builder.append("- ")
                    .append(safe(item.headline))
                    .append(" | ")
                    .append(safe(item.summary))
                    .append('\n');
        }
        return builder.toString();
    }

    private AiDecision heuristicDecision(
            String companyName,
            FinnhubQuote quote,
            TrendSnapshot trendSnapshot,
            List<FinnhubNewsItem> newsItems
    ) {
        int score = scoreNews(newsItems);
        if (quote.dp >= 2.0d) {
            score += 1;
        } else if (quote.dp <= -2.0d) {
            score -= 1;
        }
        if (trendSnapshot.percent >= 4.0d) {
            score += 1;
        } else if (trendSnapshot.percent <= -4.0d) {
            score -= 1;
        }

        StockInsight.Opinion opinion = score >= 2
                ? StockInsight.Opinion.RISE
                : score <= -2 ? StockInsight.Opinion.FALL : StockInsight.Opinion.WATCH;

        StringBuilder summary = new StringBuilder();
        summary.append(companyName).append(" is trading at ")
                .append(String.format(Locale.US, "$%.2f", quote.c))
                .append(" after a ")
                .append(String.format(Locale.US, "%.2f%%", quote.dp))
                .append(" daily move. ");
        if (newsItems.isEmpty()) {
            summary.append("No recent company headlines were available, so this view leans more on price action than news flow.");
        } else {
            summary.append(buildHeadlineDigest(newsItems));
        }

        String rationale = buildRationale(opinion, trendSnapshot, newsItems, score);
        return new AiDecision(summary.toString(), rationale, opinion, false);
    }

    private int scoreNews(List<FinnhubNewsItem> newsItems) {
        int score = 0;
        for (FinnhubNewsItem item : newsItems) {
            String text = (safe(item.headline) + " " + safe(item.summary)).toLowerCase(Locale.US);
            score += keywordHits(text, "beat", "growth", "surge", "gain", "partnership", "upgrade", "profit");
            score -= keywordHits(text, "miss", "lawsuit", "probe", "layoff", "fall", "drop", "downgrade", "loss");
        }
        return score;
    }

    private int keywordHits(String text, String... words) {
        int hits = 0;
        for (String word : words) {
            if (text.contains(word)) {
                hits++;
            }
        }
        return hits;
    }

    private String buildHeadlineDigest(List<FinnhubNewsItem> newsItems) {
        StringBuilder digest = new StringBuilder("Recent coverage centers on ");
        int limit = Math.min(newsItems.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                digest.append(i == limit - 1 ? " and " : ", ");
            }
            digest.append('"').append(safe(newsItems.get(i).headline)).append('"');
        }
        digest.append('.');
        return digest.toString();
    }

    private String buildRationale(
            StockInsight.Opinion opinion,
            TrendSnapshot trendSnapshot,
            List<FinnhubNewsItem> newsItems,
            int score
    ) {
        String direction;
        if (opinion == StockInsight.Opinion.RISE) {
            direction = "The current mix of headlines and momentum leans constructive";
        } else if (opinion == StockInsight.Opinion.FALL) {
            direction = "The current mix of headlines and momentum leans cautious";
        } else {
            direction = "The available signals are mixed";
        }

        String newsClause = newsItems.isEmpty()
                ? "because there are no recent company headlines to confirm the move"
                : "because the last headlines do not point strongly in one direction";

        if (opinion == StockInsight.Opinion.WATCH) {
            return direction + " and the app keeps the stock on watch " + newsClause + ".";
        }

        return direction + " with a 30-day trend of " + trendSnapshot.label.toLowerCase(Locale.US)
                + " and a heuristic news score of " + score + ".";
    }

    private JsonObject extractJson(String responseText) {
        int start = responseText.indexOf('{');
        int end = responseText.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return gson.fromJson(responseText.substring(start, end + 1), JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private StockInsight.Opinion parseOpinion(String raw) {
        try {
            return StockInsight.Opinion.valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (Exception ignored) {
            return StockInsight.Opinion.WATCH;
        }
    }

    private String optString(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    @NonNull
    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
