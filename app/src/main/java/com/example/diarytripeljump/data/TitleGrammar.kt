package com.example.diarytripeljump.data

/**
 * Собирает название упражнения по правилам русского языка: сам подбирает предлог и падеж.
 * «Метание» + ядро = «Метание ядра», «Присед» + штанга = «Присед со штангой»,
 * «Прыжки» + тумба = «Прыжки на тумбу», «Тройной» + шиповки = «Тройной в шиповках».
 */
object TitleGrammar {
    /** Позиция свойства в названии. */
    const val AUTO = 'A'
    const val START = 'S'
    const val MIDDLE = 'M'
    const val END = 'E'

    /** Нога по очереди: левая, правая и снова левая. Хранится в legSide. */
    const val ALTERNATE = "ALTERNATE"

    const val SPIKES_PHRASE = "в шиповках"
    const val DEFAULT_LAYOUT = "AAA"

    private val THROW_WORDS = listOf("метани", "бросок", "броски", "бросани", "толкани", "выталкиван", "выбрасыван")
    private val OVER_ROOTS = listOf("барьер", "планк")
    private val ONTO_ROOTS = listOf("тумб", "скам", "плинт", "ступен", "ящик", "горк")
    private val NAME_SPIKES_TAIL = Regex("\\s+в(\\s+шип\\S*)?$", RegexOption.IGNORE_CASE)
    private val SOFT_ENDINGS = "жшщчц"
    private val HARD_ENDINGS = "гкхжшщч"

    private val SPECIAL_GENITIVE = mapOf("гири" to "гирь", "шайбы" to "шайб", "гантели" to "гантелей")
    private val SPECIAL_INSTRUMENTAL = mapOf("мяч" to "мячом", "ключ" to "ключом")

    fun legPhrase(isSingleLeg: Boolean, legSide: String?): String? = when {
        !isSingleLeg -> null
        legSide == "LEFT" -> "на левой ноге"
        legSide == "RIGHT" -> "на правой ноге"
        legSide == ALTERNATE -> "поочерёдно на обеих ногах"
        else -> "на одной ноге"
    }

    /** Та же нога уже названа в упражнении: «Махи левой ногой» + левая. Другой выбор показывается всегда. */
    private fun nameHasLeg(name: String, legSide: String?): Boolean {
        val root = when (legSide) {
            "LEFT" -> "лев"
            "RIGHT" -> "прав"
            else -> return false
        }
        return Regex("$root\\S*\\s+ног", RegexOption.IGNORE_CASE).containsMatchIn(name)
    }

    private fun isThrow(name: String): Boolean = name.lowercase().let { n -> THROW_WORDS.any { n.contains(it) } }

    private fun stem(word: String): String = word.lowercase().trimEnd(*"аеёиоуыэюяьй".toCharArray())

    private fun nameHasInventory(name: String, inventory: String): Boolean {
        val root = stem(inventory.trim().substringAfterLast(' '))
        return root.length >= 3 && name.lowercase().contains(root)
    }

    private fun isAdjective(word: String): Boolean = listOf("ый", "ий", "ой", "ая", "яя", "ое", "ее").any { word.endsWith(it) }

    private fun adjective(word: String, case: Char): String {
        val body = word.dropLast(2)
        return when (word.takeLast(2)) {
            "ая" -> body + (when (case) { 'A' -> "ую"; else -> "ой" })
            "яя" -> body + (when (case) { 'A' -> "юю"; else -> "ей" })
            "ий" -> body + (when (case) { 'G' -> "его"; 'I' -> "им"; else -> "ий" })
            "ее" -> body + (when (case) { 'G' -> "его"; 'I' -> "им"; else -> "ее" })
            "ое" -> body + (when (case) { 'G' -> "ого"; 'I' -> "ым"; else -> "ое" })
            else -> body + (when (case) { 'G' -> "ого"; 'I' -> "ым"; else -> word.takeLast(2) })
        }
    }

    private fun isPlural(word: String): Boolean = word.length > 3 && (word.endsWith("ы") || word.endsWith("и"))

    private fun genitive(w: String): String {
        SPECIAL_GENITIVE[w]?.let { return it }
        val body = w.dropLast(1)
        return when {
            w.endsWith("ки") -> w.dropLast(2) + "ок"
            isPlural(w) -> if (w.endsWith("ы")) body + "ов" else body + "ей"
            w.endsWith("о") -> body + "а"
            w.endsWith("е") -> body + "я"
            w.endsWith("а") -> body + if (body.last() in HARD_ENDINGS) "и" else "ы"
            w.endsWith("я") -> body + "и"
            w.endsWith("ь") || w.endsWith("й") -> body + "я"
            else -> w + "а"
        }
    }

