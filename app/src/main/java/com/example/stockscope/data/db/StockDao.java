package com.example.stockscope.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StockDao {

    @Query("SELECT * FROM watchlist ORDER BY symbol ASC")
    LiveData<List<WatchlistEntity>> watchlist();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertWatchlist(WatchlistEntity e);

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    void deleteWatchlist(String symbol);

    @Query("SELECT * FROM cached_quote WHERE symbol = :symbol LIMIT 1")
    LiveData<CachedQuoteEntity> cachedQuoteLive(String symbol);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCachedQuote(CachedQuoteEntity e);
}
