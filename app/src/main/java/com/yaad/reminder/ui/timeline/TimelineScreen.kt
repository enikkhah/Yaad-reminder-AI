package com.yaad.reminder.ui.timeline

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaad.reminder.data.local.ItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onToggleVoiceRecognition: () -> Unit
) {
    val items by viewModel.timelineItems.collectAsState()
    val inputState by viewModel.inputState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = Color(0xFFFFFFFF),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            },
            bottomBar = {
                ExpandableMinimalFabBar(
                    inputState = inputState,
                    onMicClick = onToggleVoiceRecognition,
                    onExpandClick = viewModel::onExpandInput,
                    onTextChanged = viewModel::onDraftChanged,
                    onSubmit = { viewModel.onSubmitText() }
                )
            }
        ) { innerPadding ->
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "بنویسید یا صحبت کنید...",
                        color = Color(0xFF9CA3AF),
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 28.dp,
                        bottom = 112.dp
                    )
                ) {
                    items(items = items, key = { it.id }) { item ->
                        TimelineItemRow(item = item)
                        HorizontalDivider(
                            color = Color(0xFFF3F4F6),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItemRow(item: ItemEntity) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("fa", "IR")) }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR")) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
    ) {
        Text(
            text = item.originalText,
            color = Color(0xFF111827),
            fontSize = 16.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Normal
        )

        Spacer(modifier = Modifier.height(6.dp))

        val metadataParts = buildList {
            add(item.category.persianLabel)
            item.extractedTime?.let { ts ->
                add("زمان: ${dateFormat.format(Date(ts))}")
            }
            add(timeFormat.format(Date(item.createdAt)))
        }

        Text(
            text = metadataParts.joinToString(" · "),
            color = Color(0xFF6B7280),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ExpandableMinimalFabBar(
    inputState: InputUiState,
    onMicClick: () -> Unit,
    onExpandClick: () -> Unit,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                )
                .then(
                    if (inputState.isExpanded) Modifier.fillMaxWidth()
                    else Modifier.wrapContentWidth()
                ),
            shape = if (inputState.isExpanded) RoundedCornerShape(28.dp) else CircleShape,
            color = Color(0xFFF3F4F6),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            AnimatedContent(
                targetState = inputState.isExpanded,
                label = "ExpandableFabTransition"
            ) { expanded ->
                if (!expanded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF111827))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "ضبط صدا",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = "بنویسید یا صحبت کنید...",
                            color = Color(0xFF6B7280),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { onExpandClick() }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (inputState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = "ورودی صوتی",
                                tint = if (inputState.isListening) Color(0xFFDC2626) else Color(0xFF111827)
                            )
                        }

                        val displayedText = if (inputState.isListening && inputState.partialSpeechText.isNotEmpty()) {
                            inputState.partialSpeechText
                        } else {
                            inputState.draftText
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (displayedText.isEmpty()) {
                                Text(
                                    text = if (inputState.isListening) "در حال شنیدن صدای شما..." else "بنویسید یا صحبت کنید...",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = displayedText,
                                onValueChange = onTextChanged,
                                textStyle = TextStyle(
                                    color = Color(0xFF111827),
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFF1D4ED8)),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        IconButton(
                            onClick = onSubmit,
                            enabled = displayedText.isNotBlank(),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (displayedText.isNotBlank()) Color(0xFF111827)
                                    else Color.Transparent
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "ثبت",
                                tint = if (displayedText.isNotBlank()) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
