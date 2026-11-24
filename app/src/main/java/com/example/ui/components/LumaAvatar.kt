package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.LocalMomiBrandGradient
import com.example.util.MediaAssets

@Composable
fun LumaAvatar(
    username: String,
    avatarKey: String,
    size: Dp = 40.dp,
    avatarData: String? = null,
    avatarUrl: String? = null,
    hasActiveStory: Boolean = false,
    isSeenStory: Boolean = false,
    showAddBadge: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (startColor, endColor) = MediaAssets.avatarPalette(avatarKey)
    val initials = MediaAssets.initialsForUsername(username)
    val brandGradient = LocalMomiBrandGradient.current
    val decodedBytes = remember(avatarData) { MediaAssets.decodeBase64ToBytes(avatarData) }
    val cleanAvatarUrl = remember(avatarUrl) {
        avatarUrl?.trim()?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
    }

    val ringModifier = when {
        hasActiveStory && !isSeenStory -> Modifier
            .border(
                width = if (size >= 60.dp) 2.5.dp else 2.dp,
                brush = Brush.linearGradient(brandGradient),
                shape = CircleShape
            )
            .padding(if (size >= 60.dp) 4.dp else 3.dp)
        hasActiveStory && isSeenStory -> Modifier
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .padding(if (size >= 60.dp) 4.dp else 3.dp)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(ringModifier)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(startColor, endColor))),
            contentAlignment = Alignment.Center
        ) {
            if (cleanAvatarUrl != null) {
                AsyncImage(
                    model = cleanAvatarUrl,
                    contentDescription = "$username profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (decodedBytes != null) {
                AsyncImage(
                    model = decodedBytes,
                    contentDescription = "$username profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.32f).sp
                )
            }
        }

        if (showAddBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add story",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
