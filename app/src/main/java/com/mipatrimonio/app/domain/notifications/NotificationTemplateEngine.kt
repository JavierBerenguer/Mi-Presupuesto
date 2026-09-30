package com.mipatrimonio.app.domain.notifications

object NotificationTemplateBuilder {
    fun build(text: String, keyRange: IntRange, variableRange: IntRange): NotificationTemplate {
        require(validRange(text, keyRange) && validRange(text, variableRange)) { "Los rangos seleccionados no son válidos" }
        val amount = NotificationAmountRecognizer.first(text)
            ?: throw IllegalArgumentException("No se ha detectado ningún importe en la notificación")
        require(!overlaps(amount.range, variableRange)) { "El texto variable no puede incluir el importe" }
        require(!overlaps(keyRange, variableRange)) { "El texto clave y el variable no pueden solaparse" }

        val effectiveKeyRange = if (overlaps(keyRange, amount.range)) {
            minOf(keyRange.first, amount.range.first)..maxOf(keyRange.last, amount.range.last)
        } else keyRange
        val keySelections = keySelections(text, effectiveKeyRange, amount.range)
        require(keySelections.any { (it.segment as? TemplateSegment.Literal)?.text?.isNotBlank() == true }) {
            "El texto clave es obligatorio"
        }
        val selected = (keySelections + listOf(
            Selection(amount.range, TemplateSegment.Amount),
            Selection(variableRange, TemplateSegment.Variable),
        )).distinctBy { it.range to it.segment }.sortedBy { it.range.first }
        val segments = mutableListOf<TemplateSegment>()
        selected.forEachIndexed { index, selection ->
            if (index > 0) {
                val previous = selected[index - 1].range.last + 1
                val gap = text.substring(previous, selection.range.first)
                if (gap.isNotEmpty()) segments += TemplateSegment.Literal(gap)
            }
            segments += selection.segment
        }
        return NotificationTemplate(mergeLiterals(segments)).also { require(it.isValid) { "La plantilla no es válida" } }
    }

    private fun keySelections(text: String, keyRange: IntRange, amountRange: IntRange): List<Selection> {
        if (!overlaps(keyRange, amountRange)) {
            return listOf(Selection(keyRange, TemplateSegment.Literal(text.substring(keyRange))))
        }
        return buildList {
            if (keyRange.first < amountRange.first) {
                val before = keyRange.first until amountRange.first
                add(Selection(before, TemplateSegment.Literal(text.substring(before))))
            }
            if (amountRange.last < keyRange.last) {
                val after = (amountRange.last + 1)..keyRange.last
                add(Selection(after, TemplateSegment.Literal(text.substring(after))))
            }
        }
    }

    private fun validRange(text: String, range: IntRange) = range.first >= 0 && range.last < text.length && !range.isEmpty()
    private fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last
    private data class Selection(val range: IntRange, val segment: TemplateSegment)
}

object NotificationTemplateMatcher {
    fun match(template: NotificationTemplate, text: String): TemplateMatch? {
        if (!template.isValid) return null
        val normalized = indexedNormalize(text)
        return NotificationAmountRecognizer.findAll(text).firstNotNullOfOrNull { amount ->
            val amountStart = normalized.originalToNormalized(amount.range.first)
            val amountEnd = normalized.originalToNormalized(amount.range.last + 1)
            val pattern = buildString {
                append(".*?")
                template.segments.forEachIndexed { index, segment -> when (segment) {
                    is TemplateSegment.Literal -> append(literalPattern(normalizeNotificationText(segment.text)))
                    TemplateSegment.Amount -> append(Regex.escape(normalized.text.substring(amountStart, amountEnd)))
                    TemplateSegment.Variable -> append(if (index == template.segments.lastIndex) "(.*)" else "(.*?)")
                } }
                append(".*")
            }
            val match = Regex(pattern, setOf(RegexOption.DOT_MATCHES_ALL)).matchEntire(normalized.text) ?: return@firstNotNullOfOrNull null
            val group = match.groups[1] ?: return@firstNotNullOfOrNull null
            val start = normalized.map.getOrElse(group.range.first) { text.length }
            val end = normalized.map.getOrElse(group.range.last) { text.lastIndex } + 1
            val variable = text.substring(start.coerceAtMost(text.length), end.coerceAtMost(text.length))
                .trim().trimEnd(',', ';', ':', '-', '!', '?').trim()
            variable.takeIf { it.isNotEmpty() }?.let { TemplateMatch(it, amount) }
        }
    }

