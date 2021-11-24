package com.github.pjozsef.randomtree

import com.github.pjozsef.DiceRoll
import io.kotlintest.data.suspend.forall
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import io.kotlintest.tables.row
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class RepeaterTest : FreeSpec({
    "constant repeater" - {
        "returns the same constant value" {
            ConstantRepeater(4).getAmount() shouldBe 4
        }
    }

    "interval repeater" - {
        forall(
            row("returns values between 0 and 10", 0..10, 11, 5, 5),
            row("returns values between 5 and 10", 5..10, 6, 2, 7),
        ) { test, range, upperBound, randomValue, expected ->
            test {
                val random = mock<Random>()
                whenever(random.nextInt(upperBound)).thenReturn(randomValue)

                RangeRepeater(range, random).getAmount() shouldBe expected
            }
        }

        "should contain all values from range" {
            val range = 3..6
            val repeater = RangeRepeater(range, Random())
            val results = (1..10_000).map { repeater.getAmount() }.distinct()
            results shouldContainExactlyInAnyOrder range.toList()
        }
    }

    "dice roll repeater" - {
        "returns result of dice roll" {
            val expectedResult = 7
            val diceRoll = mock<DiceRoll> {
                on { roll() } doReturn expectedResult
            }
            val repeater = DiceRollRepeater(diceRoll)
            repeater.getAmount() shouldBe expectedResult
        }
    }

    "percentage repeater" - {
        forall(
            row("returns one if coinflip passes", 0.7, 0.3, 1),
            row("returns one if coinflip passes at boundary", 0.7, 0.7, 1),
            row("returns zero if coinflip fails close to boundary", 0.6, 0.6001, 0),
            row("returns zero if coinflip fails", 0.9, 0.99, 0),
        ) { test, percentage, randomRoll, expected ->
            test {
                val random = mock<Random> {
                    on { nextDouble() } doReturn randomRoll
                }
                PercentageRepeater(percentage, random).getAmount() shouldBe expected
            }
        }
    }
})

