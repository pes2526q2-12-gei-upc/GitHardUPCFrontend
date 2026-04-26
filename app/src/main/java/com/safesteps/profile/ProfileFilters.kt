package com.safesteps.profile

import com.safesteps.data.UserFilters

private val FILTER_LEVEL_WEIGHTS = listOf(0.25, 0.5, 0.75, 1.0)
private val profileFilterIndexes = profileFilterKeys.withIndex().associate { (index, key) ->
    key to index
}

internal data class ProfileFiltersUiModel(
    val values: List<Int> = List(PROFILE_FILTER_COUNT) { 1 }
) {
    init {
        require(values.size == PROFILE_FILTER_COUNT) {
            "Expected $PROFILE_FILTER_COUNT profile filters but found ${values.size}"
        }
    }

    fun updated(index: Int, value: Int): ProfileFiltersUiModel {
        if (index !in values.indices) {
            return this
        }

        val sanitizedValue = value.coerceIn(0, FILTER_LEVEL_WEIGHTS.lastIndex)
        if (values[index] == sanitizedValue) {
            return this
        }

        return ProfileFiltersUiModel(
            values = values.toMutableList().also { updatedValues ->
                updatedValues[index] = sanitizedValue
            }
        )
    }
}

internal fun ProfileFiltersUiModel.toUserFilters(): UserFilters {
    return UserFilters(
        comissaries = filterWeight(ProfileFilterKey.COMISSARIES),
        fetsPenals = filterWeight(ProfileFilterKey.FETS_PENALS),
        cameresSeguretat = filterWeight(ProfileFilterKey.CAMERES_SEGURETAT),
        infraccions = filterWeight(ProfileFilterKey.INFRACCIONS),
        fontsAigua = filterWeight(ProfileFilterKey.FONTS_AIGUA),
        bancs = filterWeight(ProfileFilterKey.BANCS),
        contaminacioAcustica = filterWeight(ProfileFilterKey.CONTAMINACIO_ACUSTICA),
        escalesMecaniques = filterWeight(ProfileFilterKey.ESCALES_MECANIQUES),
        arbres = filterWeight(ProfileFilterKey.ARBRES),
        refugisClimatics = filterWeight(ProfileFilterKey.REFUGIS_CLIMATICS),
        qualitatAire = filterWeight(ProfileFilterKey.QUALITAT_AIRE)
    )
}

internal fun UserFilters.toProfileFiltersUiModel(): ProfileFiltersUiModel {
    return ProfileFiltersUiModel(
        values = profileFilterKeys.map { filterKey ->
            valueFor(filterKey).toSliderValue()
        }
    )
}

private fun ProfileFiltersUiModel.filterWeight(filterKey: ProfileFilterKey): Double {
    return values[profileFilterIndexes.getValue(filterKey)].toFilterWeight()
}

private fun UserFilters.valueFor(filterKey: ProfileFilterKey): Double {
    return when (filterKey) {
        ProfileFilterKey.CAMERES_SEGURETAT -> cameresSeguretat
        ProfileFilterKey.COMISSARIES -> comissaries
        ProfileFilterKey.FETS_PENALS -> fetsPenals
        ProfileFilterKey.INFRACCIONS -> infraccions
        ProfileFilterKey.BANCS -> bancs
        ProfileFilterKey.CONTAMINACIO_ACUSTICA -> contaminacioAcustica
        ProfileFilterKey.ESCALES_MECANIQUES -> escalesMecaniques
        ProfileFilterKey.FONTS_AIGUA -> fontsAigua
        ProfileFilterKey.ARBRES -> arbres
        ProfileFilterKey.QUALITAT_AIRE -> qualitatAire
        ProfileFilterKey.REFUGIS_CLIMATICS -> refugisClimatics
    }
}

private fun Int.toFilterWeight(): Double {
    return FILTER_LEVEL_WEIGHTS[this.coerceIn(0, FILTER_LEVEL_WEIGHTS.lastIndex)]
}

private fun Double.toSliderValue(): Int {
    val clampedValue = coerceIn(0.0, 1.0)
    return when {
        clampedValue < 0.375 -> 0
        clampedValue < 0.625 -> 1
        clampedValue < 0.875 -> 2
        else -> 3
    }
}
