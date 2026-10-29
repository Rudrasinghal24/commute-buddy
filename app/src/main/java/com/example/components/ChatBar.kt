package com.example.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DataLoadState
import com.example.lib.ChatMessage
import com.example.lib.ChatTripContext
import com.example.lib.LocalTripContext
import com.example.lib.sendMessage
import com.example.ui.theme.MapsAccentBlue
import com.example.ui.theme.MapsBackground
import com.example.ui.theme.MapsBorder
import com.example.ui.theme.MapsSelectedTint
import com.example.ui.theme.MapsSurface
import com.example.ui.theme.MapsTextPrimary
import com.example.ui.theme.MapsTextSecondary
import kotlinx.coroutines.launch

private val SUGGESTION_CHIPS = listOf(
    "What if I miss this metro?",
    "Is it safe at night?",
    "Leave by what time?"
)

// Fixed bottom ChatBar:
// - Collapsed: white 48px bar with sparkle icon and "Ask about your trip".
// - Expanded: 70% height panel with message list, 3 suggestion chips, input and send button.
// - Strictly uses 8px grid, >=48px touch targets, Lucide 24px stroke-2 icons, and fade-only typing indicator.
@Composable
fun ChatBar(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tripContext = LocalTripContext.current
    val scope = rememberCoroutineScope()

    var inputText by rememberSaveable { mutableStateOf("") }
    var isWaitingForReply by remember { mutableStateOf(false) }
    var chatState by remember { mutableStateOf(DataLoadState.READY) }
    var lastFailedPrompt by remember { mutableStateOf<String?>(null) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                id = "welcome_msg",
                text = "Ask anything about your Delhi NCR trip, metro timings, safety, or when to leave.",
                isUser = false
            )
        )
    }

    fun submitQuestion(questionText: String) {
        val trimmed = questionText.trim()
        if (trimmed.isEmpty() || isWaitingForReply) return

        inputText = ""
        chatState = DataLoadState.READY
        messages.add(
            ChatMessage(
                id = "user_${messages.size}_${System.currentTimeMillis()}",
                text = trimmed,
                isUser = true
            )
        )
        isWaitingForReply = true

        val currentContext = ChatTripContext(
            start = tripContext.startPlace,
            end = tripContext.endPlace,
            vehicle = tripContext.selectedMode,
            selectedRoute = tripContext.selectedRoute,
            reachByTime = tripContext.reachByTime
        )

        scope.launch {
            try {
                val reply = sendMessage(trimmed, currentContext)
                messages.add(
                    ChatMessage(
                        id = "assistant_${messages.size}_${System.currentTimeMillis()}",
                        text = reply,
                        isUser = false
                    )
                )
            } catch (_: Exception) {
                lastFailedPrompt = trimmed
                chatState = DataLoadState.ERROR
            } finally {
                isWaitingForReply = false
            }
        }
    }

    if (!isExpanded) {
        // Collapsed 48px white bar fixed at the bottom
        Surface(
            color = MapsSurface,
            border = BorderStroke(1.dp, MapsBorder),
            modifier = modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp)
                .testTag("chat_bar_collapsed")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandedChange(true) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparkleIcon(
                    tint = MapsAccentBlue,
                    iconSize = 24.dp,
                    contentDesc = "Ask about your trip"
                )
                Text(
                    text = "Ask about your trip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MapsTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        BackHandler { onExpandedChange(false) }

        val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        Surface(
            shape = topShape,
            color = MapsSurface,
            border = BorderStroke(1.dp, MapsBorder),
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.70f)
                .shadow(elevation = 2.dp, shape = topShape, clip = false)
                .testTag("chat_panel_expanded")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Header bar (48dp height)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SparkleIcon(
                        tint = MapsAccentBlue,
                        iconSize = 24.dp,
                        contentDesc = "Trip assistant"
                    )
                    Text(
                        text = "Ask about your trip",
                        style = MaterialTheme.typography.titleMedium,
                        color = MapsTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_chat_panel_button")
                    ) {
                        LucideXIcon(
                            tint = MapsTextSecondary,
                            iconSize = 24.dp,
                            contentDesc = "Collapse chat panel"
                        )
                    }
                }

                HorizontalDivider(color = MapsBorder, thickness = 1.dp)

                // Message list area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    if (chatState == DataLoadState.ERROR) {
                        ListStateView(
                            state = DataLoadState.ERROR,
                            emptyMessage = "Unable to send message",
                            onRetry = {
                                val retryPrompt = lastFailedPrompt
                                chatState = DataLoadState.READY
                                if (retryPrompt != null) {
                                    submitQuestion(retryPrompt)
                                }
                            }
                        )
                    } else if (messages.isEmpty() && !isWaitingForReply) {
                        ListStateView(
                            state = DataLoadState.EMPTY,
                            emptyMessage = "No messages yet",
                            onRetry = {
                                messages.add(
                                    ChatMessage(
                                        id = "welcome_reset",
                                        text = "Ask anything about your Delhi NCR trip, metro timings, safety, or when to leave.",
                                        isUser = false
                                    )
                                )
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_messages_list"),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatBubbleRow(message = msg)
                            }

                            if (isWaitingForReply) {
                                item(key = "typing_indicator") {
                                    TypingIndicatorBubble()
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MapsBorder, thickness = 1.dp)

                // Three suggestion chips above the input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("chat_suggestion_chips"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SUGGESTION_CHIPS.forEachIndexed { idx, suggestion ->
                        OutlinedButton(
                            onClick = { submitQuestion(suggestion) },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MapsBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MapsSurface,
                                contentColor = MapsAccentBlue
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("chat_suggestion_$idx")
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelLarge,
                                color = MapsAccentBlue
                            )
                        }
                    }
                }

                // Input row and Send button (48dp min touch target)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .background(MapsBackground, CircleShape)
                            .border(1.dp, MapsBorder, CircleShape)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Ask about your trip",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MapsTextSecondary
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MapsTextPrimary),
                            cursorBrush = SolidColor(MapsAccentBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_input_field")
                        )
                    }

                    IconButton(
                        onClick = { submitQuestion(inputText) },
                        enabled = inputText.isNotBlank() && !isWaitingForReply,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (inputText.isNotBlank()) MapsAccentBlue else MapsSelectedTint,
                                shape = CircleShape
                            )
                            .testTag("chat_send_button")
                    ) {
                        LucideSendIcon(
                            tint = if (inputText.isNotBlank()) MapsSurface else MapsAccentBlue,
                            iconSize = 24.dp,
                            contentDesc = "Send message"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (message.isUser) MapsAccentBlue else MapsBackground,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(if (message.isUser) "user_bubble" else "assistant_bubble")
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) MapsSurface else MapsTextPrimary
            )
        }
    }
}

// Typing indicator: three grey dots (#5f6368), fade only (no bounce or spring animations)
@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "typing_fade")
    val alpha1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("typing_indicator"),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(
            modifier = Modifier
                .background(MapsBackground, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha1)
                    .background(MapsTextSecondary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha2)
                    .background(MapsTextSecondary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha3)
                    .background(MapsTextSecondary, CircleShape)
            )
        }
    }
}
