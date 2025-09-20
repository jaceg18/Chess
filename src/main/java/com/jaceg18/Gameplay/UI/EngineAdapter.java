package com.jaceg18.Gameplay.UI;

import com.jaceg18.Gameplay.Search.AI.AiProvider;
import com.jaceg18.Gameplay.Search.SearchEngine;
import com.jaceg18.Gameplay.Utility.GameState;

public class EngineAdapter implements AiProvider {
    private final SearchEngine engine;
    public EngineAdapter(SearchEngine engine) { this.engine = engine; }
    @Override public int pickMove(GameState snapshot) { return engine.pickMove(snapshot); }
    @Override public void setMaxDepth(int maxDepth) { engine.setMaxDepth(maxDepth); }
    @Override public int getMaxDepth() { return engine.getMaxDepth(); }
    @Override public void setProgressCallback(java.util.function.IntConsumer cb) { engine.setProgressCallback(cb); }
}