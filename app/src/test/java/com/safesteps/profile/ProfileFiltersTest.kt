package com.safesteps.profile

import com.safesteps.data.UserFilters
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFiltersTest {

    @Test
    fun `backend default weights map to medium slider values`() {
        val uiModel = UserFilters().toProfileFiltersUiModel()

        assertEquals(List(PROFILE_FILTER_COUNT) { 1 }, uiModel.values)
    }

    @Test
    fun `slider values map back to expected backend weights`() {
        val uiModel = ProfileFiltersUiModel(
            values = listOf(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2)
        )

        val filters = uiModel.toUserFilters()

        assertEquals(0.25, filters.cameresSeguretat, 0.0)
        assertEquals(0.5, filters.comissaries, 0.0)
        assertEquals(0.75, filters.fetsPenals, 0.0)
        assertEquals(1.0, filters.infraccions, 0.0)
        assertEquals(0.25, filters.bancs, 0.0)
        assertEquals(0.5, filters.contaminacioAcustica, 0.0)
        assertEquals(0.75, filters.escalesMecaniques, 0.0)
        assertEquals(1.0, filters.fontsAigua, 0.0)
        assertEquals(0.25, filters.arbres, 0.0)
        assertEquals(0.5, filters.qualitatAire, 0.0)
        assertEquals(0.75, filters.refugisClimatics, 0.0)
    }
}
