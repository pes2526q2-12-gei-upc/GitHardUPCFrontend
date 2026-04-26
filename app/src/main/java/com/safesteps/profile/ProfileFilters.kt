package com.safesteps.profile

import com.safesteps.data.UserFilters

internal const val PROFILE_FILTER_COUNT = 11

private val FILTER_LEVEL_WEIGHTS = listOf(0.25, 0.5, 0.75, 1.0)

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
        cameresSeguretat = values[0].toFilterWeight(),
        comissaries = values[1].toFilterWeight(),
        fetsPenals = values[2].toFilterWeight(),
        infraccions = values[3].toFilterWeight(),
        bancs = values[4].toFilterWeight(),
        contaminacioAcustica = values[5].toFilterWeight(),
        escalesMecaniques = values[6].toFilterWeight(),
        fontsAigua = values[7].toFilterWeight(),
        arbres = values[8].toFilterWeight(),
        qualitatAire = values[9].toFilterWeight(),
        refugisClimatics = values[10].toFilterWeight()
    )
}

internal fun UserFilters.toProfileFiltersUiModel(): ProfileFiltersUiModel {
    return ProfileFiltersUiModel(
        values = listOf(
            cameresSeguretat.toSliderValue(),
            comissaries.toSliderValue(),
            fetsPenals.toSliderValue(),
            infraccions.toSliderValue(),
            bancs.toSliderValue(),
            contaminacioAcustica.toSliderValue(),
            escalesMecaniques.toSliderValue(),
            fontsAigua.toSliderValue(),
            arbres.toSliderValue(),
            qualitatAire.toSliderValue(),
            refugisClimatics.toSliderValue()
        )
    )
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
