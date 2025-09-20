package com.jaceg18.Gameplay.Search.AI.Evaluation;

import com.jaceg18.Gameplay.Utility.GameState;

public interface EvaluationStrategy {
    int evalSTM(GameState s);

    final class Default implements EvaluationStrategy {
        @Override public int evalSTM(GameState s) {
            int whitePOV = Eval.evaluate(s);
            return s.whiteToMove() ? whitePOV : -whitePOV;
        }
    }
}
