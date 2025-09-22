package com.jaceg18.Gameplay.Search.AI.Algorithm;

import com.jaceg18.Gameplay.Utility.GameState;

import java.util.function.IntConsumer;

public interface SearchAlgorithm {
    int computeBestMove(GameState root, IntConsumer progressCb);
}
