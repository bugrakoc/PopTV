package pm.koc.poptv.core

/** "Episode 2" < "Episode 10"; case- and accent-insensitive (Turkish-aware via [TextNormalizer.fold]). */
object NaturalOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        val x = TextNormalizer.fold(a)
        val y = TextNormalizer.fold(b)
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            val cx = x[i]
            val cy = y[j]
            if (cx.isDigit() && cy.isDigit()) {
                val si = i
                val sj = j
                while (i < x.length && x[i].isDigit()) i++
                while (j < y.length && y[j].isDigit()) j++
                val nx = x.substring(si, i).trimStart('0')
                val ny = y.substring(sj, j).trimStart('0')
                if (nx.length != ny.length) return nx.length - ny.length
                val c = nx.compareTo(ny)
                if (c != 0) return c
            } else {
                if (cx != cy) return cx - cy
                i++
                j++
            }
        }
        return (x.length - i) - (y.length - j)
    }
}
