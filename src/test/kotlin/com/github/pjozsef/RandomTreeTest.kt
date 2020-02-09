package com.github.pjozsef

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
                    listOf(
                        l("a"),
                        l("b"),
                        l("c")
                    )
                ) { it.joinToString(" ") }, "a b c"
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
})

private fun <T> l(value: T) = Leaf(value)

private fun <C, T> c(components: List<RandomTree<C>>, combiner: (List<C>) -> T) =
    CompositeNode(components, combiner)

private fun <T> r(weights: List<Number>, branches: List<RandomTree<T>>, random: Random) =
    RandomNode(weights, branches, random)
