package com.github.pjozsef.randomtree

import com.github.pjozsef.WeightedDie
import com.github.pjozsef.factory.*
import io.kotlintest.data.suspend.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import io.kotlintest.tables.row
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
            ),
            row(
                d(
                    listOf(4, 6, 10),
                    listOf(
                        l("1"),
                        l("2"),
                        l("3")
                    ),
                    random()
                ),
                "3"
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

    "dicePoolNode" - {
        val random = mock<Random>()
        val node = d(
            listOf(4, 6, 10),
            listOf(
                l("1"),
                l("2"),
                l("3")
            ),
            random
        )
        "chooses branch that rolled highest" {
            whenever(random.nextInt(4)).thenReturn(3)
            whenever(random.nextInt(6)).thenReturn(6)
            whenever(random.nextInt(10)).thenReturn(1)

            node.value shouldBe "2"
        }

        "chooses higher die type when results are equal" {
            whenever(random.nextInt(4)).thenReturn(4)
            whenever(random.nextInt(6)).thenReturn(3)
            whenever(random.nextInt(10)).thenReturn(4)

            node.value shouldBe "3"
        }
    }

    "repeaterNode" - {

        forall(
            row("calls subnode n times", 3, listOf("value", "value", "value")),
            row("returns empty list if repeat amount is 0", 0, emptyList()),
        ) { test, times, expected ->
            test{
                val repeater = mock<Repeater> {
                    on { getAmount() } doReturn times
                }
                val repeaterNode = RepeaterNode(
                    repeater, LeafNode("value")
                )

                repeaterNode.values shouldBe expected
            }
        }
    }
})

