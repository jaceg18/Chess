package com.jaceg18.Gameplay.Search;

import com.jaceg18.Gameplay.Search.AI.Algorithm.SearchAlgorithm;

import com.jaceg18.Gameplay.Opening.OpeningBook;
import com.jaceg18.Gameplay.Search.AI.AiProvider;

import com.jaceg18.Gameplay.Utility.GameState;

import java.util.function.IntConsumer;

public final class SearchEngine implements AiProvider {
    private final SearchConfig cfg;
    private OpeningBook book;
    private IntConsumer progressCb;

    private final SearchAlgorithm algo;

    public SearchEngine(SearchConfig cfg) {
        this.cfg = cfg;
        this.algo = cfg.algorithm();
    }

    public void setOpeningBook(OpeningBook book) { this.book = book; }

    @Override public void setMaxDepth(int d) { cfg.maxDepthSetter().accept(d); }
    @Override public int getMaxDepth() { return cfg.maxDepthSupplier().getAsInt(); }

    @Override public void setProgressCallback(IntConsumer cb) { this.progressCb = cb; }

    @Override public int pickMove(GameState s) {
        // 1) Try book
        if (book != null) {
            int bm = book.pick(s);
            if (bm != 0) {
                if (progressCb != null) progressCb.accept(100);
                return bm;
            }
        }
        // 2) Search
        return algo.computeBestMove(s, progressCb);
    }
}