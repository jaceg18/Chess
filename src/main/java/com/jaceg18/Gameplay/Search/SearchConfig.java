package com.jaceg18.Gameplay.Search;

import com.jaceg18.Gameplay.Search.AI.Algorithm.SearchAlgorithm;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public record SearchConfig(
        IntSupplier maxDepthSupplier,
        IntConsumer maxDepthSetter,
        SearchAlgorithm algorithm
) {}
