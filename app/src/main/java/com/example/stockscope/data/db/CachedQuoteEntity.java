package com.example.stockscope.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_quote")
public class CachedQuoteEntity {
    @PrimaryKey @NonNull
    public String symbol;

    public double price;
    public double change;
    public double changePct;
    public long updatedAtMs;

    public CachedQuoteEntity(@NonNull String symbol, double price, double change, double changePct, long updatedAtMs) {
        this.symbol = symbol;
        this.price = price;
        this.change = change;
        this.changePct = changePct;
        this.updatedAtMs = updatedAtMs;
    }
}
