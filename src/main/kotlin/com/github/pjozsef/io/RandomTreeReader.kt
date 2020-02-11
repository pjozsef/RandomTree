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

fun <C, T> readTreeFromString(
    input: String,
    combiner: (Map<String, C>) -> T,
    mapper: (String) -> T,
    random: Random = Random(),
    componentMappers: Map<String, Map<String, (String) -> C>> = mapOf()
): Map<String, RandomTree<T>> {
    val root = objectMapper.readTree(input)
    return root.fields().asSequence().map { (key, value) ->
        when (value) {
            is ArrayNode -> key mappedTo readRandomNode(value, mapper, combiner, componentMappers, random)
            is ObjectNode -> if (value.isEmpty) {
                key mappedTo readEmptyNode(key, mapper)
            } else {
                key mappedTo readCompositeNode(value, mapper, combiner, componentMappers.getValue(key), componentMappers, random)
            }
            else -> error("Unsupported type for root: ${value::class.java}")
        }
    }.reduce { acc, current ->
        acc + current
    }
}

private fun <C, T> readRandomNode(
    array: ArrayNode,
    mapper: (String) -> T,
    combiner: (Map<String, C>) -> T,
    componentMappers: Map<String, Map<String, (String) -> C>> = mapOf(),
    random: Random
): RandomTree<T> =
    array.elements().asSequence().map { elementValue ->
        when (elementValue) {
            is ValueNode -> readLeaf(elementValue, mapper)
            is ObjectNode -> {
                val (nestedKey, nestedValue) = elementValue.fields().asSequence().toList().first()
                val (nestedWeight, nestedName) = extractValuesFrom(nestedKey)
                when(nestedValue){
                    is ArrayNode -> {
                        val randomNode = readRandomNode(
                            nestedValue,
                            mapper,
                            combiner,
                            componentMappers,
                            random
                        )
                        nestedWeight to randomNode
                    }
                    is ObjectNode -> {
                        val composite = readCompositeNode(
                            nestedValue,
                            mapper,
                            combiner,
                            componentMappers.getValue(nestedName),
                            componentMappers,
                            random
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

private fun <C, T> readCompositeNode(
    value: ObjectNode,
    mapper: (String) -> T,
    combiner: (Map<String, C>) -> T,
    componentMapper: Map<String, (String) -> C>,
    componentMappers: Map<String, Map<String, (String) -> C>> = mapOf(),
    random: Random
): RandomTree<T> =
    value.fields().asSequence().map { (elementKey, elementValue) ->
        when (elementValue) {
            is ArrayNode -> elementKey to readRandomNode(elementValue, componentMapper.getValue(elementKey), combiner, componentMappers, random) as RandomTree<C>
            else -> error("Unsupported type for CompositeNode: ${elementValue::class.java}")
        }
    }.toMap().let {
        CompositeNode(it, combiner)
    }

private fun <T> readLeaf(valueNode: ValueNode, mapper: (String) -> T): WeightedRandomTree<T> {
    val text = when (valueNode) {
        is TextNode -> valueNode.textValue()
        is NumericNode -> valueNode.numberValue().toString()
        is BooleanNode -> valueNode.booleanValue().toString()
        else -> error("Unsupported type for LeafNode: ${valueNode::class.java}")
    }
    val (weight, name) = extractValuesFrom(text)

    return weight to Leaf(mapper(name))
}

private fun <T> readEmptyNode(
    key: String,
    mapper: (String) -> T
) = Leaf(mapper(key))

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

private typealias WeightedRandomTree<T> = Pair<Number, RandomTree<T>>

private infix fun <V> String.mappedTo(that: V) = mapOf(this to that)

private val weightNameRegex by lazy {
    Regex("(?<weight>[1-9][0-9]*) +(?<name>.+)")
}

private val nameRegex by lazy {
    Regex("(?<name>.+)")
}
