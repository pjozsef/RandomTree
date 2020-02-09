package com.github.pjozsef

import java.util.*

data class RandomNode<T>(
    val weights: List<Number>,
    val branches: List<RandomTree<T>>,
    val random: Random = Random()
) : RandomTree<T>()

data class CompositeNode<T, C>(
    val components: List<RandomTree<C>>,
    val combiner: (List<C>) -> T
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
    this.components.map(RandomTree<C>::value).let(combiner)

private fun <T> RandomNode<T>.randomBranch() =
    WeightedDie(
        this.branches,
        this.weights,
        random
    ).roll().value

