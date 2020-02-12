package com.github.pjozsef.randomtree.cli

import com.github.pjozsef.randomtree.io.readTreeFromFile
import java.util.Random

fun main(args: Array<String>) {
    val identityMapper: (String) -> String = { it }
    val concatCombiner: (Map<String, String>) -> String = {
        it.toSortedMap().values.joinToString("_")
    }
    val random = System.currentTimeMillis().toString(36).let{
        val seed = it.takeLast(5).toUpperCase()
        println("Seed: $seed")
        Random(seed.toLong(36))
    }
    val randomTrees = readTreeFromFile(args[0], identityMapper, concatCombiner, random)
    (1..args.lastIndex).chunked(2).forEach {(times, category) ->
        repeat(args[times].toInt()){
            randomTrees[args[category]]?.value?.let(::println)
        }
        println("-----")
    }
}
