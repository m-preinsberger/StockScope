package com.example.stockscope.ui.main;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayoutMediator;
import com.example.stockscope.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding b;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        b.viewPager.setAdapter(adapter);

        new TabLayoutMediator(b.tabs, b.viewPager, (tab, pos) -> {
            tab.setText(pos == 0 ? "Watchlist" : "Search");
        }).attach();
    }
}
