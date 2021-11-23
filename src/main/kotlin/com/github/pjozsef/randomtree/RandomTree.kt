package com.github.pjozsef.randomtree

import com.github.pjozsef.WeightedDie
import java.util.*

data class DicePoolNode<T>(
    val dicePool: List<Number>,
    val branches: List<RandomTree<T>>,
    val random: Random = Random()
) : RandomTree<T> {
    override val values: List<T>
        get() = dicePool.mapIndexed { i, it ->
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
            listOf(branches[it.index].value)
        }
}

data class RandomNode<T>(
    val weights: List<Number>,
    val branches: List<RandomTree<T>>,
    val random: Random = Random()
) : RandomTree<T> {
    val weightedDie = WeightedDie(
        this.branches,
        this.weights,
        this.random
    )

    override val values: List<T>
        get() = listOf(weightedDie.roll().value)
}

data class RepeaterNode<T>(
    val repeater: Repeater,
    val node: RandomTree<T>
) : RandomTree<T> {
    override val values: List<T>
        get() = (1..repeater.getAmount()).map { node.value }
}

data class CompositeNode<T>(
    val components: Map<String, RandomTree<T>>,
    val combiner: (Map<String, T>) -> T
) : RandomTree<T> {
    override val values: List<T>
        get() = listOf(components.mapValues { (_, v) -> v.value }.let(combiner))
}

data class LeafNode<T>(val leafValue: T) : RandomTree<T> {
    override val values: List<T>
        get() = listOf(leafValue)
}

data class TreeCollection<T>(val trees: List<RandomTree<T>>) : RandomTree<T> {
    override val values: List<T>
        get() = trees.flatMap { it.values }
}

sealed interface RandomTree<T> {
    val values: List<T>

    val value: T
        get() = values.first()
}

private data class Roll(
    val index: Int,
    val type: Int,
    val value: Int
)
