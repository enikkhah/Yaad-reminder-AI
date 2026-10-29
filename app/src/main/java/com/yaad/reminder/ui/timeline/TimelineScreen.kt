package com.yaad.reminder.ui.timeline

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yaad.reminder.data.local.ItemEntity
import com.yaad.reminder.domain.calendar.JalaliCalendar
import kotlinx.coroutines.delay

@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onToggleVoiceRecognition: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val items by viewModel.timelineItems.collectAsState()
    val inputState by viewModel.inputState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Live ticking clock ("زمان اکنون") updated every 15 seconds
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    var showActiveOnly by remember { mutableStateOf(false) }
    var itemForShamsiPicker by remember { mutableStateOf<ItemEntity?>(null) }
    var showDraftShamsiPicker by remember { mutableStateOf(false) }

    var hasExactAlarmPermission by remember {
        mutableStateOf(checkCanScheduleExactAlarms(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasExactAlarmPermission = checkCanScheduleExactAlarms(context)
                nowMillis = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Separate active upcoming reminders from past / non-alarm items relative to "اکنون"
    val activeReminders = remember(items, nowMillis) {
        items
            .filter { it.extractedTime != null && it.extractedTime > nowMillis }
            .sortedBy { it.extractedTime }
    }

    val pastOrGeneralItems = remember(items, nowMillis) {
        items
            .filter { it.extractedTime == null || it.extractedTime <= nowMillis }
            .sortedByDescending { it.extractedTime ?: it.createdAt }
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
                    onOpenShamsiPicker = { showDraftShamsiPicker = true },
                    onClearCustomShamsi = { viewModel.onSetCustomShamsiAlarmForDraft(null) },
                    onTextChanged = viewModel::onDraftChanged,
                    onSubmit = { viewModel.onSubmitText() }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Live Shamsi Header + Active Reminders Summary
                NowShamsiTopHeader(
                    nowMillis = nowMillis,
                    activeCount = activeReminders.size,
                    showActiveOnly = showActiveOnly,
                    onToggleActiveOnly = { showActiveOnly = !showActiveOnly }
                )

                if (!hasExactAlarmPermission) {
                    ExactAlarmPermissionBanner(
                        onOpenAlarmSettings = { openExactAlarmSettings(context) }
                    )
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
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
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 120.dp
                        )
                    ) {
                        // 1. Active Reminders Section (Above "زمان اکنون")
                        if (activeReminders.isNotEmpty()) {
                            item(key = "section-active-header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.NotificationsActive,
                                            contentDescription = null,
                                            tint = Color(0xFF1D4ED8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "یادآورهای فعال (${JalaliCalendar.toPersianDigits(activeReminders.size.toString())})",
                                            color = Color(0xFF1D4ED8),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Text(
                                        text = "مرتب‌شده بر اساس نزدیک‌ترین زنگ",
                                        color = Color(0xFF6B7280),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            items(items = activeReminders, key = { "active-${it.id}" }) { item ->
                                TimelineItemRow(
                                    item = item,
                                    nowMillis = nowMillis,
                                    isActiveAlarm = true,
                                    onOpenShamsiPicker = { itemForShamsiPicker = item },
                                    onCancelAlarm = { viewModel.onCancelItemAlarm(item) },
                                    onDelete = { viewModel.deleteItem(item.id) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // 2. Live "اکنون (Now)" Timeline Divider Marker
                        item(key = "timeline-now-marker") {
                            TimelineNowMarkerRow(nowMillis = nowMillis)
                        }

                        // 3. Past / General Notes Timeline Section (Below "اکنون")
                        if (!showActiveOnly && pastOrGeneralItems.isNotEmpty()) {
                            item(key = "section-past-header") {
                                Text(
                                    text = "تایم‌لاین یادداشت‌ها و یادآورهای گذشته",
                                    color = Color(0xFF6B7280),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            items(items = pastOrGeneralItems, key = { "past-${it.id}" }) { item ->
                                TimelineItemRow(
                                    item = item,
                                    nowMillis = nowMillis,
                                    isActiveAlarm = false,
                                    onOpenShamsiPicker = { itemForShamsiPicker = item },
                                    onCancelAlarm = { viewModel.onCancelItemAlarm(item) },
                                    onDelete = { viewModel.deleteItem(item.id) }
                                )
                                HorizontalDivider(
                                    color = Color(0xFFF3F4F6),
                                    thickness = 1.dp
                                )
                            }
                        } else if (showActiveOnly && activeReminders.isEmpty()) {
                            item(key = "empty-active") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "در حال حاضر هیچ یادآور فعالی در آینده ثبت نشده است.",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shamsi Alarm Picker Modal for rescheduling an existing timeline item
        itemForShamsiPicker?.let { targetItem ->
            ShamsiAlarmPickerDialog(
                initialEpochMillis = targetItem.extractedTime?.takeIf { it > nowMillis }
                    ?: (nowMillis + 60 * 60 * 1000L),
                title = "تنظیم آلارم به تقویم شمسی",
                subtitle = targetItem.originalText,
                onDismiss = { itemForShamsiPicker = null },
                onConfirmEpochMillis = { selectedMillis ->
                    viewModel.onRescheduleItemShamsiAlarm(targetItem, selectedMillis)
                    itemForShamsiPicker = null
                }
            )
        }

        // Shamsi Alarm Picker Modal for setting a custom Shamsi alarm on the new draft
        if (showDraftShamsiPicker) {
            ShamsiAlarmPickerDialog(
                initialEpochMillis = inputState.customShamsiAlarmMillis
                    ?: (nowMillis + 60 * 60 * 1000L),
                title = "انتخاب زمان آلارم (تقویم شمسی)",
                subtitle = inputState.draftText.ifBlank { "یادآور جدید" },
                onDismiss = { showDraftShamsiPicker = false },
                onConfirmEpochMillis = { selectedMillis ->
                    viewModel.onSetCustomShamsiAlarmForDraft(selectedMillis)
                    showDraftShamsiPicker = false
                }
            )
        }
    }
}

@Composable
private fun NowShamsiTopHeader(
    nowMillis: Long,
    activeCount: Int,
    showActiveOnly: Boolean,
    onToggleActiveOnly: () -> Unit
) {
    val nowShamsi = remember(nowMillis) { JalaliCalendar.fromEpochMillis(nowMillis) }
    val clockStr = remember(nowMillis) {
        JalaliCalendar.toPersianDigits(String.format("%02d:%02d", nowShamsi.hour, nowShamsi.minute))
    }

    Surface(
        color = Color(0xFFFAFAFA),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "اکنون · ${nowShamsi.weekdayName} ${JalaliCalendar.toPersianDigits("${nowShamsi.day} ${nowShamsi.monthName} ${nowShamsi.year}")}",
                        color = Color(0xFF111827),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "ساعت فعلی: $clockStr · تقویم رسمی هجری شمسی",
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (showActiveOnly) Color(0xFF1D4ED8) else Color(0xFFEFF6FF),
                modifier = Modifier.clickable { onToggleActiveOnly() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = if (showActiveOnly) Color.White else Color(0xFF1D4ED8),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (showActiveOnly) {
                            "نمایش همه"
                        } else {
                            "${JalaliCalendar.toPersianDigits(activeCount.toString())} یادآور فعال"
                        },
                        color = if (showActiveOnly) Color.White else Color(0xFF1D4ED8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineNowMarkerRow(nowMillis: Long) {
    val nowShamsi = remember(nowMillis) { JalaliCalendar.fromEpochMillis(nowMillis) }
    val timeText = remember(nowMillis) {
        JalaliCalendar.toPersianDigits(
            "${nowShamsi.day} ${nowShamsi.monthName} ${nowShamsi.year} · ساعت ${
                String.format("%02d:%02d", nowShamsi.hour, nowShamsi.minute)
            }"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
        )
        Text(
            text = "خط زمان اکنون · $timeText",
            color = Color(0xFF047857),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFA7F3D0),
            thickness = 1.dp
        )
    }
}

@Composable
private fun TimelineItemRow(
    item: ItemEntity,
    nowMillis: Long,
    isActiveAlarm: Boolean,
    onOpenShamsiPicker: () -> Unit,
    onCancelAlarm: () -> Unit,
    onDelete: () -> Unit
) {
    val createdShamsi = remember(item.createdAt) {
        val dt = JalaliCalendar.fromEpochMillis(item.createdAt)
        JalaliCalendar.toPersianDigits(String.format("%02d:%02d", dt.hour, dt.minute))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isActiveAlarm) Color(0xFFF8FAFC) else Color.Transparent,
        border = if (isActiveAlarm) BorderStroke(1.dp, Color(0xFFDBEAFE)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isActiveAlarm) 14.dp else 4.dp,
                    vertical = 14.dp
                )
        ) {
            // Top Status Row for Active Reminders
            if (isActiveAlarm && item.extractedTime != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                        )
                        Text(
                            text = "یادآور فعال",
                            color = Color(0xFF1D4ED8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "·",
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "زنگ در ${JalaliCalendar.formatRemainingDuration(item.extractedTime, nowMillis)}",
                            color = Color(0xFF047857),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onOpenShamsiPicker,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = "تغییر زمان شمسی",
                                fontSize = 11.sp,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                        IconButton(
                            onClick = onCancelAlarm,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AlarmOff,
                                contentDescription = "لغو آلارم",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.originalText,
                    color = Color(0xFF111827),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontWeight = if (isActiveAlarm) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "حذف",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val shamsiAlarmText = item.extractedTime?.let { ts ->
                    val label = JalaliCalendar.formatShamsiTimelineLabel(ts, nowMillis)
                    if (ts <= nowMillis) "گذشته: $label" else "آلارم شمسی: $label"
                }

                Text(
                    text = buildList {
                        add(item.category.persianLabel)
                        if (shamsiAlarmText != null) add(shamsiAlarmText)
                        add("ثبت: $createdShamsi")
                    }.joinToString(" · "),
                    color = if (isActiveAlarm) Color(0xFF1E40AF) else Color(0xFF6B7280),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )

                if (!isActiveAlarm) {
                    TextButton(
                        onClick = onOpenShamsiPicker,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AlarmAdd,
                            contentDescription = null,
                            tint = Color(0xFF4B5563),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تنظیم آلارم شمسی",
                            fontSize = 11.sp,
                            color = Color(0xFF4B5563)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShamsiAlarmPickerDialog(
    initialEpochMillis: Long,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirmEpochMillis: (Long) -> Unit
) {
    val initialShamsi = remember(initialEpochMillis) {
        JalaliCalendar.fromEpochMillis(initialEpochMillis)
    }

    var selectedYear by remember { mutableIntStateOf(initialShamsi.year) }
    var selectedMonth by remember { mutableIntStateOf(initialShamsi.month) }
    var selectedDay by remember { mutableIntStateOf(initialShamsi.day) }
    var selectedHour by remember { mutableIntStateOf(initialShamsi.hour) }
    var selectedMinute by remember { mutableIntStateOf(initialShamsi.minute) }

    val maxDays = remember(selectedYear, selectedMonth) {
        JalaliCalendar.maxDaysInMonth(selectedYear, selectedMonth)
    }
    if (selectedDay > maxDays) {
        selectedDay = maxDays
    }

    val previewMillis = remember(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute) {
        JalaliCalendar.toEpochMillis(
            jalaliYear = selectedYear,
            jalaliMonth = selectedMonth,
            jalaliDay = selectedDay,
            hour = selectedHour,
            minute = selectedMinute
        )
    }

    val previewShamsi = remember(previewMillis) {
        JalaliCalendar.fromEpochMillis(previewMillis).formatFullWithWeekday(usePersianDigits = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Selected Shamsi Date Banner
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = previewShamsi,
                        color = Color(0xFF1D4ED8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Shamsi Year / Month / Day Selectors
                Text(
                    text = "تاریخ شمسی (روز / ماه / سال):",
                    fontSize = 12.sp,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Day stepper
                    ShamsiNumberStepper(
                        label = "روز",
                        valueText = JalaliCalendar.toPersianDigits(selectedDay.toString()),
                        onDecrement = { if (selectedDay > 1) selectedDay-- else selectedDay = maxDays },
                        onIncrement = { if (selectedDay < maxDays) selectedDay++ else selectedDay = 1 },
                        modifier = Modifier.weight(1f)
                    )

                    // Month stepper
                    ShamsiNumberStepper(
                        label = "ماه",
                        valueText = JalaliCalendar.monthName(selectedMonth),
                        onDecrement = { if (selectedMonth > 1) selectedMonth-- else selectedMonth = 12 },
                        onIncrement = { if (selectedMonth < 12) selectedMonth++ else selectedMonth = 1 },
                        modifier = Modifier.weight(1.3f)
                    )

                    // Year stepper
                    ShamsiNumberStepper(
                        label = "سال",
                        valueText = JalaliCalendar.toPersianDigits(selectedYear.toString()),
                        onDecrement = { selectedYear-- },
                        onIncrement = { selectedYear++ },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Hour & Minute Selectors
                Text(
                    text = "ساعت زنگ آلارم (۲۴ ساعته):",
                    fontSize = 12.sp,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShamsiNumberStepper(
                        label = "دقیقه",
                        valueText = JalaliCalendar.toPersianDigits(String.format("%02d", selectedMinute)),
                        onDecrement = { selectedMinute = (selectedMinute + 55) % 60 },
                        onIncrement = { selectedMinute = (selectedMinute + 5) % 60 },
                        modifier = Modifier.weight(1f)
                    )

                    ShamsiNumberStepper(
                        label = "ساعت",
                        valueText = JalaliCalendar.toPersianDigits(String.format("%02d", selectedHour)),
                        onDecrement = { selectedHour = (selectedHour + 23) % 24 },
                        onIncrement = { selectedHour = (selectedHour + 1) % 24 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmEpochMillis(previewMillis) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111827),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("ثبت آلارم شمسی", fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = Color(0xFF6B7280), fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun ShamsiNumberStepper(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF3F4F6)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 10.sp, color = Color(0xFF6B7280))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onIncrement,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(26.dp)
                ) {
                    Text("+", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
                Text(
                    text = valueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827)
                )
                TextButton(
                    onClick = onDecrement,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(26.dp)
                ) {
                    Text("−", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
            }
        }
    }
}

private fun checkCanScheduleExactAlarms(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }.onFailure {
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}

@Composable
private fun ExactAlarmPermissionBanner(
    onOpenAlarmSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFEF3C7)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "برای عملکرد دقیق زنگ یادآور در اندروید ۱۲+، مجوز آلارم دقیق را فعال کنید.",
                color = Color(0xFF92400E),
                fontSize = 12.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )

            Button(
                onClick = onOpenAlarmSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF111827),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "تنظیمات آلارم",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ExpandableMinimalFabBar(
    inputState: InputUiState,
    onMicClick: () -> Unit,
    onExpandClick: () -> Unit,
    onOpenShamsiPicker: () -> Unit,
    onClearCustomShamsi: () -> Unit,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Show selected custom Shamsi alarm chip above input bar if user picked one
        if (inputState.customShamsiAlarmMillis != null) {
            val formattedShamsi = remember(inputState.customShamsiAlarmMillis) {
                JalaliCalendar.fromEpochMillis(inputState.customShamsiAlarmMillis)
                    .formatFullWithWeekday(usePersianDigits = true)
            }
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable { onOpenShamsiPicker() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "آلارم شمسی: $formattedShamsi",
                        color = Color(0xFF1D4ED8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "حذف",
                        color = Color(0xFFDC2626),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onClearCustomShamsi() }
                    )
                }
            }
        }

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
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )

                        IconButton(
                            onClick = {
                                onExpandClick()
                                onOpenShamsiPicker()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "تنظیم آلارم شمسی",
                                tint = Color(0xFF1D4ED8)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (inputState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = "ورودی صوتی",
                                tint = if (inputState.isListening) Color(0xFFDC2626) else Color(0xFF111827)
                            )
                        }

                        IconButton(
                            onClick = onOpenShamsiPicker,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "انتخاب تاریخ شمسی آلارم",
                                tint = if (inputState.customShamsiAlarmMillis != null) Color(0xFF1D4ED8) else Color(0xFF4B5563)
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
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (displayedText.isEmpty()) {
                                Text(
                                    text = if (inputState.isListening) "در حال شنیدن صدای شما..." else "یادآور یا تاریخ شمسی (مثلاً ۱۰ آبان ساعت ۸)...",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 14.sp
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
                                .size(38.dp)
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
