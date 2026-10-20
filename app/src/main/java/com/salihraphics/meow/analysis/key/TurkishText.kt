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

    /** Separators books use between answers: middle dots, bullets, dashes of every width. */
    fun normalizeSeparators(text: String): String =
        text.replace(Regex("[·•∙●|]"), " ")
            .replace(Regex("[–—‒−]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
}
