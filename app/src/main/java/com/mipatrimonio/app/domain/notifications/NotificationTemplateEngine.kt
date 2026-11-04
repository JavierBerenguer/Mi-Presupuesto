package com.mipatrimonio.app.domain.notifications

object NotificationTemplateBuilder {
    /**
     * [amountRange] elige el importe cuando la notificación trae varios (p. ej. «1,88 € por acción. Has recibido 0,01 €»);
     * si es nulo se usa el primero. Los demás importes que quedan dentro del patrón pasan a ser «otro importe» y
     * coinciden con cualquier cantidad, para que la estructura siga reconociendo la notificación aunque cambien.
     */
    fun build(text: String, keyRange: IntRange, variableRange: IntRange, amountRange: IntRange? = null): NotificationTemplate {
        require(validRange(text, keyRange) && validRange(text, variableRange)) { "Los rangos seleccionados no son válidos" }
        val amounts = NotificationAmountRecognizer.findAll(text)
        require(amounts.isNotEmpty()) { "No se ha detectado ningún importe en la notificación" }
        val amount = if (amountRange == null) amounts.first() else amounts.firstOrNull { overlaps(it.range, amountRange) }
            ?: throw IllegalArgumentException("El importe elegido no está en la notificación")
        require(!overlaps(amount.range, variableRange)) { "El texto variable no puede incluir el importe" }
        require(!overlaps(keyRange, variableRange)) { "El texto clave y el variable no pueden solaparse" }
        val others = amounts.filter { it !== amount && !overlaps(it.range, amount.range) && !overlaps(it.range, variableRange) }
        val amountRanges = listOf(amount.range) + others.map { it.range }

        val effectiveKeyRange = amountRanges.filter { overlaps(it, keyRange) }.fold(keyRange) { range, hole ->
            minOf(range.first, hole.first)..maxOf(range.last, hole.last)
        }
        require(!overlaps(effectiveKeyRange, variableRange)) { "El texto clave y el variable no pueden solaparse" }
        val keySelections = subtract(effectiveKeyRange, amountRanges).map { Selection(it, TemplateSegment.Literal(text.substring(it))) }
        require(keySelections.any { (it.segment as TemplateSegment.Literal).text.isNotBlank() }) {
            "El texto clave es obligatorio"
        }
        val spanStart = minOf(effectiveKeyRange.first, amount.range.first, variableRange.first)
        val spanEnd = maxOf(effectiveKeyRange.last, amount.range.last, variableRange.last)
        val otherSelections = others.filter { it.range.first >= spanStart && it.range.last <= spanEnd }
            .map { Selection(it.range, TemplateSegment.OtherAmount) }
        val selected = (keySelections + otherSelections + listOf(
            Selection(amount.range, TemplateSegment.Amount),
            Selection(variableRange, TemplateSegment.Variable),
        )).distinctBy { it.range to it.segment }.sortedBy { it.range.first }
        val segments = mutableListOf<TemplateSegment>()
        selected.forEachIndexed { index, selection ->
            if (index > 0) {
                val previous = selected[index - 1].range.last + 1
                if (previous < selection.range.first) segments += TemplateSegment.Literal(text.substring(previous, selection.range.first))
            }
            segments += selection.segment
        }
        return NotificationTemplate(mergeLiterals(segments)).also { require(it.isValid) { "La plantilla no es válida" } }
    }

    private fun subtract(range: IntRange, holes: List<IntRange>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = range.first
        holes.filter { overlaps(it, range) }.sortedBy { it.first }.forEach { hole ->
            if (hole.first > start) result += start until hole.first
            start = maxOf(start, hole.last + 1)
        }
        if (start <= range.last) result += start..range.last
        return result
    }

    private fun validRange(text: String, range: IntRange) = range.first >= 0 && range.last < text.length && !range.isEmpty()
    private fun overlaps(a: IntRange, b: IntRange) = a.first <= b.last && b.first <= a.last
    private data class Selection(val range: IntRange, val segment: TemplateSegment)
}

object NotificationTemplateMatcher {
    fun match(template: NotificationTemplate, text: String): TemplateMatch? {
        if (!template.isValid) return null
        val normalized = indexedNormalize(text)
        val amounts = NotificationAmountRecognizer.findAll(text)
        fun normalizedAmount(amount: RecognizedAmount): String = normalized.text.substring(
            normalized.originalToNormalized(amount.range.first), normalized.originalToNormalized(amount.range.last + 1),
        )
        return amounts.firstNotNullOfOrNull { amount ->
            val others = amounts.filter { it !== amount }.map { Regex.escape(normalizedAmount(it)) }
            val otherPattern = if (others.isEmpty()) "(?!)" else others.joinToString("|", prefix = "(?:", postfix = ")")
            val pattern = buildString {
                append(".*?")
                template.segments.forEachIndexed { index, segment -> when (segment) {
                    is TemplateSegment.Literal -> append(literalPattern(normalizeNotificationText(segment.text)))
                    TemplateSegment.Amount -> append(Regex.escape(normalizedAmount(amount)))
                    TemplateSegment.Variable -> append(if (index == template.segments.lastIndex) "(.*)" else "(.*?)")
                    TemplateSegment.OtherAmount -> append(otherPattern)
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
            TemplateSegment.OtherAmount -> "{\"type\":\"OTRO_IMPORTE\"}"
        }
    }

    fun decode(json: String): NotificationTemplate {
        // La llave final debe ir escapada: el motor de regex de Android (ICU) rechaza «}» suelta aunque la JVM la acepte.
        val item = Regex("\\{\\\"type\\\":\\\"(LITERAL|IMPORTE|VARIABLE|OTRO_IMPORTE)\\\"(?:,\\\"text\\\":\\\"((?:\\\\.|[^\\\"])*)\\\")?\\}")
        val segments = item.findAll(json).map { match -> when (match.groupValues[1]) {
            "LITERAL" -> TemplateSegment.Literal(unescape(match.groupValues[2]))
            "IMPORTE" -> TemplateSegment.Amount
            "OTRO_IMPORTE" -> TemplateSegment.OtherAmount
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
