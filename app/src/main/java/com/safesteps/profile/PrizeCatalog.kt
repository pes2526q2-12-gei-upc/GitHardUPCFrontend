package com.safesteps.profile

import com.safesteps.R

data class ColorPrizeEntry(val id: String, val solidColor: androidx.compose.ui.graphics.Color?, val nameRes: Int)

data class LabelEntry(val id: String, val labelRes: Int)

val allAvatarIds = listOf(
    "A001_AVT_BUS_TURISTIC", "A002_AVT_TAXI", "A003_AVT_BICING",
    "A004_AVT_TMB", "A005_AVT_COTORRA", "A006_AVT_PANOT",
    "A007_AVT_PA_AMB_TOMAQUET", "A008_AVT_ARC_TRIOMF",
    "A009_AVT_TORRE_GLORIES", "A010_AVT_HOTEL_W",
    "A011_AVT_FONT_MAGICA", "A012_AVT_PARK_GUELL",
    "A013_AVT_CASA_BATLLO", "A014_AVT_CAMP_NOU",
    "A015_AVT_TIBIDABO", "A016_AVT_SAGRADA_FAMILIA"
)

val allColorPrizes = listOf(
    ColorPrizeEntry("R001_RTCOL_R0G255B0",     androidx.compose.ui.graphics.Color(0, 255, 0),       R.string.color_green),
    ColorPrizeEntry("R002_RTCOL_R255G192B203", androidx.compose.ui.graphics.Color(255, 192, 203),   R.string.color_pink),
    ColorPrizeEntry("R003_RTCOL_R200G162B200", androidx.compose.ui.graphics.Color(200, 162, 200),   R.string.color_lilac),
    ColorPrizeEntry("R004_RTCOL_R255G0B0",     androidx.compose.ui.graphics.Color(255, 0, 0),       R.string.color_red),
    ColorPrizeEntry("R005_RTCOL_R255G165B0",   androidx.compose.ui.graphics.Color(255, 165, 0),     R.string.color_orange),
    ColorPrizeEntry("R006_RTCOL_R0G255B255",   androidx.compose.ui.graphics.Color(0, 255, 255),     R.string.color_cyan),
    ColorPrizeEntry("R007_RTCOL_R255G215B0",   androidx.compose.ui.graphics.Color(255, 215, 0),     R.string.color_gold),
    ColorPrizeEntry("R008_RTCOL_R57G255B20",   androidx.compose.ui.graphics.Color(57, 255, 20),     R.string.color_lime),
    ColorPrizeEntry("R009_RTCOL_R255G127B80",  androidx.compose.ui.graphics.Color(255, 127, 80),    R.string.color_coral),
    ColorPrizeEntry("R010_RTCOL_R0G206B209",   androidx.compose.ui.graphics.Color(0, 206, 209),     R.string.color_turquoise),
    ColorPrizeEntry("R011_RTCOL_R165G0B68",    androidx.compose.ui.graphics.Color(165, 0, 68),      R.string.color_burgundy),
    ColorPrizeEntry("R012_RTCOL_R138G43B226",  androidx.compose.ui.graphics.Color(138, 43, 226),    R.string.color_purple),
    ColorPrizeEntry("R013_RTPAT_BLAUGRANA",    null,                                                R.string.color_blaugrana),
    ColorPrizeEntry("R014_RTPAT_TAXI",         null,                                                R.string.color_taxi),
    ColorPrizeEntry("R015_RTPAT_NIT",          null,                                                R.string.color_night),
    ColorPrizeEntry("R016_RTPAT_RGB_FLUID",    null,                                                R.string.color_rgb_fluid),
)

val allLabels = listOf(
    LabelEntry("T001_TIT_CAMINANTE",         R.string.label_caminante),
    LabelEntry("T002_TIT_EXPLORADOR",        R.string.label_explorador),
    LabelEntry("T003_TIT_VIGIA",             R.string.label_vigia),
    LabelEntry("T004_TIT_GUIA",              R.string.label_guia),
    LabelEntry("T005_TIT_TROTACALLES",       R.string.label_trotacalles),
    LabelEntry("T006_TIT_BRUJULA",           R.string.label_brujula),
    LabelEntry("T007_TIT_CARTOGRAFO",        R.string.label_cartografo),
    LabelEntry("T008_TIT_PISAPANOTS",        R.string.label_pisapanots),
    LabelEntry("T009_TIT_VIGIA_EIXAMPLE",    R.string.label_vigia_eixample),
    LabelEntry("T010_TIT_EXPLORADOR_GOTICO", R.string.label_explorador_gotico),
    LabelEntry("T011_TIT_PASEANTE_GRACIA",   R.string.label_paseante_gracia),
    LabelEntry("T012_TIT_DOMADOR_RONDAS",    R.string.label_domador_rondas),
    LabelEntry("T013_TIT_HEREDERO_CERDA",    R.string.label_heredero_cerda),
    LabelEntry("T014_TIT_ALMA_BLAUGRANA",    R.string.label_alma_blaugrana),
    LabelEntry("T015_TIT_MESTRE_OBRES",      R.string.label_mestre_obres),
    LabelEntry("T016_TIT_LEYENDA_CONDAL",    R.string.label_leyenda_condal),
)