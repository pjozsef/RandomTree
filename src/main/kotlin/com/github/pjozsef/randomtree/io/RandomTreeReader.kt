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
    random: Random = Random()
): Map<String, RandomTree<T>> = readTreeFromString(
    File(path).readText(),
    mapper,
    combiner,
    random
)

fun <T> readTreeFromString(
    inputString: String,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random()
): Map<String, RandomTree<T>> = readTreeFromMap(
    snakeYml.load(inputString),
    mapper,
    combiner,
    random
)

fun <T> readTreeFromMap(
    inputMap: Map<String, Any>,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random()
): Map<String, RandomTree<T>> = readTreeFromJsonNode(
    objectMapper.convertValue(inputMap, JsonNode::class.java),
    mapper,
    combiner,
    random
)

fun <T> readTreeFromJsonNode(
    root: JsonNode,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random()
): Map<String, RandomTree<T>> {
    val container = mutableMapOf<String, RandomTree<T>>()
    root.fields().asSequence().forEach { (key, value) ->
        when (value) {
            is ArrayNode -> container[key.dropSpecial()] = readArray(key, value, mapper, combiner, random, container)
            is ObjectNode -> if (value.isEmpty) {
                container[key] = readEmptyNode(key, mapper)
            } else {
                container[key] = readCompositeNode(
                    value,
                    mapper,
                    combiner,
                    random,
                    container
                )
            }
            else -> error("Unsupported type for root: ${value::class.java}")
        }
    }
    return container.toMap()
}

private fun <T> readArray(
    arrayName: String,
    array: ArrayNode,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random,
    container: Map<String, RandomTree<T>>
): RandomTree<T> =
    array.elements().asSequence().map { elementValue ->
        when (elementValue) {
            is ValueNode -> {
                val (weight, name) = elementValue.text().let(::extractValuesFrom)
                val node = extractLeafOrReference(name, mapper, container)
                weight to node
            }
            is ObjectNode -> {
                val (nestedKey, nestedValue) = elementValue.fields().asSequence().toList().first()
                val (nestedWeight, name) = extractValuesFrom(nestedKey)
                when (nestedValue) {
                    is ArrayNode -> {
                        val randomNode = readArray(
                            name,
                            nestedValue,
                            mapper,
                            combiner,
                            random,
                            container
                        )
                        nestedWeight to randomNode
                    }
                    is ObjectNode -> {
                        val composite = readCompositeNode(
                            nestedValue,
                            mapper,
                            combiner,
                            random,
                            container
                        )
                        nestedWeight to composite
                    }
                    else -> error("Unsopported type for CompositeNode inside RandomNode: ${nestedValue::class.java}")
                }
            }
            else -> error("Unsupported type for RandomNode: ${elementValue::class.java}")
        }
    }.toList().let {
        if (arrayName.startsWith("^")) {
            it.map { (times, node) ->
                if (times == 1) {
                    node
                } else {
                    (1..times.toInt()).map { node }.let(::TreeCollection)
                }
            }.let(::TreeCollection)
        } else {
            val (weights, nodes) = it.unzip()
            RandomNode(weights, nodes, random)
        }
    }

private fun <T> readCompositeNode(
    value: ObjectNode,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random,
    container: Map<String, RandomTree<T>>
): RandomTree<T> =
    value.fields().asSequence().map { (elementKey, elementValue) ->
        when (elementValue) {
            is ArrayNode -> elementKey to readArray(
                elementKey,
                elementValue,
                mapper,
                combiner,
                random,
                container
            )
            is ObjectNode -> elementKey to readCompositeNode(
                elementValue,
                mapper,
                combiner,
                random,
                container
            )
            is TextNode -> elementKey to extractLeafOrReference(elementValue.textValue(), mapper, container)
            else -> error("Unsupported type for CompositeNode: ${elementValue::class.java}")
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

private fun ValueNode.text() = when (this) {
    is TextNode -> this.textValue()
    is NumericNode -> this.numberValue().toString()
    is BooleanNode -> this.booleanValue().toString()
    else -> error("Unsupported type for LeafNode: ${this::class.java}")
}

internal fun extractValuesFrom(text: String): Pair<Number, String> {
    val trimmedText = text.trim()
    return weightNameRegex.matchEntire(trimmedText)?.let {
        val weight = it.groups.get("weight")?.value?.toInt() ?: error("Regex did not match Node text: $text")
        val name = it.groups.get("name")?.value ?: error("Regex did not match Node text: $text")

        weight to name
    } ?: nameRegex.matchEntire(trimmedText)?.let {
        val name = it.groups.get("name")?.value ?: error("Regex did not match Node text: $text")

        1 to name
    } ?: error("Regex did not match Node text: $text")
}

private fun String.dropSpecial() = this.replace("^", "")

private val weightNameRegex by lazy {
    Regex("(?<weight>[1-9][0-9]*) +(?<name>.+)")
}

private val nameRegex by lazy {
    Regex("(?<name>.+)")
}
