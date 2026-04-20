package com.safesteps.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.safesteps.R
import com.safesteps.auth.UserInfo
import com.safesteps.i18n.AppLanguage
import com.safesteps.i18n.LanguageSelector
import com.safesteps.i18n.appString

private val filterLevelResIds = listOf(
    R.string.filter_level_low,
    R.string.filter_level_medium,
    R.string.filter_level_high,
    R.string.filter_level_required
)

@Composable
fun ProfileScreen(
    user: UserInfo,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var seguridadExpanded by rememberSaveable { mutableStateOf(false) }
    var confortExpanded by rememberSaveable { mutableStateOf(false) }
    var climaExpanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteBanner by rememberSaveable { mutableStateOf(false) }

    var seguridadValue by rememberSaveable { mutableIntStateOf(0) }
    var confortValue by rememberSaveable { mutableIntStateOf(2) }
    var climaValue by rememberSaveable { mutableIntStateOf(2) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(60.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = appString(R.string.back),
                            tint = Color(0xFF33413B)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appString(R.string.profile_title),
                    color = Color(0xFF23333A),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier.width(60.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                LanguageSelector(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = onLanguageSelected
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileHeader(user = user)

                Spacer(modifier = Modifier.height(28.dp))

                FilterAccordion(
                    title = appString(R.string.filter_safety),
                    selectedLabel = appString(filterLevelResIds[seguridadValue]),
                    expanded = seguridadExpanded,
                    sliderValue = seguridadValue,
                    onExpandedChange = { seguridadExpanded = !seguridadExpanded },
                    onValueChange = { seguridadValue = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                FilterAccordion(
                    title = appString(R.string.filter_comfort),
                    selectedLabel = appString(filterLevelResIds[confortValue]),
                    expanded = confortExpanded,
                    sliderValue = confortValue,
                    onExpandedChange = { confortExpanded = !confortExpanded },
                    onValueChange = { confortValue = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                FilterAccordion(
                    title = appString(R.string.filter_climate),
                    selectedLabel = appString(filterLevelResIds[climaValue]),
                    expanded = climaExpanded,
                    sliderValue = climaValue,
                    onExpandedChange = { climaExpanded = !climaExpanded },
                    onValueChange = { climaValue = it }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            showDeleteBanner = false
                            onLogout()
                        },
                        modifier = Modifier
                            .weight(1.35f)
                            .height(54.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC86A37),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = appString(R.string.log_out),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { showDeleteBanner = true },
                        modifier = Modifier
                            .weight(0.95f)
                            .height(54.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF8E2D8),
                            contentColor = Color(0xFF8F3D1B)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = appString(R.string.delete_account_short),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }

                if (showDeleteBanner) {
                    Spacer(modifier = Modifier.height(16.dp))

                    DeleteAccountBanner(
                        onDismiss = { showDeleteBanner = false },
                        onConfirm = {
                            showDeleteBanner = false
                            onLogout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: UserInfo) {
    if (!user.photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = user.photoUrl,
            contentDescription = appString(R.string.profile_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
        )
    } else {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = Color(0xFF6DD29A)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = user.username,
        color = Color(0xFF23333A),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold
    )

    Spacer(modifier = Modifier.height(6.dp))

    Text(
        text = user.email,
        color = Color(0xFF77837D),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun FilterAccordion(
    title: String,
    selectedLabel: String,
    expanded: Boolean,
    sliderValue: Int,
    onExpandedChange: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE5ECE7), RoundedCornerShape(18.dp))
            .clickable(onClick = onExpandedChange),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF9FBFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFF23333A),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = selectedLabel,
                        color = Color(0xFF77837D),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF5C6A64)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                FilterSlider(
                    value = sliderValue,
                    onValueChange = onValueChange
                )
            }
        }
    }
}

@Composable
private fun FilterSlider(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val selectedLabel = appString(filterLevelResIds[value])

    Text(
        text = appString(R.string.preference_level),
        color = Color(0xFF5C6A64),
        style = MaterialTheme.typography.labelLarge
    )

    Spacer(modifier = Modifier.height(8.dp))

    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0f..3f,
        steps = 2,
        modifier = Modifier.fillMaxWidth()
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = selectedLabel,
            color = Color(0xFF23333A),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DeleteAccountBanner(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF0C9B8), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFF5F0)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = appString(R.string.delete_account_banner_title),
                color = Color(0xFF8F3D1B),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = appString(R.string.delete_account_banner_message),
                color = Color(0xFF5C4034),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE0B6A3)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFFFBF8),
                        contentColor = Color(0xFF8F3D1B)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = appString(R.string.cancel_action),
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBF4F2D),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = appString(R.string.confirm_action),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
