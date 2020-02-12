package com.github.pjozsef.factory

import com.github.pjozsef.randomtree.CompositeNode
import com.github.pjozsef.randomtree.LeafNode
import com.github.pjozsef.randomtree.RandomNode
import com.github.pjozsef.randomtree.RandomTree
import java.util.*

fun <T> l(value: T) = LeafNode(value)

fun <T> c(components: Map<String, RandomTree<T>>, combiner: (Map<String, T>) -> T) =
    CompositeNode(components, combiner)

fun <T> r(weights: List<Number>, branches: List<RandomTree<T>>, random: Random) =
    RandomNode(weights, branches, random)
