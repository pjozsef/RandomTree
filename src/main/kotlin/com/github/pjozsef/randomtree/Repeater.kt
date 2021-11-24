package com.github.pjozsef.randomtree

import com.github.pjozsef.DiceRoll
import com.github.pjozsef.WeightedCoin
import java.util.*

interface Repeater {
    fun getAmount() : Int
}

data class ConstantRepeater(val times: Int) : Repeater {
    override fun getAmount() = times
}

data class RangeRepeater(val range: IntRange, val random: Random): Repeater {
    override fun getAmount(): Int {
        val start = range.first
        val end = range.last

        val length = end - start

        return random.nextInt(length+1) + start
    }
}

data class DiceRollRepeater(val diceRoll: DiceRoll): Repeater {
    override fun getAmount() = diceRoll.roll()
}

data class PercentageRepeater(val percentage: Double, val random: Random): Repeater {
    val coin = WeightedCoin(percentage, random)

    override fun getAmount() = if(coin.flip()) 1 else 0
}
