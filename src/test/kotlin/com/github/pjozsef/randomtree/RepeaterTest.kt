package com.github.pjozsef.randomtree

import com.github.pjozsef.DiceRoll
import io.kotlintest.data.suspend.forall
import io.kotlintest.inspectors.forAll
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.numerics.shouldBeInRange
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
                on{ roll() } doReturn expectedResult
            }
            val repeater = DiceRollRepeater(diceRoll)
            repeater.getAmount() shouldBe expectedResult
        }
    }
})

