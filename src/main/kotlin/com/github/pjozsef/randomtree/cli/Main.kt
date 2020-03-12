package com.github.pjozsef.randomtree.cli

import com.github.pjozsef.randomtree.RandomTree
import com.github.pjozsef.randomtree.TreeCollection
import com.github.pjozsef.randomtree.io.readTreeFromFile
import java.util.Random

fun main(args: Array<String>) {
    val identityMapper: (String) -> String = { it }
    val concatCombiner: (Map<String, String>) -> String = {
        it.toSortedMap().values.joinToString(" ")
    }
    val random = System.currentTimeMillis().toString(36).let {
        val seed = it.takeLast(5).toUpperCase()
        println("Seed: $seed")
        Random(seed.toLong(36))
    }
    val randomTrees = readTreeFromFile(args[0], identityMapper, concatCombiner, random)
    (1..args.lastIndex).forEach { category ->
        randomTrees[args[category]]?.print()
        println("-----")
    }
}

private fun <T> RandomTree<T>.print() = when (this) {
    is TreeCollection -> println(this.values)
    else -> println(this.value)
}