    fun select(structures: List<NotificationStructure>, text: String): Pair<NotificationStructure, TemplateMatch>? =
        structures.asSequence().filter { it.enabled }.mapNotNull { structure ->
            match(structure.template, text)?.let { Triple(structure, it, structure.template.literalLength) }
        }.sortedWith(compareByDescending<Triple<NotificationStructure, TemplateMatch, Int>> { it.third }
            .thenBy { it.first.createdAt }.thenBy { it.first.id })
            .firstOrNull()?.let { it.first to it.second }

    private fun literalPattern(literal: String): String = Regex("\\s+|\\S+").findAll(literal).joinToString("") {
        if (it.value.first().isWhitespace()) "\\s+" else Regex.escape(it.value)
    }

    private data class IndexedText(val text: String, val map: List<Int>) {
        fun originalToNormalized(index: Int): Int = map.indexOfFirst { it >= index }.let { if (it < 0) text.length else it }
    }

    private fun indexedNormalize(value: String): IndexedText {
        val out = StringBuilder()
        val map = mutableListOf<Int>()
        var inWhitespace = false
        value.forEachIndexed { index, char ->
            if (char.isWhitespace()) {
                if (!inWhitespace) { out.append(' '); map += index }
                inWhitespace = true
            } else {
                val normalizedChar = normalizeNotificationText(char.toString())
                normalizedChar.forEach { out.append(it); map += index }
                inWhitespace = false
            }
        }
        return IndexedText(out.toString(), map)
    }
}

object NotificationTemplateJson {
    fun encode(template: NotificationTemplate): String = template.segments.joinToString(prefix = "[", postfix = "]") { segment ->
        when (segment) {
            is TemplateSegment.Literal -> "{\"type\":\"LITERAL\",\"text\":\"${escape(segment.text)}\"}"
            TemplateSegment.Amount -> "{\"type\":\"IMPORTE\"}"
            TemplateSegment.Variable -> "{\"type\":\"VARIABLE\"}"
        }
    }

    fun decode(json: String): NotificationTemplate {
        // La llave final debe ir escapada: el motor de regex de Android (ICU) rechaza «}» suelta aunque la JVM la acepte.
        val item = Regex("\\{\\\"type\\\":\\\"(LITERAL|IMPORTE|VARIABLE)\\\"(?:,\\\"text\\\":\\\"((?:\\\\.|[^\\\"])*)\\\")?\\}")
        val segments = item.findAll(json).map { match -> when (match.groupValues[1]) {
            "LITERAL" -> TemplateSegment.Literal(unescape(match.groupValues[2]))
            "IMPORTE" -> TemplateSegment.Amount
            else -> TemplateSegment.Variable
        } }.toList()
        return NotificationTemplate(segments).also { require(it.isValid) { "La plantilla guardada no es válida" } }
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    private fun unescape(value: String): String {
        val out = StringBuilder(); var escaped = false
        value.forEach { char -> if (escaped) { out.append(if (char == 'n') '\n' else if (char == 'r') '\r' else char); escaped = false }
            else if (char == '\\') escaped = true else out.append(char) }
        return out.toString()
    }
}

private fun mergeLiterals(segments: List<TemplateSegment>): List<TemplateSegment> = segments.fold(mutableListOf()) { result, segment ->
    val previous = result.lastOrNull()
    if (previous is TemplateSegment.Literal && segment is TemplateSegment.Literal) {
        result[result.lastIndex] = TemplateSegment.Literal(previous.text + segment.text)
    } else result += segment
    result
}