    private fun instrumental(w: String): String {
        SPECIAL_INSTRUMENTAL[w]?.let { return it }
        val body = w.dropLast(1)
        return when {
            isPlural(w) -> body + if (w.endsWith("и") && body.last() !in HARD_ENDINGS) "ями" else "ами"
            w.endsWith("о") -> body + "ом"
            w.endsWith("е") -> body + "ем"
            w.endsWith("а") -> body + if (body.last() in SOFT_ENDINGS) "ей" else "ой"
            w.endsWith("я") -> body + "ей"
            w.endsWith("ь") || w.endsWith("й") -> body + "ем"
            w.last() in SOFT_ENDINGS -> w + "ем"
            else -> w + "ом"
        }
    }

    private fun accusative(w: String): String = when {
        w.endsWith("а") -> w.dropLast(1) + "у"
        w.endsWith("я") -> w.dropLast(1) + "ю"
        else -> w
    }

    /** Склоняет название снаряда: [case] G — родительный, I — творительный, A — винительный. */
    fun decline(phrase: String, case: Char): String {
        val words = phrase.trim().lowercase().split(Regex("\\s+"))
        return words.mapIndexed { i, word ->
            when {
                word.length < 3 -> word
                i < words.lastIndex && isAdjective(word) -> adjective(word, case)
                i == words.lastIndex -> when (case) {
                    'G' -> genitive(word)
                    'I' -> instrumental(word)
                    else -> accusative(word)
                }
                else -> word
            }
        }.joinToString(" ")
    }

    private fun withPreposition(next: String): String =
        if (Regex("^[сзшщж][бвгджзклмнпрстфхцчшщ]").containsMatchIn(next)) "со $next" else "с $next"

    /** Снаряд в названии: «ядра 5 кг», «со штангой», «через барьер 91 см»; пусто, если он уже назван. */
    fun inventoryPhrase(exerciseName: String, inventory: String, value: String): String {
        val low = inventory.lowercase()
        val phrase = when {
            nameHasInventory(exerciseName, inventory) -> ""
            isThrow(exerciseName) -> decline(inventory, 'G')
            OVER_ROOTS.any { low.contains(it) } -> "через " + decline(inventory, 'A')
            ONTO_ROOTS.any { low.contains(it) } -> "на " + decline(inventory, 'A')
            else -> withPreposition(decline(inventory, 'I'))
        }
        return listOf(phrase, value).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Полное название. [layout] — позиции ноги, снаряда и шиповок: по символу на свойство (см. [AUTO]).
     * Авто: снаряд метания сразу за первым словом («Метание ядра лицом вперёд»), остальное в конце.
     */
    fun compose(
        name: String, isSingleLeg: Boolean, legSide: String?, inSpikes: Boolean,
        inventory: String?, inventoryValue: String, layout: String?
    ): String {
        var base = name.trim()
        if (inSpikes) base = base.replace(NAME_SPIKES_TAIL, "")
        val leg = legPhrase(isSingleLeg, legSide)?.takeUnless { nameHasLeg(base, legSide) }
        val implement = inventory?.takeIf { it.isNotBlank() }?.let { inventoryPhrase(base, it, inventoryValue) }?.takeIf { it.isNotBlank() }
        val spikes = if (inSpikes) SPIKES_PHRASE else null

        val positions = (layout ?: DEFAULT_LAYOUT).padEnd(3, AUTO)
        val implementAuto = if (inventory != null && isThrow(base) && !nameHasInventory(base, inventory)) MIDDLE else END
        fun position(index: Int, auto: Char) = positions[index].let { if (it == AUTO) auto else it }
        val parts = listOf(
            leg to position(0, END),
            implement to position(1, implementAuto),
            spikes to position(2, END)
        ).filter { it.first != null }
        fun at(pos: Char) = parts.filter { it.second == pos }.mapNotNull { it.first }

        val words = base.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val head = words.firstOrNull().orEmpty()
        val keepsCase = head.length > 1 && head[1].isUpperCase()
        val first = if (at(START).isNotEmpty() && !keepsCase) head.replaceFirstChar { it.lowercase() } else head
        val all = at(START) + first + at(MIDDLE) + words.drop(1) + at(END)
        return all.filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() }
    }
}
