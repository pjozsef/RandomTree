package com.github.pjozsef.randomtree.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.pjozsef.factory.*
import io.kotlintest.assertSoftly
import io.kotlintest.data.suspend.forall
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FreeSpec
import io.kotlintest.tables.row
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Paths
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
            row("2 3", 2, "3"),
            row("2 :tree reference", 2, ":tree reference")
        ) { input, expectedWeight, expectedName ->
            val (actualWeight, actualName) = extractValuesFrom(input)
            assertSoftly {
                actualWeight.value shouldBe expectedWeight
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

    "compositeTree with degenerate leaf components" {
        val input = """
            root:
                c1: leaf1
                c2: leaf2
                c3: leaf3
        """.trimIndent()

        val expected = mapOf(
            "root" to c(
                mapOf(
                    "c1" to l("leaf1"),
                    "c2" to l("leaf2"),
                    "c3" to l("leaf3")
                ),
                concatCombiner
            )
        )

        val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

        actual shouldBe expected
    }

    "randomTree with backreferences to previous nodes at root" {
        val input = """
            refLeaf: {}
            refRandomTree:
                - a
                - 3 :refLeaf
            refCompositeTree:
                p1: :refRandomTree
                p2: :refLeaf
                p3:
                    - 1
                    - 2
            root:
                - :refLeaf
                - 5 :refRandomTree
                - :refCompositeTree
                - refCompositeTree
            """.trimIndent()

        val refLeaf = l("refLeaf")
        val refRandomTree = r(
            listOf(1, 3),
            listOf(l("a"), refLeaf),
            random
        )
        val refCompositeTree = c(
            mapOf(
                "p1" to refRandomTree,
                "p2" to refLeaf,
                "p3" to r(
                    listOf(1, 1),
                    listOf(l("1"), l("2")),
                    random
                )
            ),
            concatCombiner
        )
        val expected = mapOf(
            "refLeaf" to refLeaf,
            "refRandomTree" to refRandomTree,
            "refCompositeTree" to refCompositeTree,
            "root" to r(
                listOf(1, 5, 1, 1),
                listOf(refLeaf, refRandomTree, refCompositeTree, l("refCompositeTree")),
                random
            )
        )

        val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

        actual shouldBe expected
    }
    "treeCollection with inline definitions" {
        val input = """
                ^collection: 
                    - inlineLeaf
                    - inlineRandom:
                        - a
                        - b
                        - c
                    - inlineComposite:
                        first: 
                            - 0
                            - 1
                        second:
                            - 2
                            - 3
                    - ^inlineCollection:
                        - x
                        - y
                        - z
                    - 3 inlineWithRepetition
            """.trimIndent()

        val expected = mapOf(
            "collection" to coll(
                l("inlineLeaf"),
                r(
                    listOf(1, 1, 1),
                    listOf(l("a"), l("b"), l("c")),
                    random
                ),
                c(
                    mapOf(
                        "first" to r(
                            listOf(1, 1),
                            listOf(l("0"), l("1")),
                            random
                        ),
                        "second" to r(
                            listOf(1, 1),
                            listOf(l("2"), l("3")),
                            random
                        )
                    ), concatCombiner
                ),
                coll(
                    l("x"),
                    l("y"),
                    l("z")
                ),
                coll(
                    l("inlineWithRepetition"),
                    l("inlineWithRepetition"),
                    l("inlineWithRepetition")
                )
            )
        )

        val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

        actual shouldBe expected
    }
    "treeCollection with references" {
        val input = """
                referenceLeaf: {}
                referenceWithRepetition: {}
                ^collection: 
                    - :referenceLeaf
                    - 3 :referenceWithRepetition
            """.trimIndent()

        val expected = mapOf(
            "referenceLeaf" to l("referenceLeaf"),
            "referenceWithRepetition" to l("referenceWithRepetition"),
            "collection" to coll(
                l("referenceLeaf"),
                coll(
                    l("referenceWithRepetition"),
                    l("referenceWithRepetition"),
                    l("referenceWithRepetition")
                )
            )
        )

        val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

        actual shouldBe expected
    }
    "adjusts relative weight when 'relativeWeight' is enabled" {
        val input = """
            node:
                - 5 weightedArray: [a,b,c]
                - 2 anotherWeighted: [d,e]
                - 3 g
                - f
            """.trimIndent()

        val expected = mapOf(
            "node" to r(
                listOf(15, 4, 3, 1),
                listOf(
                    r(
                        listOf(1, 1, 1),
                        listOf(l("a"), l("b"), l("c")),
                        random
                    ),
                    r(
                        listOf(1, 1),
                        listOf(l("d"), l("e")),
                        random
                    ),
                    l("g"),
                    l("f")
                ),
                random
            )
        )

        val actual = readTreeFromString(
            input,
            identityMapper,
            concatCombiner,
            random,
            adjustRelativeWeight = true
        )

        actual shouldBe expected
    }

    "dice pool node" - {
        "parses dice pool leaves" {
            val input = """
                root:
                  - d4 a
                  - d6 b
            """.trimIndent()

            val expected = mapOf(
                "root" to d(
                    listOf(4, 6),
                    listOf(l("a"), l("b")),
                    random
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }

        "parses dice pool arrays" {
            val input = """
                root:
                  - d4 _: ["a","b","c"]
                  - d6 _: ["d3 x","d5 y","d7 z"]
            """.trimIndent()

            val expected = mapOf(
                "root" to d(
                    listOf(4, 6),
                    listOf(
                        r(
                            listOf(1,1,1),
                            listOf(l("a"),l("b"),l("c")),
                            random
                        ),
                        d(
                            listOf(3,5,7),
                            listOf(l("x"),l("y"),l("z")),
                            random
                        )
                    ),
                    random
                )
            )

            val actual = readTreeFromString(input, identityMapper, concatCombiner, random)

            actual shouldBe expected
        }

        "throws error if there is a mix of int weights and dice pool weights" {
            val input = """
                root:
                  - d4 a
                  - d6 b
                  - 3 c
                  - 12 d
            """.trimIndent()

            shouldThrow<Exception> {
                readTreeFromString(input, identityMapper, concatCombiner, random)
            }.message shouldBe "Dice pool and int weights are mixed in 'root'"
        }

        "throws error if not all entries have a dice value" {
            val input = """
                root:
                  - d4 a
                  - d6 b
                  - c
            """.trimIndent()

            shouldThrow<Exception> {
                readTreeFromString(input, identityMapper, concatCombiner, random)
            }.message shouldBe "Dice pool and int weights are mixed in 'root'"
        }
    }

    "readTreeFromFile" - {
        "reads the correct tree" {
            val path = Thread.currentThread()
                .contextClassLoader
                .getResource("test.yml")
                ?.toURI()
                ?.let(Paths::get)
                ?.toAbsolutePath()
                ?.toString() as String

            val expected = readTreeFromString(
                File(path).readText(),
                identityMapper,
                concatCombiner,
                random
            )
            val actual = readTreeFromFile(
                path,
                identityMapper,
                concatCombiner,
                random
            )

            actual shouldBe expected
        }
    }

    "readTreeFromJsonNode" - {
        "reads the correct tree" {
            val path = Thread.currentThread()
                .contextClassLoader
                .getResource("test.yml")
                ?.toURI()
                ?.let(Paths::get)
                ?.toAbsolutePath()
                ?.toString() as String

            val rawText = File(path).readText()

            val expected = readTreeFromString(
                rawText,
                identityMapper,
                concatCombiner,
                random
            )

            val jsonNodeInput = withContext(Dispatchers.IO) {
                val map = Yaml().load<Map<String, Any>>(rawText)
                ObjectMapper(YAMLFactory())
                    .findAndRegisterModules()
                    .convertValue(map, JsonNode::class.java)
            }

            val actual = readTreeFromJsonNode(
                jsonNodeInput,
                identityMapper,
                concatCombiner,
                random
            )

            actual shouldBe expected
        }
    }

    "readTreeFromMap" - {
        "reads the correct tree" {
            val path = Thread.currentThread()
                .contextClassLoader
                .getResource("test.yml")
                ?.toURI()
                ?.let(Paths::get)
                ?.toAbsolutePath()
                ?.toString() as String

            val rawText = File(path).readText()

            val expected = readTreeFromString(
                rawText,
                identityMapper,
                concatCombiner,
                random
            )

            val mapInput = withContext(Dispatchers.IO) {
                Yaml().load<Map<String, Any>>(rawText)
            }

            val actual = readTreeFromMap(
                mapInput,
                identityMapper,
                concatCombiner,
                random
            )

            actual shouldBe expected
        }
    }
})
