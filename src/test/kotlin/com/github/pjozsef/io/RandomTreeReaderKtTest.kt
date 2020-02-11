package com.github.pjozsef.io

import com.github.pjozsef.factory.c
import com.github.pjozsef.factory.l
import com.github.pjozsef.factory.r
import io.kotlintest.assertSoftly
import io.kotlintest.data.suspend.forall
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import io.kotlintest.tables.row
import java.util.*

class RandomTreeReaderKtTest : FreeSpec({

    val random = Random()
    val identityMapper: (String) -> String = { it }
    val concatCombiner: (Map<String, String>) -> String = {
        it.toSortedMap().values.joinToString("_")
    }

    "weightRegex" {
        forall(
            row("asdf", 1, "asdf"),
            row("asdf   ", 1, "asdf"),
            row("   asdf", 1, "asdf"),
            row("text with spaces", 1, "text with spaces"),
            row("5 text with spaces", 5, "text with spaces"),
            row("10 asdf", 10, "asdf"),
            row("10      asdf", 10, "asdf"),
            row("10", 1, "10"),
            row("10      ", 1, "10"),
            row("     10      ", 1, "10"),
            row("2 3", 2, "3")
        ) { input, expectedWeight, expectedName ->
            val (actualWeight, actualName) = extractValuesFrom(input)
            assertSoftly {
                actualWeight shouldBe expectedWeight
                actualName shouldBe expectedName
            }
        }
    }
    "readTreeFromString" - {
        "randomTree of Leaf nodes" {
            val input = """
                root:
                    - leaf1
                    - 10
                    - false
                    - leaf with spaces
            """.trimIndent()

            val expected = mapOf(
                "root" to r(
                    listOf(1, 1, 1, 1),
                    listOf(l("leaf1"), l("10"), l("false"), l("leaf with spaces")),
                    random
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }
        "randomTree of Leaf nodes with custom weights" {
            val input = """
                root:
                    - 3 leaf1
                    - leaf2
                    - 10000 leaf with spaces
            """.trimIndent()

            val expected = mapOf(
                "root" to r(
                    listOf(3, 1, 10_000),
                    listOf(l("leaf1"), l("leaf2"), l("leaf with spaces")),
                    random
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }
        "leaf with empty body" {
            val input = """
                empty: {}
            """.trimIndent()

            val expected = mapOf(
                "empty" to l("empty")
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }
        "compositeNode with randomNodes" {
            val input = """
                composite:
                  part1:
                    - a
                    - b
                  part2:
                    - 1
                    - 2
                    - 3
            """.trimIndent()

            val expected = mapOf(
                "composite" to c(
                    mapOf(
                        "part1" to r(
                            listOf(1, 1),
                            listOf(l("a"), l("b")),
                            random
                        ),
                        "part2" to r(
                            listOf(1, 1, 1),
                            listOf(l("1"), l("2"), l("3")),
                            random
                        )
                    ),
                    concatCombiner
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }
        "compositeNode with randomNodes does not split name by weight" {
            val input = """
                composite:
                  1 part:
                    - a
                    - b
                  2 part:
                    - 1
                    - 2
                    - 3
            """.trimIndent()

            val expected = mapOf(
                "composite" to c(
                    mapOf(
                        "1 part" to r(
                            listOf(1, 1),
                            listOf(l("a"), l("b")),
                            random
                        ),
                        "2 part" to r(
                            listOf(1, 1, 1),
                            listOf(l("1"), l("2"), l("3")),
                            random
                        )
                    ),
                    concatCombiner
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }
        "randomTree with deeply nested branches" {
            val input = """
                root:
                    - 3 a:
                        - a
                        - aa
                        - aaa
                    - b:
                        - b
                        - 5 bb
                    - 4 composite:
                        part1:
                            - a
                            - b
                        part2:
                            - 1
                            - 2
                    - leafValue
            """.trimIndent()

            val expected = mapOf(
                "root" to r(
                    listOf(3, 1, 4, 1),
                    listOf(
                        r(
                            listOf(1, 1, 1),
                            listOf(l("a"), l("aa"), l("aaa")),
                            random
                        ),
                        r(
                            listOf(1, 5),
                            listOf(l("b"), l("bb")),
                            random
                        ),
                        c(
                            mapOf(
                                "part1" to r(
                                    listOf(1, 1),
                                    listOf(l("a"), l("b")),
                                    random
                                ),
                                "part2" to r(
                                    listOf(1, 1),
                                    listOf(l("1"), l("2")),
                                    random
                                )
                            ),
                            concatCombiner
                        ),
                        l("leafValue")
                    ),
                    random
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }
    }
    "compositeTree with deeply nested branches" {
        val input = """
                compositeRoot:
                    innerComposite:
                        first:
                            - x
                            - 3 y
                        second:
                            - z
                            - w
                    innerBranch:
                        - 2 value1
                        - value2
            """.trimIndent()

        val expected = mapOf(
            "compositeRoot" to c(
                mapOf(
                    "innerComposite" to c(
                        mapOf(
                            "first" to r(
                                listOf(1, 3),
                                listOf(l("x"), l("y")),
                                random
                            ),
                            "second" to r(
                                listOf(1, 1),
                                listOf(l("z"), l("w")),
                                random
                            )
                        ),
                        concatCombiner
                    ),
                    "innerBranch" to r(
                        listOf(2, 1),
                        listOf(l("value1"), l("value2")),
                        random
                    )
                ),
                concatCombiner
            )
        )

        val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

        actual shouldBe expected
    }
})
