package com.dfcruz.compose.gallery.gradle

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
internal fun JsonObject.bool(k: String): Boolean =
    (this[k] as? JsonPrimitive)?.booleanOrNull ?: false

internal fun JsonObject.float(k: String): Float? =
    (this[k] as? JsonPrimitive)?.content?.toFloatOrNull()

internal fun JsonObject.arr(k: String): JsonArray = this[k] as? JsonArray ?: JsonArray(emptyList())

fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull