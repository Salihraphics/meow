package com.salihraphics.meow.analysis.key

/** Text folding for matching Turkish (and English) headings regardless of case and accents. */
object TurkishText {
    /** "CEVAP ANAHTARI", "Cevap Anahtarı" and "cevap anahtari" all fold to "cevap anahtari". */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            sb.append(
                when (c) {
                    'İ', 'I', 'ı', 'i' -> 'i'
                    'Ç', 'ç' -> 'c'
                    'Ğ', 'ğ' -> 'g'
                    'Ö', 'ö' -> 'o'
                    'Ş', 'ş' -> 's'
                    'Ü', 'ü' -> 'u'
                    'Â', 'â' -> 'a'
                    'Î', 'î' -> 'i'
                    'Û', 'û' -> 'u'
                    else -> c.lowercaseChar()
                },
            )
        }
        return sb.toString()
    }

    /**
     * Separators books use between answers (middle dots, bullets, dashes of every width), and
     * answer letters printed in circles (Ⓒ), full width (Ｃ) or in brackets ("(C)"), as plain letters.
     */
    fun normalizeSeparators(text: String): String {
        val letters = StringBuilder(text.length)
        for (c in text) {
            letters.append(
                when (c) {
                    in '\u24B6'..'\u24CF' -> " ${'A' + (c - '\u24B6')} "
                    in '\u24D0'..'\u24E9' -> " ${'A' + (c - '\u24D0')} "
                    in '\uFF21'..'\uFF3A' -> 'A' + (c - '\uFF21')
                    else -> c
                },
            )
        }
        return letters.toString()
            .replace(Regex("\\(([A-Ea-e])\\)"), " $1 ")
            .replace(Regex("[·•∙●|]"), " ")
            .replace(Regex("[–—‒−]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
