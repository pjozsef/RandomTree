package com.github.pjozsef.factory

import com.github.pjozsef.randomtree.*
import java.util.*

fun <T> l(value: T) = LeafNode(value)

fun <T> c(components: Map<String, RandomTree<T>>, combiner: (Map<String, T>) -> T) =
    CompositeNode(components, combiner)

fun <T> r(weights: List<Number>, branches: List<RandomTree<T>>, random: Random) =
    RandomNode(weights, branches, random)

fun <T> d(dicePool: List<Number>, branches: List<RandomTree<T>>, random: Random) =
    DicePoolNode(dicePool, branches, random)

fun <T> coll(vararg trees: RandomTree<T>) = TreeCollection(trees.asList())
