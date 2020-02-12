package com.github.pjozsef.randomtree

import com.github.pjozsef.WeightedDie
import java.util.*

data class RandomNode<T>(
    val weights: List<Number>,
    val branches: List<RandomTree<T>>,
    val random: Random = Random()
) : RandomTree<T>() {
    val weightedDie = WeightedDie(
        this.branches,
        this.weights,
        this.random
    )
}

data class CompositeNode<T>(
    val components: Map<String, RandomTree<T>>,
    val combiner: (Map<String, T>) -> T
) : RandomTree<T>()

data class LeafNode<T>(val leafValue: T) : RandomTree<T>()

sealed class RandomTree<T> {
    val value: T
        get() = when (this) {
            is LeafNode<T> -> leafValue
            is CompositeNode<T> -> this.combinedValue()
            is RandomNode<T> -> this.randomBranch()
        }
}

private fun <T> CompositeNode<T>.combinedValue() =
    this.components.mapValues { (_, v) -> v.value }.let(combiner)

private fun <T> RandomNode<T>.randomBranch() = weightedDie.roll().value

