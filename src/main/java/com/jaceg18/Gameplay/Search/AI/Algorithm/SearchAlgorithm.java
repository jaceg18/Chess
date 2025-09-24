package com.jaceg18.Gameplay.Search.AI.Algorithm;

import com.jaceg18.Gameplay.Utility.GameState;

import java.util.function.IntConsumer;
import java.util.function.LongToIntFunction;

public interface SearchAlgorithm {
    int computeBestMove(GameState root, IntConsumer progressCb);

    int computeBestMove(GameState root, int timeMs, IntConsumer progressCb);

    void setRepetitionCounter(LongToIntFunction f);


}
