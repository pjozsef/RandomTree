package com.github.pjozsef.randomtree.cli

import com.github.pjozsef.randomtree.RandomTree
import com.github.pjozsef.randomtree.TreeCollection
import com.github.pjozsef.randomtree.io.RandomTreeReader.CONCAT_COMBINER
import com.github.pjozsef.randomtree.io.RandomTreeReader.IDENTITY_MAPPER
import com.github.pjozsef.randomtree.io.readTreeFromFile
import java.util.Random

fun main(args: Array<String>) {
    val random = System.currentTimeMillis().toString(36).let {
        val seed = it.takeLast(5).toUpperCase()
        println("Seed: $seed")
        Random(seed.toLong(36))
    }
    val randomTrees = readTreeFromFile(args[0], IDENTITY_MAPPER, CONCAT_COMBINER, random)
    (1..args.lastIndex).forEach { category ->
        randomTrees[args[category]]?.print()
        println("-----")
    }
}

private fun <T> RandomTree<T>.print() = when (this) {
    is TreeCollection -> println(this.values)
    else -> println(this.value)
}
