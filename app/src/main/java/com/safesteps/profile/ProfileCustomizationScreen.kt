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
import androidx.compose.ui.draw.clip
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
    onBack: () -> Unit,
    onSave: (UserInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPhoto by remember { mutableStateOf(user.photoUrl) }
    var selectedRouteColor by remember { mutableStateOf(getInitialRouteColor(user.routeColor)) }
    var selectedNameStyle by remember { mutableStateOf(getInitialNameStyle(user.nameStyle)) }

    Scaffold(
        topBar = {
            CustomizationTopBar(onBack)
        },
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
                nameStyle = selectedNameStyle,
                routeColor = selectedRouteColor
            )

            PhotoSelectionSection(
                selectedPhoto = selectedPhoto,
                onPhotoSelected = { selectedPhoto = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ColorSelectionSection(
                selectedColor = selectedRouteColor,
                unlockedPremis = unlockedPremis,
                onColorSelected = { selectedRouteColor = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            NameStyleSelectionSection(
                username = user.username,
                selectedStyle = selectedNameStyle,
                onStyleSelected = { selectedNameStyle = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            SaveButton(
                onClick = {
                    val updatedUser = user.copy(
                        photoUrl = selectedPhoto,
                        routeColor = String.format("#%06X", (0xFFFFFF and selectedRouteColor.toArgb())),
                        nameStyle = getNameStyleString(selectedNameStyle)
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
    nameStyle: FontWeight,
    routeColor: Color
) {
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
                fontWeight = nameStyle,
                color = Color(0xFF23333A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            RouteIndicator(routeColor)
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
private fun RouteIndicator(color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp, 4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
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
    onPhotoSelected: (String?) -> Unit
) {
    val availablePhotos = listOf(
        null,
        "https://api.dicebear.com/7.x/avataaars/svg?seed=Felix",
        "https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka",
        "https://api.dicebear.com/7.x/avataaars/svg?seed=Midnight",
        "https://api.dicebear.com/7.x/avataaars/svg?seed=Jasper",
        "https://api.dicebear.com/7.x/avataaars/svg?seed=Sawyer"
    )

    CustomizationSection(title = appString(R.string.customize_photo)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(availablePhotos) { photoUrl ->
                PhotoItem(
                    photoUrl = photoUrl,
                    isSelected = selectedPhoto == photoUrl,
                    onClick = { onPhotoSelected(photoUrl) }
                )
            }
        }
    }
}

@Composable
private fun PhotoItem(photoUrl: String?, isSelected: Boolean, onClick: () -> Unit) {
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
        }
    }
}

@Composable
private fun ColorSelectionSection(
    selectedColor: Color,
    unlockedPremis: List<com.safesteps.data.PremiResponse>,
    onColorSelected: (Color) -> Unit
) {
    val defaultColor = Color(0xFF2196F3) // Azul por defecto

    val unlockedColors = unlockedPremis.mapNotNull { premi ->
        parseColorFromPremiId(premi.id)
    }

    // Todos los colores posibles de la tabla prizes
    val allPrizeColors = listOf(
        Color(0x00, 0xFF, 0x00),   // R001 - verde
        Color(0xFF, 0xC0, 0xCB),   // R002 - rosa
        Color(0xC8, 0xA2, 0xC8),   // R003 - lila
        Color(0xFF, 0x00, 0x00)    // R004 - rojo
    )

    CustomizationSection(title = appString(R.string.customize_route_color)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            // Azul siempre disponible
            item {
                ColorItem(
                    color = defaultColor,
                    isSelected = selectedColor == defaultColor,
                    isLocked = false,
                    onClick = { onColorSelected(defaultColor) }
                )
            }
            // Colores de premios
            items(allPrizeColors) { prizeColor ->
                val isUnlocked = unlockedColors.any { isSameColor(it, prizeColor) }
                ColorItem(
                    color = prizeColor,
                    isSelected = isSameColor(selectedColor, prizeColor),
                    isLocked = !isUnlocked,
                    onClick = { if (isUnlocked) onColorSelected(prizeColor) }
                )
            }
        }
    }
}

@Composable
private fun ColorItem(
    color: Color,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isLocked) color.copy(alpha = 0.3f) else color)
            .clickable(enabled = !isLocked, onClick = onClick)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) Color.White else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White
            )
        }
        if (isLocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NameStyleSelectionSection(
    username: String,
    selectedStyle: FontWeight,
    onStyleSelected: (FontWeight) -> Unit
) {
    val availableNameStyles = listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
        FontWeight.Bold, FontWeight.ExtraBold
    )

    CustomizationSection(title = appString(R.string.customize_name_style)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            availableNameStyles.forEach { style ->
                NameStyleItem(
                    username = username,
                    style = style,
                    isSelected = selectedStyle == style,
                    onClick = { onStyleSelected(style) }
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