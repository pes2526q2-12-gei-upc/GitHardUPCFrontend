package com.safesteps.profile

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.safesteps.R

internal enum class ProfileFilterKey(
    val backendName: String,
    @param:StringRes val labelResId: Int
) {
    CAMERES_SEGURETAT(
        backendName = "cameresSeguretat",
        labelResId = R.string.profile_filter_security_cameras
    ),
    COMISSARIES(
        backendName = "comissaries",
        labelResId = R.string.profile_filter_police_stations
    ),
    FETS_PENALS(
        backendName = "fetsPenals",
        labelResId = R.string.profile_filter_criminal_incidents
    ),
    INFRACCIONS(
        backendName = "infraccions",
        labelResId = R.string.profile_filter_infractions
    ),
    BANCS(
        backendName = "bancs",
        labelResId = R.string.profile_filter_benches
    ),
    CONTAMINACIO_ACUSTICA(
        backendName = "contaminacioAcustica",
        labelResId = R.string.profile_filter_noise_pollution
    ),
    ESCALES_MECANIQUES(
        backendName = "escalesMecaniques",
        labelResId = R.string.profile_filter_escalators
    ),
    FONTS_AIGUA(
        backendName = "fontsAigua",
        labelResId = R.string.profile_filter_drinking_fountains
    ),
    ARBRES(
        backendName = "arbres",
        labelResId = R.string.profile_filter_trees
    ),
    QUALITAT_AIRE(
        backendName = "qualitatAire",
        labelResId = R.string.profile_filter_air_quality
    ),
    REFUGIS_CLIMATICS(
        backendName = "refugisClimatics",
        labelResId = R.string.profile_filter_climate_shelters
    )
}

internal data class ProfileFilterGroupDefinition(
    @param:StringRes val titleResId: Int,
    @param:StringRes val descriptionResId: Int,
    val icon: ImageVector,
    val accentColor: Color,
    val filters: List<ProfileFilterKey>
)

internal val profileFilterGroups = listOf(
    ProfileFilterGroupDefinition(
        titleResId = R.string.filter_safety,
        descriptionResId = R.string.profile_filter_group_safety_description,
        icon = Icons.Default.Security,
        accentColor = Color(0xFF2A5F8A),
        filters = listOf(
            ProfileFilterKey.CAMERES_SEGURETAT,
            ProfileFilterKey.COMISSARIES,
            ProfileFilterKey.FETS_PENALS,
            ProfileFilterKey.INFRACCIONS
        )
    ),
    ProfileFilterGroupDefinition(
        titleResId = R.string.filter_comfort,
        descriptionResId = R.string.profile_filter_group_comfort_description,
        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
        accentColor = Color(0xFF388E6C),
        filters = listOf(
            ProfileFilterKey.BANCS,
            ProfileFilterKey.CONTAMINACIO_ACUSTICA,
            ProfileFilterKey.ESCALES_MECANIQUES,
            ProfileFilterKey.FONTS_AIGUA
        )
    ),
    ProfileFilterGroupDefinition(
        titleResId = R.string.filter_climate,
        descriptionResId = R.string.profile_filter_group_climate_description,
        icon = Icons.Default.WbSunny,
        accentColor = Color(0xFFD97832),
        filters = listOf(
            ProfileFilterKey.ARBRES,
            ProfileFilterKey.QUALITAT_AIRE,
            ProfileFilterKey.REFUGIS_CLIMATICS
        )
    )
)

internal val profileFilterKeys = buildProfileFilterKeys()

internal val PROFILE_FILTER_COUNT = profileFilterKeys.size

private fun buildProfileFilterKeys(): List<ProfileFilterKey> {
    val keys = profileFilterGroups.flatMap(ProfileFilterGroupDefinition::filters)

    check(keys.size == ProfileFilterKey.entries.size) {
        "Expected ${ProfileFilterKey.entries.size} filters but found ${keys.size}"
    }
    check(keys.distinct().size == keys.size) {
        "Profile filter catalog contains duplicated backend fields"
    }

    return keys
}
