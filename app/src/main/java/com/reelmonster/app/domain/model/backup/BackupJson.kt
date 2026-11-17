package com.reelmonster.app.domain.model.backup

/**
 * Phase 15 — Minimal JSON model for offline backups (proposal §8).
 *
 * The project has no JSON serialization dependency, and backup files
 * must be readable and verifiable without one. This is a small,
 * strict, dependency-free JSON implementation in pure Kotlin, so the
 * validator, planner, and journal are all testable with plain JUnit.
 *
 * Canonical form (used for checksums): object keys sorted
 * lexicographically, no whitespace, numbers normalized (integral
 * values as plain integers, non-integral via [Double.toString]).
 * The parser is strict: duplicate object keys, trailing data,
 * unterminated strings, invalid numbers, and nesting deeper than
 * [MAX_DEPTH] are all rejected.
 */
sealed interface JsonValue

data class JsonObj(val entries: Map<String, JsonValue>) : JsonValue
data class JsonArr(val items: List<JsonValue>) : JsonValue
data class JsonStr(val value: String) : JsonValue
data class JsonInt(val value: Long) : JsonValue
data class JsonDouble(val value: Double) : JsonValue
data class JsonBool(val value: Boolean) : JsonValue
data object JsonNull : JsonValue

fun JsonValue.asObj(): JsonObj? = this as? JsonObj
fun JsonValue.asArr(): JsonArr? = this as? JsonArr
fun JsonValue.asStr(): String? = (this as? JsonStr)?.value
fun JsonValue.asLong(): Long? = when (this) {
    is JsonInt -> value
    is JsonDouble -> if (value % 1.0 == 0.0 && value in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
        value.toLong()
    } else {
        null
    }
    else -> null
}
fun JsonValue.asBool(): Boolean? = (this as? JsonBool)?.value

object BackupJson {
    const val MAX_DEPTH = 32

    /** Serializes [value] in canonical form (sorted keys, no whitespace). */
    fun stringify(value: JsonValue): String {
        val sb = StringBuilder()
        write(value, sb)
        return sb.toString()
    }

    private fun write(value: JsonValue, sb: StringBuilder) {
        when (value) {
            is JsonObj -> {
                sb.append('{')
                var first = true
                for ((key, v) in value.entries.toSortedMap()) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(key, sb)
                    sb.append(':')
                    write(v, sb)
                }
                sb.append('}')
            }
            is JsonArr -> {
                sb.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) sb.append(',')
                    write(item, sb)
                }
                sb.append(']')
            }
            is JsonStr -> writeString(value.value, sb)
            is JsonInt -> sb.append(value.value.toString())
            is JsonDouble -> sb.append(value.value.toString())
            is JsonBool -> sb.append(if (value.value) "true" else "false")
            JsonNull -> sb.append("null")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }

    /** Parses [input] strictly. Returns null on any malformed input. */
    fun parse(input: String): JsonValue? {
        return try {
            val parser = Parser(input)
            val value = parser.parseValue(0)
            parser.skipWhitespace()
            if (parser.pos != input.length) null else value
        } catch (_: ParseException) {
            null
        }
    }

    private class ParseException : Exception()

    private class Parser(val input: String) {
        var pos = 0

        fun skipWhitespace() {
            while (pos < input.length && input[pos] in " \t\n\r") pos++
        }

        fun parseValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) throw ParseException()
            skipWhitespace()
            if (pos >= input.length) throw ParseException()
            return when (val ch = input[pos]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> JsonStr(parseString())
                't' -> {
                    expect("true")
                    JsonBool(true)
                }
                'f' -> {
                    expect("false")
                    JsonBool(false)
                }
                'n' -> {
                    expect("null")
                    JsonNull
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw ParseException()
            }
        }

        private fun expect(word: String) {
            if (!input.startsWith(word, pos)) throw ParseException()
            pos += word.length
        }

        private fun parseObject(depth: Int): JsonObj {
            pos++ // '{'
            val map = linkedMapOf<String, JsonValue>()
            skipWhitespace()
            if (pos < input.length && input[pos] == '}') {
                pos++
                return JsonObj(map)
            }
            while (true) {
                skipWhitespace()
                if (pos >= input.length || input[pos] != '"') throw ParseException()
                val key = parseString()
                if (key in map) throw ParseException() // duplicate keys rejected
                skipWhitespace()
                if (pos >= input.length || input[pos] != ':') throw ParseException()
                pos++
                val value = parseValue(depth + 1)
                map[key] = value
                skipWhitespace()
                if (pos >= input.length) throw ParseException()
                when (input[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonObj(map)
                    }
                    else -> throw ParseException()
                }
            }
        }

        private fun parseArray(depth: Int): JsonArr {
            pos++ // '['
            val list = mutableListOf<JsonValue>()
            skipWhitespace()
            if (pos < input.length && input[pos] == ']') {
                pos++
                return JsonArr(list)
            }
            while (true) {
                list.add(parseValue(depth + 1))
                skipWhitespace()
                if (pos >= input.length) throw ParseException()
                when (input[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonArr(list)
                    }
                    else -> throw ParseException()
                }
            }
        }

        private fun parseString(): String {
            // Assumes input[pos] == '"'.
            pos++
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= input.length) throw ParseException()
                        when (val esc = input[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > input.length) throw ParseException()
                                val hex = input.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw ParseException()
                                pos += 4
                                sb.append(code.toChar())
                            }
                            else -> throw ParseException()
                        }
                    }
                    else -> {
                        if (ch.code < 0x20) throw ParseException()
                        sb.append(ch)
                    }
                }
            }
            throw ParseException()
        }

        private fun parseNumber(): JsonValue {
            val start = pos
            if (pos < input.length && input[pos] == '-') pos++
            if (pos >= input.length) throw ParseException()
            if (input[pos] == '0') {
                pos++
            } else if (input[pos] in '1'..'9') {
                while (pos < input.length && input[pos].isDigit()) pos++
            } else {
                throw ParseException()
            }
            var isDouble = false
            if (pos < input.length && input[pos] == '.') {
                isDouble = true
                pos++
                if (pos >= input.length || !input[pos].isDigit()) throw ParseException()
                while (pos < input.length && input[pos].isDigit()) pos++
            }
            if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
                if (pos >= input.length || !input[pos].isDigit()) throw ParseException()
                while (pos < input.length && input[pos].isDigit()) pos++
            }
            val text = input.substring(start, pos)
            return if (isDouble) {
                JsonDouble(text.toDoubleOrNull() ?: throw ParseException())
            } else {
                val longValue = text.toLongOrNull() ?: throw ParseException()
                JsonInt(longValue)
            }
        }
    }
}
