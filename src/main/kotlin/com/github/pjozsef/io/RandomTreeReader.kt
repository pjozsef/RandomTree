package com.github.pjozsef.io

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.*
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.pjozsef.CompositeNode
import com.github.pjozsef.Leaf
import com.github.pjozsef.RandomNode
import com.github.pjozsef.RandomTree
import java.util.*

private val objectMapper by lazy {
    ObjectMapper(YAMLFactory()).findAndRegisterModules()
}

fun <T> readTreeFromString(
    input: String,
    mapper: (String) -> T,
    combiner: (Map<String, T>) -> T,
    random: Random = Random()
): Map<String, RandomTree<T>> {
    val root = objectMapper.readTree(input)
    val container = mutableMapOf<String, RandomTree<T>>()
    root.fields().asSequence().forEach { (key, value) ->
        when (value) {
            is ArrayNode -> container[key] = readRandomNode(value, mapper, combiner, random, container)
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

private fun <T> readRandomNode(
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
                val (nestedWeight, _) = extractValuesFrom(nestedKey)
                when (nestedValue) {
                    is ArrayNode -> {
                        val randomNode = readRandomNode(
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
        val (weights, nodes) = it.unzip()
        RandomNode(weights, nodes, random)
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
            is ArrayNode -> elementKey to readRandomNode(
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
): RandomTree<T> = Leaf(mapper(key))

private fun <T> extractLeafOrReference(
    key: String,
    mapper: (String) -> T,
    container: Map<String, RandomTree<T>>
): RandomTree<T> = if (key.startsWith(":")) {
    container.getValue(key.drop(1))
} else {
    Leaf(mapper(key))
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
        val weight = it.groups.get("weight")?.value?.toInt() ?: error("Regex did not match LeafNode text: $text")
        val name = it.groups.get("name")?.value ?: error("Regex did not match LeafNode text: $text")

        weight to name
    } ?: nameRegex.matchEntire(trimmedText)?.let {
        val name = it.groups.get("name")?.value ?: error("Regex did not match LeafNode text: $text")

        1 to name
    } ?: error("Regex did not match LeafNode text: $text")
}

private val weightNameRegex by lazy {
    Regex("(?<weight>[1-9][0-9]*) +(?<name>.+)")
}

private val nameRegex by lazy {
    Regex("(?<name>.+)")
}
