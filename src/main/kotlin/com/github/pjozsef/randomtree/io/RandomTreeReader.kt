package com.github.pjozsef.randomtree.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.*
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.pjozsef.randomtree.*
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.*

private val objectMapper by lazy {
    ObjectMapper(YAMLFactory()).findAndRegisterModules()
}

private val snakeYml by lazy {
    Yaml()
}

object RandomTreeReader {
    val IDENTITY_MAPPER: (String) -> String = { it }
    val CONCAT_COMBINER: (String) -> ((Map<String, String>) -> String) = { joinString ->
        { it -> it.toSortedMap().values.joinToString(joinString) }
    }
}

fun <T> readTreeFromFile(
    path: String,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random(),
    adjustRelativeWeight: Boolean = false,
): Map<String, RandomTree<T>> = readTreeFromString(
    File(path).readText(),
    mapper,
    combiner,
    random,
    adjustRelativeWeight
)

fun <T> readTreeFromString(
    inputString: String,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random(),
    adjustRelativeWeight: Boolean = false,
): Map<String, RandomTree<T>> = readTreeFromMap(
    snakeYml.load(inputString),
    mapper,
    combiner,
    random,
    adjustRelativeWeight
)

fun <T> readTreeFromMap(
    inputMap: Map<String, Any>,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random(),
    adjustRelativeWeight: Boolean = false,
): Map<String, RandomTree<T>> = readTreeFromJsonNode(
    objectMapper.convertValue(inputMap, JsonNode::class.java),
    mapper,
    combiner,
    random,
    adjustRelativeWeight
)

fun <T> readTreeFromJsonNode(
    root: JsonNode,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random(),
    adjustRelativeWeight: Boolean = false
): Map<String, RandomTree<T>> {
    val container = mutableMapOf<String, RandomTree<T>>()
    root.fields().asSequence().forEach { (key, value) ->
        when (value) {
            is ArrayNode -> container[key.dropSpecial()] =
                readArray(key, key, value, mapper, combiner, random, container, adjustRelativeWeight)
            is ObjectNode -> if (value.isEmpty) {
                container[key] = readEmptyNode(key, mapper)
            } else {
                container[key] = readCompositeNode(
                    key,
                    key,
                    value,
                    mapper,
                    combiner,
                    random,
                    container,
                    adjustRelativeWeight
                )
            }
            is TextNode -> container[key] =  extractLeafOrReference(value.textValue(), mapper, container)
            else -> error("Unsupported type for root: ${value.className} at: $key")
        }
    }
    return container.toMap()
}

private fun <T> readArray(
    arrayName: String,
    path: String,
    array: ArrayNode,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random,
    container: Map<String, RandomTree<T>>,
    adjustRelativeWeight: Boolean
): RandomTree<T> =
    array.elements().asSequence().mapIndexed { i, elementValue ->
        val arrayIndexName = "$path[${i+1}]"
        when (elementValue) {
            is ValueNode -> {
                val (weight, name) = elementValue.text(arrayIndexName).let(::extractValuesFrom)
                val node = extractLeafOrReference(name, mapper, container)
                weight to node
            }
            is ArrayNode -> DEFAULT_WEIGHT to readArray(
                "",
                arrayIndexName,
                elementValue,
                mapper,
                combiner,
                random,
                container,
                adjustRelativeWeight
            )
            is ObjectNode -> {
                val (nestedKey, nestedValue) = elementValue.fields().asSequence().toList().first()
                val (nestedWeight, name) = extractValuesFrom(nestedKey)
                when (nestedValue) {
                    is ArrayNode -> {
                        val randomNode = readArray(
                            name,
                            "$arrayIndexName>$name",
                            nestedValue,
                            mapper,
                            combiner,
                            random,
                            container,
                            adjustRelativeWeight
                        )
                        when (nestedWeight) {
                            is IntWeight -> {
                                val multiplier =
                                    if (adjustRelativeWeight) {
                                        nestedValue.elements().asSequence().toList().size
                                    } else 1
                                IntWeight(nestedWeight.value * multiplier) to randomNode
                            }
                            is DicePoolWeight -> nestedWeight to randomNode
                        }
                    }
                    is ObjectNode -> {
                        val composite = readCompositeNode(
                            name,
                            "$arrayIndexName>$name",
                            nestedValue,
                            mapper,
                            combiner,
                            random,
                            container,
                            adjustRelativeWeight
                        )
                        nestedWeight to composite
                    }
                    else -> error("Unsopported type for CompositeNode inside RandomNode: ${nestedValue.className} at: $arrayIndexName>$name")
                }
            }
            else -> error("Unsupported type for RandomNode: ${elementValue::class.java} at: $arrayIndexName")
        }
    }.toList().let {
        if (arrayName.startsWith("^")) {
            it.map { (times, node) ->
                when (times) {
                    DEFAULT_WEIGHT -> node
                    is DicePoolWeight -> node
                    is IntWeight -> (1..times.value).map { node }.let(::TreeCollection)
                }
            }.let(::TreeCollection)
        } else {
            val (weights, nodes) = it.unzip()
            validateWeights(weights, arrayName)
            val numericWeights = weights.map { it.value }
            when (weights.first()) {
                is IntWeight -> RandomNode(numericWeights, nodes, random)
                is DicePoolWeight -> DicePoolNode(numericWeights, nodes, random)
            }

        }
    }

