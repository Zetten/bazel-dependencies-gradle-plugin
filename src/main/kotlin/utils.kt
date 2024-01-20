package com.github.zetten.bazeldeps

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.io.IOException

fun Boolean.toStarlark() = toString().uppercaseFirstChar()

fun indentedStrings(
    list: List<String>,
    indent: Int = 2,
    separator: String = ",",
    prefix: String = "\n" + "    ".repeat(indent + 1),
    postfix: String = "    ".repeat(indent),
    sorted: Boolean = true,
): String = when {
    list.isEmpty() -> ""
    list.size == 1 -> list[0]
    else -> (if (sorted) list.sorted() else list)
        .joinToString(separator = "    ".repeat(indent + 1), prefix = prefix, postfix = postfix) { s ->
            "$s$separator\n"
        }
}

val objectMapper: JsonMapper = DefaultIndenter().let { indenter ->
    JsonMapper.builder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .addModule(KotlinModule.Builder().build())
        .defaultPrettyPrinter(
            MavenInstallPrettyPrinter()
                .withArrayIndenter(indenter)
                .withObjectIndenter(indenter)
        )
        .build()
}

internal class MavenInstallPrettyPrinter : DefaultPrettyPrinter() {
    init {
        _arrayIndenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE
    }

    override fun createInstance(): DefaultPrettyPrinter {
        return MavenInstallPrettyPrinter()
    }

    override fun writeObjectFieldValueSeparator(g: JsonGenerator) {
        g.writeRaw(": ")
    }

    override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
        if (!_objectIndenter.isInline) {
            --_nesting
        }
        if (nrOfEntries > 0) {
            _objectIndenter.writeIndentation(g, _nesting)
        }
        g.writeRaw('}')
    }

    override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
        if (!_arrayIndenter.isInline) {
            --_nesting
        }
        if (nrOfValues > 0) {
            _arrayIndenter.writeIndentation(g, _nesting)
        }
        g.writeRaw(']')
    }
}
