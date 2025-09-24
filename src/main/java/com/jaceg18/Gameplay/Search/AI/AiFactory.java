package com.jaceg18.Gameplay.Search.AI;

import com.jaceg18.Gameplay.Search.AI.Algorithm.ArrayTranspositionTable;
import com.jaceg18.Gameplay.Search.AI.Algorithm.DefaultMoveOrderer;
import com.jaceg18.Gameplay.Search.AI.Algorithm.NegamaxAB;
import com.jaceg18.Gameplay.Search.AI.Algorithm.SearchAlgorithm;
import com.jaceg18.Gameplay.Search.AI.Evaluation.EvaluationStrategy;
import com.jaceg18.Gameplay.Search.SearchConfig;
import com.jaceg18.Gameplay.Search.SearchEngine;

import java.util.function.IntConsumer;

public class AiFactory {
    public static SearchEngine balanced() {
        var tt = new ArrayTranspositionTable(1 << 20);
        var orderer = new DefaultMoveOrderer();
        var eval = new EvaluationStrategy.Default();
        var metrics = new SearchMetrics();
        var depthBox = new MutableInt(8);

        SearchAlgorithm algo = new NegamaxAB(
                new SearchConstants(), tt, orderer, eval, metrics,
                depthBox::get, depthBox::set
        );


        var cfg = new SearchConfig(
                depthBox::get,
                (IntConsumer & java.io.Serializable) depthBox::set,
                algo
        );
        return new SearchEngine(cfg);
    }

    public static SearchEngine deep() {
        var eng = balanced();
        eng.setMaxDepth(14);
        return eng;
    }

    public static SearchEngine fast() {
        var eng = balanced();
        eng.setMaxDepth(5);
        return eng;
    }


    static final class MutableInt {
        private int v;
        MutableInt(int v){ this.v = v; }
        int get(){ return v; }
        void set(int nv){ v = nv; }
    }
}
