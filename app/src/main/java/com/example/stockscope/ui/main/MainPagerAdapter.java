package com.example.stockscope.ui.main;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.stockscope.ui.search.SearchFragment;
import com.example.stockscope.ui.watchlist.WatchlistFragment;

public class MainPagerAdapter extends FragmentStateAdapter {
    public MainPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }

    @NonNull @Override public Fragment createFragment(int position) {
        return position == 0 ? new WatchlistFragment() : new SearchFragment();
    }

    @Override public int getItemCount() { return 2; }
}
