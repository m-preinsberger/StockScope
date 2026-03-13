package com.example.stockscope;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.stockscope.databinding.ActivityMainBinding;
import com.example.stockscope.ui.main.MainPagerAdapter;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabs, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? R.string.analysis_tab : R.string.watchlist_tab);
        }).attach();
    }

    public void showAnalysisTab() {
        binding.viewPager.setCurrentItem(0, true);
    }
}
