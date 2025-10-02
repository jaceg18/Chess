package com.jaceg18.Gameplay.Search;

import com.jaceg18.Gameplay.Opening.OpeningBook;
import com.jaceg18.Gameplay.Search.AI.AiProvider;
import com.jaceg18.Gameplay.Search.AI.Algorithm.SearchAlgorithm;
import com.jaceg18.Gameplay.Utility.GameState;
import com.jaceg18.ModernMain;

import java.util.function.IntConsumer;
import java.util.function.LongToIntFunction;

public final class SearchEngine implements AiProvider {
    private final SearchConfig cfg;
    private OpeningBook book;
    private IntConsumer progressCb;

    private final SearchAlgorithm algo;

    private boolean declaredOpening = false;

    public SearchEngine(SearchConfig cfg) {
        this.cfg = cfg;
        this.algo = cfg.algorithm();
    }

    public void setOpeningBook(OpeningBook book) { this.book = book; }

    @Override public void setMaxDepth(int d) { cfg.maxDepthSetter().accept(d); }


    @Override public int getMaxDepth() { return cfg.maxDepthSupplier().getAsInt(); }

    @Override public void setProgressCallback(IntConsumer cb) { this.progressCb = cb; }


    @Override
    public void setRepetitionCounter(LongToIntFunction o) {
        algo.setRepetitionCounter(o);
    }


    @Override public int pickMove(GameState s, int timeMs) {
        if (book != null) {
            int bm = book.pick(s);
            if (bm != 0) {
                if (!declaredOpening){
                    GameState clone = s.clone();
                    clone.make(bm);
                    String name = book.getOpeningName(clone);
                    if (name != null && !name.isEmpty()) {
                        ModernMain.displayOpening(name);
                    }
                    declaredOpening = true;
                }
                if (progressCb != null) progressCb.accept(100);
                return bm;
            }

        }
        return timeMs > 0 ? algo.computeBestMove(s, timeMs, null) : algo.computeBestMove(s, progressCb);

    }
}