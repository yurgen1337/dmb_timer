package com.example.dmbtimer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val Context.dataStore by preferencesDataStore(name = "dmb_prefs_v2")

object PrefKeys {
    val START_EPOCH = longPreferencesKey("start_epoch")
    val END_EPOCH = longPreferencesKey("end_epoch")
    val SOLDIER_NAME = stringPreferencesKey("soldier_name")
    val RANK = stringPreferencesKey("rank")
    val BRANCH = stringPreferencesKey("branch")
    val UNIT_NUMBER = stringPreferencesKey("unit_number")
    val THEME_NAME = stringPreferencesKey("theme_name")
    val CUSTOM_EVENTS = stringPreferencesKey("custom_events")
}

data class AppThemeColors(
    val name: String,
    val bg: Color,
    val card: Color,
    val primary: Color,
    val accent: Color
)

val ThemesList = listOf(
    AppThemeColors("Цифра / Олива", Color(0xFF0E140F), Color(0xFF19221A), Color(0xFF6B8E23), Color(0xFFD4AF37)),
    AppThemeColors("ВДВ (Небо)", Color(0xFF0B131E), Color(0xFF142133), Color(0xFF2196F3), Color(0xFF4CAF50)),
    AppThemeColors("ВМФ (Флот)", Color(0xFF090D16), Color(0xFF121A2B), Color(0xFF3F51B5), Color(0xFFFFC107)),
    AppThemeColors("Карбон", Color(0xFF111111), Color(0xFF1E1E1E), Color(0xFF757575), Color(0xFFFF9800))
)

val TextPrimary = Color(0xFFF2F5F2)
val TextSecondary = Color(0xFF9AA89C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DmbMainApp()
        }
    }
}

data class SoldierData(
    val startMillis: Long,
    val endMillis: Long,
    val name: String,
    val rank: String,
    val branch: String,
    val unit: String,
    val themeName: String,
    val customEventsRaw: String
)

