package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveGiftItem
import com.example.data.model.MomiCreditCatalog
import com.example.data.model.UserWallet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendGiftBottomSheet(
    recipientUsername: String,
    isLiveContext: Boolean,
    wallet: UserWallet?,
    isProcessing: Boolean,
    onSendGift: (LiveGiftItem) -> Unit,
    onOpenWallet: () -> Unit,
    onDismiss: () -> Unit
) {
    val balance = wallet?.balance ?: 0L
    var selectedGift by remember { mutableStateOf(MomiCreditCatalog.gifts.first()) }
    val hasEnoughCredits = balance >= selectedGift.creditsCost

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isLiveContext) "Send Live Gift" else "Send Creator Gift",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "To @$recipientUsername",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onDismiss()
                            onOpenWallet()
                        }
                        .testTag("gift_sheet_balance_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Toll,
                            contentDescription = "Credits balance",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$balance cr",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MomiCreditCatalog.gifts, key = { it.key }) { gift ->
                    val selected = gift.key == selectedGift.key
                    val accent = Color(gift.accentHex)
                    Column(
                        modifier = Modifier
                            .width(108.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) accent.copy(alpha = 0.14f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedGift = gift }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                            .testTag("gift_option_${gift.key}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = gift.emojiLabel, fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = gift.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${gift.creditsCost} credits",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }
            }

            if (!hasEnoughCredits) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "You need ${selectedGift.creditsCost - balance} more credits to send ${selectedGift.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenWallet()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("gift_sheet_buy_credits_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buy Credits", fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    enabled = hasEnoughCredits && !isProcessing,
                    onClick = { onSendGift(selectedGift) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(selectedGift.accentHex),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.25f)
                        .heightIn(min = 48.dp)
                        .testTag("send_gift_confirm_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = "Send ${selectedGift.emojiLabel} (${selectedGift.creditsCost} cr)",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
