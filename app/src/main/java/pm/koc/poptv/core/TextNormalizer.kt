package pm.koc.poptv.core

import java.text.Normalizer

/**
 * Text folding used for search and matching.
 *
 * Turkish-aware: every i-variant (İ, I, ı, i) folds to `i`, so "ISTANBUL", "İstanbul" and "istanbul" all
 * match regardless of device locale. Diacritics are stripped (ş→s, ğ→g, ç→c, ö→o, ü→u, é→e, ...), and the
 * result is lower-cased with [java.util.Locale.ROOT] so it never depends on the default locale.
 */
object TextNormalizer {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    /** Folds case, i-variants and diacritics; keeps punctuation and collapses whitespace. */
    fun fold(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                'İ', 'I', 'ı' -> sb.append('i')
                'ß' -> sb.append("ss")
                'Æ', 'æ' -> sb.append("ae")
                'Ø', 'ø' -> sb.append('o')
                'Đ', 'đ' -> sb.append('d')
                'Ł', 'ł' -> sb.append('l')
                else -> sb.append(ch)
            }
        }
        val decomposed = Normalizer.normalize(sb, Normalizer.Form.NFD)
        return combiningMarks.replace(decomposed, "")
            .lowercase(java.util.Locale.ROOT)
            .replace(whitespace, " ")
            .trim()
    }

    /** [fold] plus punctuation removal: "Tom & Jerry: The Movie!" -> "tom jerry the movie". */
    fun searchKey(input: String): String = fold(input).replace(nonAlnum, " ").trim()

    /** Compact key without any separators, used for fuzzy channel-name matching: "TRT 1 HD" -> "trt1hd". */
    fun compactKey(input: String): String = fold(input).replace(nonAlnum, "")
}
