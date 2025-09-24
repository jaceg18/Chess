package com.jaceg18.Gameplay.Search.AI;

import com.jaceg18.Gameplay.Utility.GameState;

import java.util.function.LongToIntFunction;

public interface AiProvider {
    int pickMove(GameState snapshot, int timeMs);
    void setMaxDepth(int maxDepth);
    int getMaxDepth();
    default void setProgressCallback(java.util.function.IntConsumer cb) {}

    void setRepetitionCounter(LongToIntFunction o);
}