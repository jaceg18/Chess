package com.jaceg18.Gameplay.Search.AI;

import com.jaceg18.Gameplay.Utility.GameState;

public interface AiProvider {
    int pickMove(GameState snapshot);
    void setMaxDepth(int maxDepth);
    int getMaxDepth();
    default void setProgressCallback(java.util.function.IntConsumer cb) {}
}