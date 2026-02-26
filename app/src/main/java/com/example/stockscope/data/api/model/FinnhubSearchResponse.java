package com.example.stockscope.data.api.model;

import java.util.List;

public class FinnhubSearchResponse {
    public int count;
    public List<Result> result;

    public static class Result {
        public String description;
        public String displaySymbol;
        public String symbol;
        public String type;
    }
}
