package com.example.stockscope.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "watchlist")
public class WatchlistEntity {
    @PrimaryKey @NonNull
    public String symbol;

    public String name;

    public WatchlistEntity(@NonNull String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }
}
