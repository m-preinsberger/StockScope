package com.example.stockscope.ui.main;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.stockscope.ui.analysis.AnalysisFragment;
import com.example.stockscope.ui.watchlist.WatchlistFragment;

public final class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull AppCompatActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return position == 0 ? new AnalysisFragment() : new WatchlistFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
