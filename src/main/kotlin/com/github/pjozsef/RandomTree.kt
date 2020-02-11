package com.github.pjozsef

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

data class CompositeNode<T, C>(
    val components: Map<String, RandomTree<C>>,
    val combiner: (Map<String, C>) -> T
) : RandomTree<T>()

data class Leaf<T>(val leafValue: T) : RandomTree<T>()

sealed class RandomTree<T> {
    val value: T
        get() = when (this) {
            is Leaf<T> -> leafValue
            is CompositeNode<T, *> -> this.combinedValue()
            is RandomNode<T> -> this.randomBranch()
        }
}

private fun <T, C> CompositeNode<T, C>.combinedValue() =
    this.components.mapValues { (_, v) -> v.value }.let(combiner)

private fun <T> RandomNode<T>.randomBranch() = weightedDie.roll().value

