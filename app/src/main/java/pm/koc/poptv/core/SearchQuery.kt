package pm.koc.poptv.core

/** Turns user input into a SQL LIKE pattern over [TextNormalizer.searchKey]-folded columns. */
object SearchQuery {

    const val MIN_LENGTH = 2

    /**
     * Words must appear in order, anything may be between them: "kanal d" -> "%kanal%d%".
     * Returns null when the query is too short to search. LIKE wildcards in the input are escaped with '\'.
     */
    fun likePattern(input: String): String? {
        val folded = TextNormalizer.searchKey(input)
        if (folded.replace(" ", "").length < MIN_LENGTH) return null
        val words = folded.split(' ').filter { it.isNotEmpty() }
        return words.joinToString(separator = "%", prefix = "%", postfix = "%") { escape(it) }
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
