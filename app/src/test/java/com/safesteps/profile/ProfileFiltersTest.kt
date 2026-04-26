package com.safesteps.profile

import com.safesteps.data.UserFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFiltersTest {

    @Test
    fun `filter catalog matches the backend contract`() {
        assertEquals(
            listOf(
                "cameresSeguretat",
                "comissaries",
                "fetsPenals",
                "infraccions",
                "bancs",
                "contaminacioAcustica",
                "escalesMecaniques",
                "fontsAigua",
                "arbres",
                "qualitatAire",
                "refugisClimatics"
            ),
            profileFilterKeys.map(ProfileFilterKey::backendName)
        )
        assertEquals(11, PROFILE_FILTER_COUNT)
    }

    @Test
    fun `user filters round trip through the profile ui model`() {
        val filters = UserFilters(
            comissaries = 0.25,
            fetsPenals = 0.5,
            cameresSeguretat = 0.75,
            infraccions = 1.0,
            fontsAigua = 0.25,
            bancs = 0.5,
            contaminacioAcustica = 0.75,
            escalesMecaniques = 1.0,
            arbres = 0.25,
            refugisClimatics = 0.5,
            qualitatAire = 0.75
        )

        val uiModel = filters.toProfileFiltersUiModel()

        assertEquals(
            listOf(2, 0, 1, 3, 1, 2, 3, 0, 0, 2, 1),
            uiModel.values
        )
        assertEquals(filters, uiModel.toUserFilters())
    }
}
