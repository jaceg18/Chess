package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Search.AI.AiProvider;
import com.jaceg18.Gameplay.Search.SearchEngine;
import com.jaceg18.Gameplay.Utility.GameState;

import java.util.function.LongToIntFunction;

public class EngineAdapter implements AiProvider {
    private final SearchEngine engine;
    public EngineAdapter(SearchEngine engine) { this.engine = engine; }
    @Override public int pickMove(GameState snapshot, int timeMs) { return engine.pickMove(snapshot, timeMs); }
    @Override public void setMaxDepth(int maxDepth) { engine.setMaxDepth(maxDepth); }
    @Override public int getMaxDepth() { return engine.getMaxDepth(); }
    @Override public void setProgressCallback(java.util.function.IntConsumer cb) { engine.setProgressCallback(cb); }


    @Override
    public void setRepetitionCounter(LongToIntFunction o) {
        engine.setRepetitionCounter(o);
    }
}