private fun <T> readCompositeNode(
    key: String,
    path: String,
    value: ObjectNode,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random,
    container: Map<String, RandomTree<T>>,
    adjustRelativeWeight: Boolean
): RandomTree<T> =
    value.fields().asSequence().map { (elementKey, elementValue) ->
        when (elementValue) {
            is ArrayNode -> elementKey to readArray(
                elementKey,
                "$path>$elementKey",
                elementValue,
                mapper,
                combiner,
                random,
                container,
                adjustRelativeWeight
            )
            is ObjectNode -> elementKey to readCompositeNode(
                elementKey,
                "$path>$elementKey",
                elementValue,
                mapper,
                combiner,
                random,
                container,
                adjustRelativeWeight
            )
            is TextNode -> elementKey to extractLeafOrReference(elementValue.textValue(), mapper, container)
            else -> error("Unsupported type for CompositeNode: ${elementValue.className} at: $path>$elementKey")
        }
    }.toMap().let {
        CompositeNode(it, combiner)
    }

private fun <T> readEmptyNode(
    key: String,
    mapper: (String) -> T
): RandomTree<T> = LeafNode(mapper(key))

private fun <T> extractLeafOrReference(
    key: String,
    mapper: (String) -> T,
    container: Map<String, RandomTree<T>>
): RandomTree<T> = if (key.startsWith(":")) {
    container.getValue(key.drop(1))
} else {
    LeafNode(mapper(key))
}

private fun ValueNode.text(path: String) = when (this) {
    is TextNode -> this.textValue()
    is NumericNode -> this.numberValue().toString()
    is BooleanNode -> this.booleanValue().toString()
    else -> error("Unsupported type for LeafNode: ${this.className} at: $path")
}

internal sealed interface Weight {
    val value: Int
}

@JvmInline
private value class IntWeight(override val value: Int) : Weight

@JvmInline
private value class DicePoolWeight(override val value: Int) : Weight

internal fun extractValuesFrom(text: String): Pair<Weight, String> {
    val trimmedText = text.trim()
    return intWeightNameRegex.matchEntire(trimmedText)?.toWeightNamePair(
        text,
        ::IntWeight
    ) ?: diceWeightNameRegex.matchEntire(trimmedText)?.toWeightNamePair(
        text,
        ::DicePoolWeight
    ) ?: nameRegex.matchEntire(trimmedText)?.let {
        val name = it.groups.get("name")?.value ?: error("Regex did not match Node text: $text")

        DEFAULT_WEIGHT to name
    } ?: error("Regex did not match Node text: $text")
}

private fun MatchResult.toWeightNamePair(text: String, weightFn: (Int) -> Weight): Pair<Weight, String> {
    val weight = this.groups.get("weight")?.value?.toInt() ?: error("Regex did not match Node text: $text")
    val name = this.groups.get("name")?.value ?: error("Regex did not match Node text: $text")

    return weightFn(weight) to name
}

private fun String.dropSpecial() = this.replace("^", "")

private val intWeightNameRegex by lazy {
    Regex("(?<weight>[1-9][0-9]*) +(?<name>.+)")
}

private val diceWeightNameRegex by lazy {
    Regex("d(?<weight>[1-9][0-9]*) +(?<name>.+)")
}

private val nameRegex by lazy {
    Regex("(?<name>.+)")
}

private val DEFAULT_WEIGHT = IntWeight(1)

private fun validateWeights(weights: List<Weight>, arrayName: String) {
    val allInt = weights.all { it is IntWeight }
    val allDice = weights.all { it is DicePoolWeight }

    if (!(allInt || allDice)) {
        error("Dice pool and int weights are mixed at: $arrayName")
    }
}

private val Any.className: String
    get() = this::class.java.simpleName
