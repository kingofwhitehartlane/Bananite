package ir.mums.stufood.util

import kotlin.math.max

/**
 * Fuzzy matching between names on the Buy Food page and the foods / self (cafeteria)
 * names on the Reservation page. Tolerates typos, ي/ی, ZWNJ and filler words.
 *
 * Rules that keep similar names apart:
 *  - A half portion ("نیم پرس") only ever matches a half portion, a full portion only a full one.
 *    "نیم" itself is detected with typo tolerance.
 *  - When the reservation page gives prices, they are a second opinion: the cheaper of two
 *    foods with the same name is the half portion (see parseAll).
 *  - Matching is always against ALL the day's foods first; whether the match is switched on
 *    is checked afterwards by the caller.
 */
object FoodMatcher {

    /** A food of the day as read from the reservation page. */
    data class Candidate(val name: String, val priceRial: Long? = null)

    private val generic = setOf("چلو", "پلو", "خورشت", "خوراک", "برنج", "با", "و", "همراه")
    private val selfWords = setOf("سلف", "سالن", "غذاخوری")

    /** Minimum similarity (0..1) for a food name. Only ~3 distinct foods compete, so this can be lenient. */
    private const val MIN_FOOD_SCORE = 0.65
    private const val MIN_SELF_SCORE = 0.7

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ۀ', 'ة' -> sb.append('ه')
                'أ', 'إ', 'آ' -> sb.append('ا')
                '\u0640' -> {}
                '\u200c', '\u200f', '\u200e' -> sb.append(' ')
                in '\u064B'..'\u065F', '\u0670' -> {}
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                else -> sb.append(if (c.isLetterOrDigit()) c.lowercaseChar() else ' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun bigrams(s: String): List<String> =
        if (s.length < 2) listOf(s) else (0 until s.length - 1).map { s.substring(it, it + 2) }

    /** Best of: bigram Dice (good for extra/missing words) and edit-distance ratio (good for typos). */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val x = bigrams(a)
        val rest = bigrams(b).toMutableList()
        var hits = 0
        for (g in x) if (rest.remove(g)) hits++
        val dice = 2.0 * hits / (x.size + bigrams(b).size)
        val ratio = 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
        return max(dice, ratio)
    }

    // ------------------------------------------------------------------
    // Foods
    // ------------------------------------------------------------------

    private class Parsed(var half: Boolean, val core: String, val price: Long?)

    private fun isHalfToken(t: String): Boolean = when {
        t == "نیم" || t == "نیمه" || t == "نیمپرس" -> true
        t.length in 3..4 && t.startsWith("ن") && levenshtein(t, "نیم") <= 1 -> true   // typo: نین، نیمم ...
        t.length in 5..7 && levenshtein(t, "نیمپرس") <= 1 -> true                    // typo: نیمپرص ...
        else -> false
    }

    private fun parse(name: String, price: Long? = null): Parsed {
        val tokens = normalize(name).split(' ').filter { it.isNotEmpty() }
        val half = tokens.any { isHalfToken(it) }
        val core = tokens
            .filterNot { isHalfToken(it) }
            .filterNot { half && levenshtein(it, "پرس") <= 1 }
            .filterNot { it in generic }
            .joinToString("")
        return Parsed(half, core, price)
    }

    /**
     * Parses all candidates, then uses prices as a SECOND opinion on half vs full.
     * For foods sharing a name (same core), score each one:
     *   name says "نیم پرس"  +1      cheaper than its same-name sibling  +1
     *   name has no marker    0      dearer than its sibling             -1
     * Half if the total is positive. A name/price disagreement (total 0) goes to the name,
     * so price confirms the name or fills in when the name has no marker; it never
     * overrules a clear "نیم پرس" on its own.
     */
    private fun parseAll(cands: List<Candidate>): List<Parsed> {
        val ps = cands.map { parse(it.name, it.priceRial) }
        val nameHalf = ps.map { it.half }
        val seen = mutableSetOf<Int>()
        for (i in ps.indices) {
            if (i in seen) continue
            val group = ps.indices.filter { similarity(ps[it].core, ps[i].core) >= 0.85 }
            seen += group
            val priced = group.filter { ps[it].price != null }
            if (priced.size < 2) continue
            val lo = priced.minOf { ps[it].price!! }
            val hi = priced.maxOf { ps[it].price!! }
            if (lo == hi) continue
            for (j in priced) {
                val nameVote = if (nameHalf[j]) 1 else 0
                val priceVote = if (ps[j].price == lo) 1 else -1
                val total = nameVote + priceVote
                ps[j].half = if (total == 0) nameHalf[j] else total > 0
            }
        }
        return ps
    }

    /** True if the food counts as a half portion (used for the little "half" tag in the UI). */
    fun halfFlags(cands: List<Candidate>): List<Boolean> = parseAll(cands).map { it.half }

    /**
     * Index in [candidates] of the best match for the Buy Food page's [serverName],
     * or null if nothing is close enough.
     */
    fun bestMatch(serverName: String, candidates: List<Candidate>): Int? {
        val target = parse(serverName)
        val parsed = parseAll(candidates)
        var bestIdx: Int? = null
        var bestScore = 0.0
        parsed.forEachIndexed { i, p ->
            if (p.half != target.half) return@forEachIndexed   // portion size must agree
            val score = similarity(target.core, p.core)
            if (score > bestScore) { bestScore = score; bestIdx = i }
        }
        return if (bestScore >= MIN_FOOD_SCORE) bestIdx else null
    }

    // ------------------------------------------------------------------
    // Self (cafeteria)
    // ------------------------------------------------------------------

    private fun selfCore(s: String): String {
        val tokens = normalize(s).split(' ').filter { it.isNotEmpty() }
        val core = tokens.filterNot { it in selfWords }.joinToString("")
        return core.ifEmpty { tokens.joinToString("") }
    }

    private fun selfScore(a: String, b: String): Double {
        val x = selfCore(a)
        val y = selfCore(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x.contains(y) || y.contains(x)) return 1.0   // "پردیس" inside "سلف پردیس شماره 2"
        return similarity(x, y)
    }

    /** Index in [options] of the self that best matches [target], or null. */
    fun bestSelf(target: String, options: List<String>): Int? {
        var best: Int? = null
        var bestScore = 0.0
        options.forEachIndexed { i, o ->
            val s = selfScore(o, target)
            if (s > bestScore) { bestScore = s; best = i }
        }
        return if (bestScore >= MIN_SELF_SCORE) best else null
    }
}
