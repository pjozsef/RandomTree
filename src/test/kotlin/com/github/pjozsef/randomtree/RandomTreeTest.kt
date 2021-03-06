package com.github.pjozsef.randomtree

import com.github.pjozsef.WeightedDie
import com.github.pjozsef.factory.c
import com.github.pjozsef.factory.coll
import com.github.pjozsef.factory.l
import com.github.pjozsef.factory.r
import io.kotlintest.data.suspend.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import io.kotlintest.tables.row
import java.util.*

class RandomTreeTest : FreeSpec({
    val random = { Random(1000L) }
    "value calculated for each type" {

        forall(
            row(l("value"), "value"),
            row(
                c(
                    sortedMapOf(
                        "1" to l("a"),
                        "2" to l("b"),
                        "3" to l("c")
                    )
                ) { it.map { (k, v) -> "$k=$v" }.joinToString(", ") }, "1=a, 2=b, 3=c"
            ),
            row(
                r(
                    listOf(1, 2, 3),
                    listOf(
                        l("1"),
                        l("2"),
                        l("3")
                    ),
                    random()
                ), "2"
            ),
            row(
                coll(
                    l("value1"),
                    l("value2"),
                    l("value3")
                ), "value1"
            )
        ) { tree: RandomTree<*>, expectedValue: String ->
            tree.value shouldBe expectedValue
        }
    }

    "randomNode chooses branch correctly" {
        val node = r(
            listOf(1, 2, 3),
            listOf(
                l("1"),
                l("2"),
                l("3")
            ),
            random()
        )
        val randomDie = WeightedDie(
            mapOf(
                "1" to 1,
                "2" to 2,
                "3" to 3
            ), random()
        )

        val steps = 1_000_000

        val expectedValues = (1..steps).map { randomDie.roll() }
        val actualValues = (1..steps).map { node.value }

        actualValues shouldBe expectedValues
    }

    "treeCollection" - {
        "returns all values" {
            coll(
                l("value1"),
                l("value2"),
                l("value3")
            ).values shouldBe listOf("value1", "value2", "value3")
        }
        "flattens nested values" {
            coll(
                l("value1"),
                coll(
                    l("a"),
                    l("b"),
                    coll(
                        l("x"),
                        l("y")
                    )
                ),
                l("value2"),
                l("value3")
            ).values shouldBe listOf("value1", "a", "b", "x", "y", "value2", "value3")
        }
    }
})

