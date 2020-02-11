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
    val emptyCombiner: (Map<String, String>) -> String = { "" }
    val emptyComponentMapper: Map<String, Map<String, (String) -> String>> = mapOf()

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

            val actual = readTreeFromString(input, emptyCombiner, { it }, random, emptyComponentMapper)

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

            val actual = readTreeFromString(input, emptyCombiner, { it }, random, emptyComponentMapper)

            actual shouldBe expected
        }
        "leaf with empty body" {
            val input = """
                empty: {}
            """.trimIndent()

            val expected = mapOf(
                "empty" to l("empty")
            )

            val actual = readTreeFromString(input, emptyCombiner, { it }, random, emptyComponentMapper)

            actual shouldBe expected
        }
        "compositeNode with randomNodes" {
            val componentMappers = mapOf(
                "composite" to mapOf(
                    "part1" to { it: String -> it+"_part1" },
                    "part2" to { it: String -> it+"_part2" }
                )
            )
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
                            listOf(l("a_part1"), l("b_part1")),
                            random
                        ),
                        "part2" to r(
                            listOf(1, 1, 1),
                            listOf(l("1_part2"), l("2_part2"), l("3_part2")),
                            random
                        )
                    ),
                    emptyCombiner
                )
            )

            val actual = readTreeFromString(
                input,
                emptyCombiner,
                { it },
                random,
                componentMappers
            )

            actual shouldBe expected
        }
        "compositeNode with randomNodes does not split name by weight" {
            val componentMappers = mapOf(
                "composite" to mapOf(
                    "1_part" to { it: String -> it+"_from_1_part" },
                    "2_part" to { it: String -> it+"_from_2_part" }
                )
            )
            val input = """
                composite:
                  1_part:
                    - a
                    - b
                  2_part:
                    - 1
                    - 2
                    - 3
            """.trimIndent()

            val expected = mapOf(
                "composite" to c(
                    mapOf(
                        "1_part" to r(
                            listOf(1, 1),
                            listOf(l("a_from_1_part"), l("b_from_1_part")),
                            random
                        ),
                        "2_part" to r(
                            listOf(1, 1, 1),
                            listOf(l("1_from_2_part"), l("2_from_2_part"), l("3_from_2_part")),
                            random
                        )
                    ),
                    emptyCombiner
                )
            )

            val actual = readTreeFromString(
                input,
                emptyCombiner,
                { it },
                random,
                componentMappers
            )

            actual shouldBe expected
        }
        "randomTree with deeply nested branches" {
            val componentMappers = mapOf(
                "composite" to mapOf(
                    "part1" to { it: String -> it },
                    "part2" to { it: String -> it }
                )
            )
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
                    listOf(3,1,4, 1),
                    listOf(
                        r(
                            listOf(1,1,1),
                            listOf(l("a"),l("aa"),l("aaa")),
                            random
                        ),
                        r(
                            listOf(1,5),
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
                            emptyCombiner
                        ),
                        l("leafValue")
                    ),
                    random
                )
            )

            val actual = readTreeFromString(
                input,
                emptyCombiner,
                { it },
                random,
                componentMappers
            )

            actual shouldBe expected
        }
    }
})
