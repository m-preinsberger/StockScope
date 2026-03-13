package com.example.stockscope.ui.watchlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.stockscope.MainActivity;
import com.example.stockscope.databinding.FragmentWatchlistBinding;
import com.example.stockscope.ui.common.WatchlistAdapter;
import com.example.stockscope.viewmodel.StockViewModel;

public class WatchlistFragment extends Fragment {

    private FragmentWatchlistBinding binding;
    private StockViewModel viewModel;
    private WatchlistAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWatchlistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(StockViewModel.class);

        adapter = new WatchlistAdapter(
                item -> {
                    viewModel.analyze(item.symbol, item.name);
                    ((MainActivity) requireActivity()).showAnalysisTab();
                },
                item -> viewModel.removeFromWatchlist(item.symbol)
        );
        binding.watchlistRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.watchlistRecycler.setAdapter(adapter);

        viewModel.watchlist().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            binding.emptyView.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