@Composable
fun DmbMainApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultStart = remember { LocalDate.now().minusDays(180).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val defaultEnd = remember { LocalDate.now().plusDays(185).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }

    val prefsFlow = remember {
        context.dataStore.data.map { prefs ->
            SoldierData(
                startMillis = prefs[PrefKeys.START_EPOCH] ?: defaultStart,
                endMillis = prefs[PrefKeys.END_EPOCH] ?: defaultEnd,
                name = prefs[PrefKeys.SOLDIER_NAME] ?: "Рядовой Иванов",
                rank = prefs[PrefKeys.RANK] ?: "Рядовой",
                branch = prefs[PrefKeys.BRANCH] ?: "Сухопутные войска",
                unit = prefs[PrefKeys.UNIT_NUMBER] ?: "в/ч 12345",
                themeName = prefs[PrefKeys.THEME_NAME] ?: "Цифра / Олива",
                customEventsRaw = prefs[PrefKeys.CUSTOM_EVENTS] ?: ""
            )
        }
    }
    val soldier by prefsFlow.collectAsState(
        initial = SoldierData(defaultStart, defaultEnd, "Рядовой Иванов", "Рядовой", "Сухопутные войска", "в/ч 12345", "Цифра / Олива", "")
    )

    val themeColors = ThemesList.find { it.name == soldier.themeName } ?: ThemesList[0]

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(80L)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = themeColors.bg,
            surface = themeColors.card,
            primary = themeColors.primary,
            secondary = themeColors.accent,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        )
    ) {
        Scaffold(
            containerColor = themeColors.bg,
            bottomBar = {
                NavigationBar(containerColor = themeColors.card) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        label = { Text("Таймер", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Flag, contentDescription = null) },
                        label = { Text("События", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        label = { Text("Календарь", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.MilitaryTech, contentDescription = null) },
                        label = { Text("Погоны", fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Опции", fontSize = 11.sp) }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> TimerScreen(soldier, nowMillis, themeColors)
                    1 -> EventsScreen(soldier, nowMillis, themeColors) { newRaw ->
                        scope.launch { context.dataStore.edit { it[PrefKeys.CUSTOM_EVENTS] = newRaw } }
                    }
                    2 -> CalendarScreen(soldier, nowMillis, themeColors)
                    3 -> RanksScreen(soldier.rank, themeColors) { newRank ->
                        scope.launch { context.dataStore.edit { it[PrefKeys.RANK] = newRank } }
                    }
                    4 -> SettingsScreen(soldier, themeColors) { updated ->
                        scope.launch {
                            context.dataStore.edit {
                                it[PrefKeys.START_EPOCH] = updated.startMillis
                                it[PrefKeys.END_EPOCH] = updated.endMillis
                                it[PrefKeys.SOLDIER_NAME] = updated.name
                                it[PrefKeys.BRANCH] = updated.branch
                                it[PrefKeys.UNIT_NUMBER] = updated.unit
                                it[PrefKeys.THEME_NAME] = updated.themeName
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerScreen(soldier: SoldierData, nowMillis: Long, colors: AppThemeColors) {
    var showPassed by remember { mutableStateOf(false) }

    val totalMillis = (soldier.endMillis - soldier.startMillis).coerceAtLeast(1L)
    val passedMillis = (nowMillis - soldier.startMillis).coerceIn(0L, totalMillis)
    val leftMillis = (soldier.endMillis - nowMillis).coerceAtLeast(0L)

    val progress = (passedMillis.toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0)
    val percentFormatted = String.format(Locale.US, "%.6f%%", progress * 100.0)

    val displayMillis = if (showPassed) passedMillis else leftMillis
    val days = displayMillis / (1000 * 60 * 60 * 24)
    val hours = (displayMillis / (1000 * 60 * 60)) % 24
    val minutes = (displayMillis / (1000 * 60)) % 60
    val seconds = (displayMillis / 1000) % 60

    val passedDays = passedMillis / (1000 * 60 * 60 * 24)
    val leftDays = leftMillis / (1000 * 60 * 60 * 24)

    val armyStatus = when {
        leftDays == 0L -> "ДЕМБЕЛЬ!"
        leftDays <= 10 -> "КОСМОС"
        leftDays <= 100 -> "ДЕД (СТОДНЕВКА)"
        passedDays >= 182 -> "ЧЕРПАК"
        passedDays >= 100 -> "СЛОН"
        else -> "ДУХ"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(soldier.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("${soldier.rank} • ${soldier.branch}", fontSize = 14.sp, color = TextSecondary)
                        Text(soldier.unit, fontSize = 13.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accent.copy(alpha = 0.15f))
                                .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Статус: $armyStatus", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    ShoulderBoardView(rank = soldier.rank, accentColor = colors.accent)
                }
            }
        }

        item {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    drawArc(
                        color = colors.card,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(colors.primary, colors.accent, colors.primary)),
                        startAngle = -90f,
                        sweepAngle = (360f * progress).toFloat(),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (showPassed) "ОТЛУЖЕНО" else "ДО ДЕМБЕЛЯ",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "$days дн.",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = percentFormatted,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .padding(4.dp)
            ) {
                listOf("Осталось служить" to false, "Уже отслужено" to true).forEach { (title, isPassedMode) ->
                    val active = showPassed == isPassedMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) colors.primary else Color.Transparent)
                            .clickable { showPassed = isPassedMode }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (active) Color.White else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeUnitBox(value = days.toString(), label = "ДНЕЙ", cardColor = colors.card, modifier = Modifier.weight(1f))
                TimeUnitBox(value = String.format("%02d", hours), label = "ЧАСОВ", cardColor = colors.card, modifier = Modifier.weight(1f))
                TimeUnitBox(value = String.format("%02d", minutes), label = "МИНУТ", cardColor = colors.card, modifier = Modifier.weight(1f))
                TimeUnitBox(value = String.format("%02d", seconds), label = "СЕКУНД", cardColor = colors.card, modifier = Modifier.weight(1f))
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Детальная сводка (${if (showPassed) "прошло" else "осталось"})", fontWeight = FontWeight.Bold, color = colors.accent)
                    StatRow("Полных недель", "${days / 7}")
                    StatRow("Выходных (Сб и Вс)", "${(days / 7) * 2}")
                    StatRow("Отбоев и подъемов", "$days")
                    StatRow("Приемов пищи", "${days * 3}")
                }
            }
        }
    }
}

@Composable
fun TimeUnitBox(value: String, label: String, cardColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
            Text(label, fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun StatRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}

@Composable
fun ShoulderBoardView(rank: String, accentColor: Color) {
    Canvas(
        modifier = Modifier
            .width(44.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(Color(0xFF3E4E2C))
            .border(1.5.dp, accentColor, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
    ) {
        val w = size.width
        val h = size.height
        drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(w / 2, 12.dp.toPx()))

        when (rank) {
            "Ефрейтор" -> {
                drawRect(accentColor, topLeft = Offset(4.dp.toPx(), h * 0.55f), size = Size(w - 8.dp.toPx(), 6.dp.toPx()))
            }
            "Младший сержант" -> {
                drawRect(accentColor, topLeft = Offset(4.dp.toPx(), h * 0.50f), size = Size(w - 8.dp.toPx(), 6.dp.toPx()))
                drawRect(accentColor, topLeft = Offset(4.dp.toPx(), h * 0.62f), size = Size(w - 8.dp.toPx(), 6.dp.toPx()))
            }
            "Сержант" -> {
                listOf(0.45f, 0.56f, 0.67f).forEach { pos ->
                    drawRect(accentColor, topLeft = Offset(4.dp.toPx(), h * pos), size = Size(w - 8.dp.toPx(), 6.dp.toPx()))
                }
            }
            "Старший сержант" -> {
                drawRect(accentColor, topLeft = Offset(4.dp.toPx(), h * 0.55f), size = Size(w - 8.dp.toPx(), 14.dp.toPx()))
            }
            "Старшина" -> {
                drawRect(accentColor, topLeft = Offset(4.dp.toPx(), h * 0.45f), size = Size(w - 8.dp.toPx(), 14.dp.toPx()))
                drawRect(accentColor, topLeft = Offset((w - 10.dp.toPx()) / 2, h * 0.45f), size = Size(10.dp.toPx(), h * 0.50f))
            }
            "Прапорщик" -> {
                drawCircle(accentColor, radius = 4.5.dp.toPx(), center = Offset(w / 2, h * 0.45f))
                drawCircle(accentColor, radius = 4.5.dp.toPx(), center = Offset(w / 2, h * 0.70f))
            }
            "Лейтенант" -> {
                drawRect(Color(0xFF8B1E1E), topLeft = Offset(w * 0.46f, 18.dp.toPx()), size = Size(3.dp.toPx(), h - 22.dp.toPx()))
                drawCircle(accentColor, radius = 4.5.dp.toPx(), center = Offset(w * 0.30f, h * 0.65f))
                drawCircle(accentColor, radius = 4.5.dp.toPx(), center = Offset(w * 0.70f, h * 0.65f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(soldier: SoldierData, nowMillis: Long, colors: AppThemeColors, onSaveCustom: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(soldier.startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(soldier.endMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    val totalDays = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(1)

    val baseEvents = listOf(
        "Призыв / Убытие в часть" to startDate,
        "Присяга" to startDate.plusDays(21),
        "100 дней службы" to startDate.plusDays(100),
        "Экватор (50% службы)" to startDate.plusDays(totalDays / 2),
        "200 дней службы" to startDate.plusDays(200),
        "Стодневка до приказа" to endDate.minusDays(100),
        "300 дней службы" to startDate.plusDays(300),
        "Космос (10 дней до ДМБ)" to endDate.minusDays(10),
        "ДЕМБЕЛЬ (Точка)" to endDate
    )

    val customEvents = remember(soldier.customEventsRaw) {
        soldier.customEventsRaw.split(";").filter { it.contains("|") }.mapNotNull {
            val parts = it.split("|")
            runCatching { parts[0] to LocalDate.parse(parts[1]) }.getOrNull()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf(today.plusDays(7)) }

    val allEvents = (baseEvents + customEvents).sortedBy { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("События и даты", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }
        items(allEvents) { (title, date) ->
            val diff = ChronoUnit.DAYS.between(today, date)
            val isDone = diff <= 0
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDone) colors.card.copy(alpha = 0.6f) else colors.card),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = if (isDone) colors.primary else TextPrimary)
                        Text(date.format(formatter), fontSize = 13.sp, color = TextSecondary)
                    }
                    Text(
                        text = if (isDone) "Пройдено" else "Через $diff дн.",
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) colors.primary else colors.accent,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = newDate.atStartOfDay(zone).toInstant().toEpochMilli())
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новое событие") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Название (например, Отпуск)") },
                        singleLine = true
                    )
                    Text("Выберите дату ниже:", fontSize = 12.sp, color = TextSecondary)
                    DatePicker(state = dateState, showModeToggle = false)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) {
                        val picked = dateState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        } ?: newDate
                        val updatedList = customEvents + (newTitle.replace("|", "").replace(";", "") to picked)
                        val serialized = updatedList.joinToString(";") { "${it.first}|${it.second}" }
                        onSaveCustom(serialized)
                        newTitle = ""
                    }
                    showAddDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun CalendarScreen(soldier: SoldierData, nowMillis: Long, colors: AppThemeColors) {
    val zone = ZoneId.systemDefault()
    val startDate = Instant.ofEpochMilli(soldier.startMillis).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(soldier.endMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    val monthNames = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")

    val firstDayOfMonth = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()
    val startOffset = firstDayOfMonth.dayOfWeek.value - 1 // Пн = 0

    val cells = buildList<LocalDate?> {
        repeat(startOffset) { add(null) }
        for (day in 1..daysInMonth) {
            add(currentMonth.atDay(day))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
            }
            Text(
                "${monthNames[currentMonth.monthValue - 1]} ${currentMonth.year}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(cells) { date ->
                if (date == null) {
                    Box(modifier = Modifier.aspectRatio(1f))
                } else {
                    val isServiceDay = !date.isBefore(startDate) && !date.isAfter(endDate)
                    val isPassed = isServiceDay && date.isBefore(today)
                    val isToday = date == today
                    val isDmb = date == endDate

                    val bg = when {
                        isDmb -> colors.accent
                        isToday -> colors.primary
                        isPassed -> colors.primary.copy(alpha = 0.35f)
                        isServiceDay -> colors.card
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .border(
                                width = if (isToday || isDmb) 1.5.dp else 0.dp,
                                color = if (isDmb) Color.White else colors.accent,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = if (isDmb) Color.Black else if (isServiceDay) TextPrimary else TextSecondary.copy(alpha = 0.4f),
                            fontWeight = if (isToday || isDmb) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = colors.card), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                LegendDot(colors.primary.copy(alpha = 0.4f), "Отслужено")
                LegendDot(colors.primary, "Сегодня")
                LegendDot(colors.accent, "Дембель")
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
fun RanksScreen(currentRank: String, colors: AppThemeColors, onSelectRank: (String) -> Unit) {
    val ranks = listOf(
        "Рядовой", "Ефрейтор", "Младший сержант", "Сержант",
        "Старший сержант", "Старшина", "Прапорщик", "Лейтенант"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Звания и погоны ВС РФ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Нажмите на звание, чтобы установить его на главный экран", fontSize = 13.sp, color = TextSecondary)
        }
        items(ranks) { rank ->
            val selected = rank == currentRank
            Card(
                colors = CardDefaults.cardColors(containerColor = if (selected) colors.primary.copy(alpha = 0.25f) else colors.card),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (selected) colors.accent else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { onSelectRank(rank) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(rank, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    ShoulderBoardView(rank = rank, accentColor = colors.accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(soldier: SoldierData, colors: AppThemeColors, onSave: (SoldierData) -> Unit) {
    var name by remember(soldier) { mutableStateOf(soldier.name) }
    var branch by remember(soldier) { mutableStateOf(soldier.branch) }
    var unit by remember(soldier) { mutableStateOf(soldier.unit) }
    var themeName by remember(soldier) { mutableStateOf(soldier.themeName) }
    var startMillis by remember(soldier) { mutableLongStateOf(soldier.startMillis) }
    var endMillis by remember(soldier) { mutableLongStateOf(soldier.endMillis) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var savedToast by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val startStr = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate().format(formatter)
    val endStr = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate().format(formatter)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Настройки службы", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("ФИО / Позывной") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                label = { Text("Род войск (ВДВ, ВМФ, РВСН и др.)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Номер части") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Призыв: $startStr", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("ДМБ: $endStr", fontSize = 13.sp)
                }
            }
        }
        item {
            Text("Тема оформления", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemesList.forEach { t ->
                    val active = t.name == themeName
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(t.card)
                            .border(2.dp, if (active) t.accent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { themeName = t.name }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t.name.substringBefore(" "), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    onSave(
                        soldier.copy(
                            name = name,
                            branch = branch,
                            unit = unit,
                            themeName = themeName,
                            startMillis = startMillis,
                            endMillis = endMillis
                        )
                    )
                    savedToast = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Сохранить изменения", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (savedToast) {
                Text("Настройки сохранены!", color = colors.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        startMillis = it
                        endMillis = Instant.ofEpochMilli(it).atZone(zone).toLocalDate().plusYears(1)
                            .atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    showStartPicker = false
                }) { Text("ОК") }
            }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { endMillis = it }
                    showEndPicker = false
                }) { Text("ОК") }
            }
        ) { DatePicker(state = state) }
    }
}
