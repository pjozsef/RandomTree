package com.github.pjozsef.randomtree

import com.github.pjozsef.WeightedDie
import java.util.*

data class DicePoolNode<T>(
    val dicePool: List<Number>,
    val branches: List<RandomTree<T>>,
    val random: Random = Random()
) : RandomTree<T>()

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

data class RepeaterNode<T>(
    val repeater: Repeater,
    val node: RandomTree<T>
): RandomTree<T>()

data class CompositeNode<T>(
    val components: Map<String, RandomTree<T>>,
    val combiner: (Map<String, T>) -> T
) : RandomTree<T>()

data class LeafNode<T>(val leafValue: T) : RandomTree<T>()

data class TreeCollection<T>(val trees: List<RandomTree<T>>) : RandomTree<T>() {
    val values: List<T>
        get() = trees.flatMap {
            when (it) {
                is TreeCollection<T> -> it.values
                else -> listOf(it.value)
            }
        }
}

sealed class RandomTree<T> {
    val value: T
        get() = when (this) {
            is LeafNode<T> -> leafValue
            is CompositeNode<T> -> this.combinedValue()
            is RandomNode<T> -> this.randomBranch()
            is DicePoolNode<T> -> this.randomBranch()
            is TreeCollection<T> -> trees.first().value
            is RepeaterNode<T> -> node.value
        }
}

private fun <T> CompositeNode<T>.combinedValue() =
    this.components.mapValues { (_, v) -> v.value }.let(combiner)

private fun <T> RandomNode<T>.randomBranch() = weightedDie.roll().value

private data class Roll(
    val index: Int,
    val type: Int,
    val value: Int
)

private fun <T> DicePoolNode<T>.randomBranch() =
    dicePool.mapIndexed { i, it ->
        Roll(
            i,
            it.toInt(),
            random.nextInt(it.toInt()) + 1
        )
    }.reduce { acc, curr ->
        when {
            curr.value > acc.value -> curr
            curr.value == acc.value && curr.type > acc.type -> curr
            else -> acc
        }
    }.let {
        branches[it.index].value
    }

private fun <T> RepeaterNode<T>.repeatedValue() = node.value
