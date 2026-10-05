package com.alphaomegos.annasagenda.screens.travel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alphaomegos.annasagenda.R
import com.alphaomegos.annasagenda.model.TravelBaseCountry
import com.alphaomegos.annasagenda.model.TravelContinent
import com.alphaomegos.annasagenda.model.TravelCountryRecord
import com.alphaomegos.annasagenda.support.TravelNames
import com.alphaomegos.annasagenda.util.appLocale
import java.text.Collator
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Country names as the screen shows them (decided 05.10): the name the
 * system gives the ISO code in the app's language, the base's English name
 * where the system has none, the user's own name for a country of their own.
 * A search also finds a country by its English name.
 */
internal class TravelScreenNames(
    private val base: Map<String, TravelBaseCountry>,
    private val records: List<TravelCountryRecord>,
    private val locale: Locale,
) : TravelNames {
    private val cache = HashMap<String, String>()

    override fun nameOf(countryId: String): String = cache.getOrPut(countryId) {
        records.firstOrNull { it.countryId == countryId }?.customName
            ?: base[countryId]?.let { b ->
                Locale("", b.code).getDisplayCountry(locale)
                    .takeIf { it.isNotBlank() && it != b.code } ?: b.englishName
            }
            ?: countryId
    }

    override fun searchNamesOf(countryId: String): List<String> =
        listOfNotNull(nameOf(countryId), base[countryId]?.englishName).distinct()
}

@Composable
internal fun rememberTravelNames(base: List<TravelBaseCountry>, records: List<TravelCountryRecord>): TravelScreenNames {
    val locale = appLocale()
    return remember(base, records, locale) { TravelScreenNames(base.associateBy { it.code }, records, locale) }
}

/** The app language's alphabet, for sorting names. */
@Composable
internal fun rememberTravelCollator(): Comparator<String> {
    val locale = appLocale()
    return remember(locale) {
        val collator = Collator.getInstance(locale)
        Comparator { a, b -> collator.compare(a, b) }
    }
}

/** "Май", "September": the month as one says it alone, with a capital. */
internal fun travelMonthName(month: Int, locale: Locale): String =
    Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

internal fun travelContinentRes(continent: TravelContinent): Int = when (continent) {
    TravelContinent.AFRICA -> R.string.travel_continent_africa
    TravelContinent.ANTARCTICA -> R.string.travel_continent_antarctica
    TravelContinent.ASIA -> R.string.travel_continent_asia
    TravelContinent.EUROPE -> R.string.travel_continent_europe
    TravelContinent.NORTH_AMERICA -> R.string.travel_continent_north_america
    TravelContinent.OCEANIA -> R.string.travel_continent_oceania
    TravelContinent.SOUTH_AMERICA -> R.string.travel_continent_south_america
}
