package com.github.pjozsef.factory

import com.github.pjozsef.CompositeNode
import com.github.pjozsef.Leaf
import com.github.pjozsef.RandomNode
import com.github.pjozsef.RandomTree
import java.util.*

fun <T> l(value: T) = Leaf(value)

fun <T> c(components: Map<String, RandomTree<T>>, combiner: (Map<String, T>) -> T) =
    CompositeNode(components, combiner)

fun <T> r(weights: List<Number>, branches: List<RandomTree<T>>, random: Random) =
    RandomNode(weights, branches, random)
