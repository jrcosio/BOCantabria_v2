package com.jrblanco.boccantabria.data.source.local

import androidx.room.TypeConverter
import com.jrblanco.boccantabria.domain.model.ParserWarning
import java.time.LocalDate

/**
 * Translations between the domain types and what SQLite can hold.
 *
 * Dates are stored as ISO text rather than as a day count so that lexicographic order matches
 * chronological order: `ORDER BY publication_date DESC` then needs no conversion, and a human
 * inspecting the database can read it.
 *
 * Lists use the ASCII unit separator. A comma or a pipe would collide with the real content:
 * organisation names carry commas, and `categorias` is pipe-separated to begin with.
 */
class Converters {

    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun listToString(value: List<String>?): String =
        value.orEmpty().joinToString(SEPARATOR)

    @TypeConverter
    fun stringToList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(SEPARATOR)

    @TypeConverter
    fun warningsToString(value: Set<ParserWarning>?): String =
        value.orEmpty().joinToString(SEPARATOR) { it.name }

    /**
     * Unknown names are dropped instead of throwing: a database written by a future version that
     * added a warning must still be readable by this one.
     */
    @TypeConverter
    fun stringToWarnings(value: String?): Set<ParserWarning> =
        if (value.isNullOrEmpty()) {
            emptySet()
        } else {
            value.split(SEPARATOR)
                .mapNotNull { name -> ParserWarning.entries.firstOrNull { it.name == name } }
                .toSet()
        }

    private companion object {
        /** ASCII unit separator: it does not appear in the text the source publishes. */
        const val SEPARATOR = "\u001F"
    }
}
