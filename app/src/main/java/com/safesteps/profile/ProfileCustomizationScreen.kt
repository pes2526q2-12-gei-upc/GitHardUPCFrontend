package com.safesteps.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.appString
import kotlin.collections.any

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCustomizationScreen(
    user: UserInfo,
    unlockedPremis: List<com.safesteps.data.PremiResponse>,
    avatarCatalog: List<String>,
    colorCatalog: List<ColorPrizeEntry>,
    labelCatalog: List<LabelEntry>,
    onBack: () -> Unit,
    onSave: (UserInfo) -> Unit,
    modifier: Modifier = Modifier
){
    var selectedPhoto by remember { mutableStateOf(user.photoUrl) }
    var selectedRouteColor by remember { mutableStateOf(getInitialRouteColor(user.routeColor)) }
    var selectedColorId by remember { mutableStateOf(user.routeColor?.takeIf { it.startsWith("R") }) }
    var selectedLabelId by remember { mutableStateOf(user.selectedLabel) }

    Scaffold(
        topBar = { CustomizationTopBar(onBack) },
        containerColor = Color(0xFFF4F7F5)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PreviewCard(
                username = user.username,
                photoUrl = selectedPhoto,
                labelText = selectedLabelId?.let { id ->
                    labelCatalog.find { it.id == id }?.labelRes?.let { appString(it) }
                },
                routeColor = selectedRouteColor,
                routeColorId = selectedColorId
            )

            PhotoSelectionSection(
                selectedPhoto = selectedPhoto,
                unlockedPremis = unlockedPremis,
                avatarCatalog = avatarCatalog,
                onPhotoSelected = { selectedPhoto = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ColorSelectionSection(
                selectedColor = selectedRouteColor,
                selectedColorId = selectedColorId,
                unlockedPremis = unlockedPremis,
                colorCatalog = colorCatalog,
                onColorSelected = { color, id ->
                    selectedRouteColor = color
                    selectedColorId = id
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            LabelSelectionSection(
                selectedLabelId = selectedLabelId,
                unlockedPremis = unlockedPremis,
                labelCatalog = labelCatalog,
                onLabelSelected = { selectedLabelId = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            SaveButton(
                onClick = {
                    val colorToSave = selectedColorId
                        ?: String.format("#%06X", (0xFFFFFF and selectedRouteColor.toArgb()))
                    val updatedUser = user.copy(
                        photoUrl = selectedPhoto,
                        routeColor = colorToSave,
                        selectedLabel = selectedLabelId
                    )
                    onSave(updatedUser)
                    onBack()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizationTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = appString(R.string.customize_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = appString(R.string.back)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.White
        )
    )
}


@Composable
private fun PreviewCard(
    username: String,
    photoUrl: String?,
    labelText: String?,
    routeColor: Color,
    routeColorId: String? = null
)  {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileImage(photoUrl, size = 100.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = username,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF23333A)
            )

            if (labelText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5E9F7A),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            RouteIndicator(color = routeColor, colorId = routeColorId)
        }
    }
}

@Composable
private fun ProfileImage(photoUrl: String?, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        border = BorderStroke(2.dp, Color(0xFFE5ECE7)),
        color = Color(0xFFF9FBFA)
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = Color(0xFF6DD29A)
            )
        }
    }
}

@Composable
private fun RouteIndicator(color: Color, colorId: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val brush = patternBrushFromId(colorId)
        Box(
            modifier = Modifier
                .size(40.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .then(
                    when {
                        isRgbFluid(colorId) -> Modifier // pintado dentro
                        brush != null -> Modifier.background(brush)
                        else -> Modifier.background(color)
                    }
                )
        ) {
            if (isRgbFluid(colorId)) {
                RgbFluidCircle(modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = appString(R.string.navigation_follow_route),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF77837D)
        )
    }
}

@Composable
private fun PhotoSelectionSection(
    selectedPhoto: String?,
    unlockedPremis: List<com.safesteps.data.PremiResponse>,
    avatarCatalog: List<String>,
    onPhotoSelected: (String?) -> Unit
) {
    // La foto de Google siempre disponible + avatares desbloqueados con URL real
    val unlockedAvatars = unlockedPremis.filter { premi ->
        prizeTypeFromId(premi.id) == PrizeType.AVATAR &&
                premi.url != null && premi.url != "NONE"
    }

    CustomizationSection(title = appString(R.string.customize_photo)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {

            // Avatares desbloqueados del backend
            items(unlockedAvatars) { premi ->
                PhotoItem(
                    photoUrl = premi.url,
                    isSelected = selectedPhoto == premi.url,
                    isLocked = false,
                    onClick = { onPhotoSelected(premi.url) }
                )
            }
            val unlockedIds = unlockedAvatars.map { it.id }.toSet()
            val lockedIds = avatarCatalog.filter { it !in unlockedIds }
            items(lockedIds) {
                PhotoItem(
                    photoUrl = null,
                    isSelected = false,
                    isLocked = true,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun PhotoItem(
    photoUrl: String?,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        border = BorderStroke(
            width = if (isSelected) 3.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE5ECE7)
        ),
        color = if (photoUrl == null) Color(0xFFF9FBFA) else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = Color(0xFF6DD29A)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun ColorSelectionSection(
    selectedColor: Color,
    selectedColorId: String?,
    unlockedPremis: List<com.safesteps.data.PremiResponse>,
    colorCatalog: List<ColorPrizeEntry>,
    onColorSelected: (Color, String?) -> Unit
) {
    val defaultColor = Color(0xFF2196F3)
    val unlockedIds = unlockedPremis
        .filter { prizeTypeFromId(it.id) == PrizeType.COLOR }
        .map { it.id }
        .toSet()

    CustomizationSection(title = appString(R.string.customize_route_color)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            // Azul por defecto, siempre disponible
            item {
                ColorItem(
                    color = defaultColor,
                    isSelected = selectedColorId == null && isSameColor(selectedColor, defaultColor),
                    isLocked = false,
                    onClick = { onColorSelected(defaultColor, null) }
                )
            }
            items(colorCatalog) { entry ->
                val isUnlocked = entry.id in unlockedIds
                val isSelected = selectedColorId == entry.id
                ColorItem(
                    color = entry.solidColor ?: defaultColor,
                    brush = patternBrushFromId(entry.id),
                    isRgbFluid = isRgbFluid(entry.id),
                    isSelected = isSelected,
                    isLocked = !isUnlocked,
                    onClick = {
                        if (isUnlocked) {
                            onColorSelected(entry.solidColor ?: defaultColor, entry.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorItem(
    color: Color,
    brush: Brush? = null,
    isRgbFluid: Boolean = false,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    val alpha = if (isLocked) 0.3f else 1f
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                when {
                    isRgbFluid -> Modifier // se pinta dentro
                    brush != null -> Modifier.background(brush).alpha(alpha)
                    else -> Modifier.background(color.copy(alpha = alpha))
                }
            )
            .clickable(enabled = !isLocked, onClick = onClick)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isRgbFluid) {
            RgbFluidCircle(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha)
            )
        }
        if (isSelected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
        }
        if (isLocked) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}


@Composable
private fun LabelSelectionSection(
    selectedLabelId: String?,
    unlockedPremis: List<com.safesteps.data.PremiResponse>,
    labelCatalog: List<LabelEntry>,
    onLabelSelected: (String?) -> Unit
) {
    val unlockedIds = unlockedPremis
        .filter { prizeTypeFromId(it.id) == PrizeType.LABEL }
        .map { it.id }
        .toSet()

    CustomizationSection(title = appString(R.string.customize_label)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Opción sin etiqueta
            LabelItem(
                text = appString(R.string.customize_label_none),
                isSelected = selectedLabelId == null,
                isLocked = false,
                onClick = { onLabelSelected(null) }
            )
            labelCatalog.forEach { entry ->
                val isUnlocked = entry.id in unlockedIds
                LabelItem(
                    text = appString(entry.labelRes),
                    isSelected = selectedLabelId == entry.id,
                    isLocked = !isUnlocked,
                    onClick = { if (isUnlocked) onLabelSelected(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun LabelItem(
    text: String,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLocked, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isLocked -> Color(0xFFF0F0F0)
            else -> Color.White
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE5ECE7)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isLocked) Color(0xFFAAAAAA) else Color(0xFF23333A)
            )
            when {
                isSelected -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                isLocked -> Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
@Composable
private fun NameStyleItem(username: String, style: FontWeight, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE5ECE7))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = username,
                fontWeight = style,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SaveButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF23333A)
        )
    ) {
        Text(
            text = appString(R.string.confirm_action),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CustomizationSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF23333A),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

private fun getInitialRouteColor(colorStr: String?): Color {
    return colorStr?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { Color(0xFF4CAF50) }
    } ?: Color(0xFF4CAF50)
}

private fun getInitialNameStyle(styleStr: String?): FontWeight {
    return when (styleStr) {
        "Normal" -> FontWeight.Normal
        "Medium" -> FontWeight.Medium
        "SemiBold" -> FontWeight.SemiBold
        "Bold" -> FontWeight.Bold
        "ExtraBold" -> FontWeight.ExtraBold
        else -> FontWeight.SemiBold
    }
}

private fun getNameStyleString(fontWeight: FontWeight): String {
    return when (fontWeight) {
        FontWeight.Normal -> "Normal"
        FontWeight.Medium -> "Medium"
        FontWeight.SemiBold -> "SemiBold"
        FontWeight.Bold -> "Bold"
        FontWeight.ExtraBold -> "ExtraBold"
        else -> "SemiBold"
    }
}

private fun isSameColor(a: Color, b: Color): Boolean {
    return (a.red - b.red).let { it * it } +
            (a.green - b.green).let { it * it } +
            (a.blue - b.blue).let { it * it } < 0.01f
}