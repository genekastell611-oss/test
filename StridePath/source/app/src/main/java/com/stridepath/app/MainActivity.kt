package com.stridepath.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StridePathRoot() }
    }
}

private data class Palette(val primary: Color, val secondary: Color, val background: Color, val darkBackground: Color, val accent: Color)

private fun palette(id: String): Palette = when (id) {
    "arcade" -> Palette(Color(0xFFFF3CAC), Color(0xFF00E5FF), Color(0xFFFFF5FC), Color(0xFF170018), Color(0xFFFFD400))
    "fantasy" -> Palette(Color(0xFF6D4C9C), Color(0xFF2E7D5B), Color(0xFFFFFBF0), Color(0xFF181326), Color(0xFFD6A93B))
    "cozy" -> Palette(Color(0xFF4D7A55), Color(0xFFC7773C), Color(0xFFFFFAF1), Color(0xFF1D2A21), Color(0xFFE8B34B))
    "cyber" -> Palette(Color(0xFF00A7B5), Color(0xFF7C4DFF), Color(0xFFF2FEFF), Color(0xFF07151C), Color(0xFFFF4ECD))
    "space" -> Palette(Color(0xFF4451C4), Color(0xFF8A4FFF), Color(0xFFF7F7FF), Color(0xFF080B25), Color(0xFFFFC857))
    "monster" -> Palette(Color(0xFFE34B4B), Color(0xFF3A72CF), Color(0xFFFFFBF1), Color(0xFF1D1B25), Color(0xFFFFD54F))
    else -> Palette(Color(0xFF176B4A), Color(0xFF3279A8), Color(0xFFF5FAF6), Color(0xFF071A12), Color(0xFFF4B942))
}

@Composable
private fun StridePathRoot() {
    val context = LocalContext.current
    val store = remember { AppStore(context) }
    var profile by remember { mutableStateOf(store.loadProfile()) }
    var editing by remember { mutableStateOf(profile == null) }
    var themeId by remember { mutableStateOf(store.selectedTheme()) }
    val colors = palette(themeId)
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        tertiary = colors.accent,
        background = colors.darkBackground,
        surface = colors.darkBackground.copy(alpha = 0.94f)
    ) else lightColorScheme(
        primary = colors.primary,
        secondary = colors.secondary,
        tertiary = colors.accent,
        background = colors.background,
        surface = Color.White
    )

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize()) {
            if (editing || profile == null) {
                ProfileEditor(profile) {
                    store.saveProfile(it)
                    profile = it
                    editing = false
                    ReminderScheduler.scheduleAll(context, store)
                    StridePathWidget.updateAll(context)
                }
            } else {
                StridePathApp(profile!!, store, onEditProfile = { editing = true }, onThemeChanged = { themeId = it })
            }
        }
    }
}

private enum class Tab(val label: String) {
    Home("Today"), Food("Food"), Move("Move"), Achievements("Awards"), Progress("Progress")
}

@Composable
private fun StridePathApp(profile: UserProfile, store: AppStore, onEditProfile: () -> Unit, onThemeChanged: (String) -> Unit) {
    val plan = remember(profile) { GoalCalculator.calculate(profile) }
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Home) }
    var refresh by remember { mutableIntStateOf(0) }
    val stats = remember(refresh, profile, selectedTab) { GameEngine.playerStats(store, profile, plan) }
    val context = LocalContext.current
    LaunchedEffect(stats.achievementsUnlocked, refresh) {
        notifyNewAchievements(context, store, GameEngine.achievements(store, profile, plan))
        StridePathWidget.updateAll(context)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val icon = when (tab) {
                        Tab.Home -> Icons.Default.Home
                        Tab.Food -> Icons.Default.Restaurant
                        Tab.Move -> Icons.Default.DirectionsWalk
                        Tab.Achievements -> Icons.Default.EmojiEvents
                        Tab.Progress -> Icons.Default.ShowChart
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab; refresh++ },
                        icon = { Icon(icon, tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            Tab.Home -> HomeScreen(profile, plan, store, stats, Modifier.padding(padding), onEditProfile) { refresh++ }
            Tab.Food -> FoodScreen(profile, plan, store, Modifier.padding(padding)) { refresh++ }
            Tab.Move -> MoveScreen(profile, plan, store, Modifier.padding(padding)) { refresh++ }
            Tab.Achievements -> GameScreen(profile, plan, store, stats, Modifier.padding(padding), onThemeChanged) { refresh++ }
            Tab.Progress -> ProgressScreen(profile, plan, store, Modifier.padding(padding)) { refresh++ }
        }
    }
}

@Composable
private fun ProfileEditor(existing: UserProfile?, onSave: (UserProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.displayName ?: "Player") }
    var weight by rememberSaveable { mutableStateOf(existing?.currentWeightLb?.format0() ?: "") }
    var lose by rememberSaveable { mutableStateOf(existing?.loseLb?.format0() ?: "") }
    var weeks by rememberSaveable { mutableStateOf(existing?.weeks?.toString() ?: "") }
    var steps by rememberSaveable { mutableStateOf(existing?.baselineSteps?.toString() ?: "5000") }
    var feet by rememberSaveable { mutableStateOf(existing?.heightIn?.roundToInt()?.div(12)?.toString() ?: "5") }
    var inches by rememberSaveable { mutableStateOf(existing?.heightIn?.roundToInt()?.rem(12)?.toString() ?: "8") }
    var age by rememberSaveable { mutableStateOf(existing?.age?.toString() ?: "") }
    var water by rememberSaveable { mutableStateOf(existing?.waterGoalFlOz?.toString() ?: "64") }
    var sex by rememberSaveable { mutableStateOf(existing?.sexEstimate ?: SexEstimate.Midpoint) }
    var activity by rememberSaveable { mutableStateOf(existing?.activityLevel ?: ActivityLevel.Light) }
    var adultChecked by rememberSaveable { mutableStateOf(existing != null) }

    val valid = weight.toDoubleOrNull()?.let { it in 80.0..700.0 } == true &&
        lose.toDoubleOrNull()?.let { it > 0 && it < (weight.toDoubleOrNull() ?: 0.0) } == true &&
        weeks.toIntOrNull()?.let { it in 1..260 } == true &&
        steps.toIntOrNull()?.let { it in 0..100000 } == true &&
        feet.toIntOrNull()?.let { it in 4..7 } == true && inches.toIntOrNull()?.let { it in 0..11 } == true &&
        age.toIntOrNull()?.let { it in 18..90 } == true && water.toIntOrNull()?.let { it in 8..256 } == true && adultChecked

    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("STRIDEPATH", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Turn your wellness plan into a campaign you actually want to play.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { OutlinedTextField(name, { name = it.take(20) }, label = { Text("Player name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { NumberField("Campaign starting weight (lb)", weight) { weight = it } }
        item { NumberField("Weight you want to lose (lb)", lose) { lose = it } }
        item { NumberField("Goal timeline (weeks)", weeks) { weeks = it } }
        item { NumberField("Usual daily steps", steps) { steps = it } }
        item { NumberField("Age", age) { age = it } }
        item {
            Text("Height", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { NumberField("Feet", feet) { feet = it } }
                Box(Modifier.weight(1f)) { NumberField("Inches", inches) { inches = it } }
            }
        }
        item {
            Text("Calorie estimate", fontWeight = FontWeight.SemiBold)
            FlowButtons(SexEstimate.entries.map { it.label }, sex.ordinal) { sex = SexEstimate.entries[it] }
        }
        item {
            Text("Usual activity", fontWeight = FontWeight.SemiBold)
            FlowButtons(ActivityLevel.entries.map { it.label }, activity.ordinal) { activity = ActivityLevel.entries[it] }
        }
        item { NumberField("Hydration goal (fl oz/day)", water) { water = it } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(adultChecked, { adultChecked = it })
                Text("I’m 18+ and understand these are planning estimates, not medical advice.")
            }
        }
        item {
            Button(
                enabled = valid,
                onClick = {
                    val startDate = existing?.startDate ?: LocalDate.now().toString()
                    onSave(
                        UserProfile(
                            currentWeightLb = weight.toDouble(), loseLb = lose.toDouble(), weeks = weeks.toInt(), baselineSteps = steps.toInt(),
                            heightIn = feet.toInt() * 12.0 + inches.toInt(), age = age.toInt(), sexEstimate = sex, activityLevel = activity,
                            startDate = startDate, waterGoalFlOz = water.toInt(), displayName = name.ifBlank { "Player" }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (existing == null) "START CAMPAIGN" else "SAVE LOADOUT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
        }
        item { Text("Save new weights from Progress instead of changing the campaign starting weight. StridePath caps its planning deficit and flags goals faster than about 2 lb/week. Calories, macros and burn are estimates. Display units use U.S. customary measures: lb, ft/in, miles and fl oz; macros use grams to match U.S. Nutrition Facts labels.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FlowButtons(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            if (selected == index) Button(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            else OutlinedButton(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}

@Composable
private fun PeriodSelector(selected: TrackingPeriod, onSelect: (TrackingPeriod) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrackingPeriod.entries.forEach { period ->
            if (period == selected) Button(onClick = { onSelect(period) }, modifier = Modifier.weight(1f)) { Text(period.label) }
            else OutlinedButton(onClick = { onSelect(period) }, modifier = Modifier.weight(1f)) { Text(period.label) }
        }
    }
}

@Composable
private fun RatingSelector(value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        (1..5).forEach { score ->
            if (value == score) Button(onClick = { onChange(score) }, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { Text(score.toString()) }
            else OutlinedButton(onClick = { onChange(score) }, Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) { Text(score.toString()) }
        }
    }
}

private fun trackingRange(anchor: LocalDate, period: TrackingPeriod): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    val (start, naturalEnd) = when (period) {
        TrackingPeriod.Day -> anchor to anchor
        TrackingPeriod.Week -> {
            val monday = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            monday to monday.plusDays(6)
        }
        TrackingPeriod.Month -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
    }
    return start to if (naturalEnd.isAfter(today)) today else naturalEnd
}

private fun shiftTrackingDate(anchor: LocalDate, period: TrackingPeriod, amount: Long): LocalDate = when (period) {
    TrackingPeriod.Day -> anchor.plusDays(amount)
    TrackingPeriod.Week -> anchor.plusWeeks(amount)
    TrackingPeriod.Month -> anchor.plusMonths(amount)
}

private fun trackingRangeLabel(anchor: LocalDate, period: TrackingPeriod): String {
    val range = trackingRange(anchor, period)
    return when (period) {
        TrackingPeriod.Day -> if (anchor == LocalDate.now()) "TODAY" else anchor.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        TrackingPeriod.Week -> "${range.first.format(DateTimeFormatter.ofPattern("MMM d"))} – ${range.second.format(DateTimeFormatter.ofPattern("MMM d"))}"
        TrackingPeriod.Month -> anchor.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
}

@Composable
private fun HomeScreen(profile: UserProfile, plan: GoalPlan, store: AppStore, stats: PlayerStats, modifier: Modifier, onEdit: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stepCounter = remember { StepCounterManager(context) }
    val health = remember { HealthConnectSteps(context) }
    var sensorSteps by remember { mutableIntStateOf(store.dailySteps()) }
    var healthSteps by remember { mutableStateOf<Int?>(null) }
    var healthGranted by remember { mutableStateOf(false) }
    var syncText by remember { mutableStateOf("Checking step sources…") }
    var waterToday by remember { mutableIntStateOf(store.waterFlOz()) }
    var sleepHours by remember { mutableStateOf(store.sleepHours()) }
    var sleepQuality by remember { mutableIntStateOf(store.sleepQuality()) }
    var showSleep by remember { mutableStateOf(false) }
    var wellness by remember { mutableStateOf(store.wellness()) }
    var showWellness by remember { mutableStateOf(false) }
    var widgetPinMessage by remember { mutableStateOf("") }
    var activityGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
    }

    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        activityGranted = granted
        if (granted) onRefresh()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            ReminderScheduler.scheduleAll(context, store)
            onRefresh()
        }
    }
    val healthPermission = rememberLauncherForActivityResult(health.permissionContract()) { granted ->
        healthGranted = granted.contains(health.readStepsPermission)
        store.setHealthConnectEnabled(healthGranted)
        if (healthGranted) scope.launch {
            val recent = health.readRecentSteps()
            recent.forEach { (date, steps) -> store.saveDailySteps(steps, date.toString()) }
            healthSteps = recent[LocalDate.now()]
            onRefresh()
        }
    }

    fun syncHealth() {
        scope.launch {
            healthGranted = health.hasPermission()
            if (healthGranted) {
                val recent = health.readRecentSteps()
                recent.forEach { (date, steps) -> store.saveDailySteps(steps, date.toString()) }
                healthSteps = recent[LocalDate.now()]
                syncText = "Health Connect synced ${recent.size} days"
            } else syncText = if (health.isAvailable()) "Health Connect permission available" else "Health Connect unavailable; using phone sensor"
            onRefresh()
        }
    }

    LaunchedEffect(Unit) { syncHealth() }
    DisposableEffect(activityGranted) {
        if (activityGranted) stepCounter.start { steps ->
            sensorSteps = max(sensorSteps, steps)
            store.saveDailySteps(sensorSteps)
            onRefresh()
        }
        onDispose { stepCounter.stop() }
    }

    val todaySteps = max(store.dailySteps(), max(sensorSteps, healthSteps ?: 0))
    val quests = GameEngine.dailyQuests(store, profile, plan)
    val bossHp = GameEngine.bossHpRemaining(todaySteps, plan.balancedStepTarget)
    val latest = store.latestWeight(profile.currentWeightLb)
    val expected = GoalCalculator.expectedWeight(profile)
    val delta = latest - expected
    val caloriesToday = store.loadFood().sumOf { it.calories }
    val exerciseMinutes = store.loadExercise().sumOf { it.minutes }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${profile.displayName.uppercase()} // CAMPAIGN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text("Level ${stats.level} • ${stats.coins} coins • ${stats.walkingStreak}-day walking combo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Settings, "Edit profile") }
            }
        }
        item { PlayerHud(stats) }
        item {
            var pipTaps by rememberSaveable { mutableIntStateOf(0) }
            GamePanel("PIP // DAILY GUIDE", "🐾", emphasized = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BuddyAvatar(stats.level, bossHp, pipTaps, Modifier.size(104.dp).clickable { pipTaps++ }, cosmetic = store.equippedCosmetic())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val pipMessages = GameEngine.buddyMessages(store, profile, plan)
                        Text(pipMessages[pipTaps % pipMessages.size], fontWeight = FontWeight.SemiBold)
                        Text("Tap Pip for another useful update. Pip responds to your steps, hydration, food, workouts, weight path, and streak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            GamePanel("PIP HOME-SCREEN WIDGET", "📱") {
                Text("Keep Pip, steps, calories, hydration, level, and XP on your Android home screen. Tap the widget anytime to open StridePath.")
                Button(onClick = { widgetPinMessage = if (StridePathWidget.requestPin(context)) "Widget request opened" else "Long-press your home screen, choose Widgets, then StridePath RPG" }, Modifier.fillMaxWidth()) { Text("ADD WIDGET") }
                if (widgetPinMessage.isNotBlank()) Text(widgetPinMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            GamePanel("TODAY AT A GLANCE", "✨") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "FOOD", caloriesToday.formatComma(), "Calories")
                    MetricBox(Modifier.weight(1f), "WATER", "$waterToday", "fl oz")
                    MetricBox(Modifier.weight(1f), "SLEEP", if (sleepHours > 0f) "${sleepHours.toDouble().format1()} h" else "—", if (sleepQuality > 0) "$sleepQuality/5 quality" else "not logged")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "ACTIVE", "$exerciseMinutes", "workout min")
                    MetricBox(Modifier.weight(1f), "MOOD", wellness?.mood?.let { "$it/5" } ?: "—", "optional")
                    MetricBox(Modifier.weight(1f), "ENERGY", wellness?.energy?.let { "$it/5" } ?: "—", "optional")
                }
            }
        }
        item {
            GamePanel(title = "TODAY'S STEP BOSS", icon = "👾", emphasized = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${todaySteps.formatComma()} / ${plan.balancedStepTarget.formatComma()} STEPS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 23.sp)
                        Text(if (bossHp == 0) "BOSS DEFEATED • +120 QUEST XP" else "Boss HP: $bossHp% • ${(plan.balancedStepTarget - todaySteps).coerceAtLeast(0).formatComma()} steps remaining")
                    }
                    Text(if (bossHp == 0) "🏆" else "⚔️", fontSize = 42.sp)
                }
                LinearProgressIndicator(progress = { (todaySteps.toFloat() / plan.balancedStepTarget.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Build.VERSION.SDK_INT >= 29 && ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        OutlinedButton(onClick = { activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }, modifier = Modifier.weight(1f)) { Text("Phone sensor") }
                    }
                    if (health.isAvailable() && !healthGranted) {
                        Button(onClick = { healthPermission.launch(setOf(health.readStepsPermission)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.HealthAndSafety, null); Spacer(Modifier.width(4.dp)); Text("Health Connect") }
                    } else if (healthGranted) {
                        OutlinedButton(onClick = { syncHealth() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(4.dp)); Text("Sync steps") }
                    }
                }
                Text(syncText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GamePanel("WEIGHT QUESTLINE", "📉") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBox(Modifier.weight(1f), "ACTUAL", "${latest.format1()} lb", "latest")
                    MetricBox(Modifier.weight(1f), "PROJECTED", "${expected.format1()} lb", "today")
                    MetricBox(Modifier.weight(1f), "DELTA", signed(delta), if (delta <= 0) "at/ahead" else "above path")
                }
                Text("Goal: ${plan.targetWeightLb.format1()} lb over ${profile.weeks} weeks • requested pace ${plan.requestedRatePerWeek.format1()} lb/week")
                if (!plan.isCommonRange) DangerBanner("Timeline is aggressive", "This goal averages ${plan.requestedRatePerWeek.format1()} lb/week. A timeline of about ${plan.minimumWeeksAtTwoPerWeek}+ weeks would keep the average at 2 lb/week or less.")
            }
        }
        item {
            GamePanel("HYDRATION", "💧") {
                Text("$waterToday / ${profile.waterGoalFlOz} fl oz", fontWeight = FontWeight.Bold)
                LinearProgressIndicator(progress = { (waterToday.toFloat() / profile.waterGoalFlOz.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { store.addWater(); waterToday = store.waterFlOz(); onRefresh() }, Modifier.weight(1f)) { Text("+ 8 FL OZ") }
                    OutlinedButton(onClick = { store.removeWater(); waterToday = store.waterFlOz(); onRefresh() }, Modifier.weight(1f)) { Text("UNDO") }
                }
            }
        }
        item {
            GamePanel("RECOVERY CHECK-IN", "🌙") {
                Text(if (sleepHours > 0f) "${sleepHours.toDouble().format1()} hours • quality ${sleepQuality.coerceAtLeast(1)}/5" else "Sleep is not logged for last night.", fontWeight = FontWeight.SemiBold)
                Text("Sleep can affect hunger, energy, and recovery. This optional log helps Pip spot patterns without judging a single night.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showSleep = true }, Modifier.fillMaxWidth()) { Text(if (sleepHours > 0f) "EDIT SLEEP" else "LOG SLEEP") }
            }
        }
        item {
            GamePanel("HOW ARE YOU FEELING?", "🧠") {
                if (wellness == null) Text("A 10-second mood, energy, and stress check-in can help reveal patterns over time.")
                else {
                    Text("Mood ${wellness!!.mood}/5 • Energy ${wellness!!.energy}/5 • Stress ${wellness!!.stress}/5", fontWeight = FontWeight.SemiBold)
                    if (wellness!!.note.isNotBlank()) Text(wellness!!.note, style = MaterialTheme.typography.bodySmall)
                }
                Text("StridePath records your ratings but does not diagnose or score your mental health.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showWellness = true }, Modifier.fillMaxWidth()) { Text(if (wellness == null) "DAILY CHECK-IN" else "EDIT CHECK-IN") }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) item {
            GamePanel("ENABLE QUEST ALERTS", "🔔") {
                Text("Allow notifications for hydration side quests, daily quest reminders, food-log reminders and weekly checkpoints. You can choose which ones stay on in the Game tab.")
                Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("ENABLE NOTIFICATIONS") }
            }
        }
        item {
            GamePanel("DAILY QUEST BOARD", "📜", emphasized = quests.all { it.complete }) {
                Text("${quests.count { it.complete }} / ${quests.size} quests complete", fontSize = 22.sp, fontWeight = FontWeight.Black)
                LinearProgressIndicator(progress = { quests.count { it.complete }.toFloat() / quests.size.coerceAtLeast(1) }, Modifier.fillMaxWidth().height(10.dp))
                Text(if (quests.all { it.complete }) "FULL CLEAR! Base quest XP plus a 200 XP full-clear bonus secured." else "Available quest XP: ${quests.sumOf { it.xp }} • complete what supports you today; no penalty for the rest.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(quests) { QuestCard(it) }
        item {
            GamePanel("NUTRITION HUD", "🍽️") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBox(Modifier.weight(1f), "CAL", plan.mealCalorieTarget.formatComma(), "target")
                    MetricBox(Modifier.weight(1f), "PRO", "${plan.proteinTargetG}g", "target")
                    MetricBox(Modifier.weight(1f), "FIBER", "${plan.fiberTargetG}g", "target")
                }
                Text("Estimated maintenance: ~${plan.estimatedMaintenance.formatComma()} Calories/day. StridePath splits the estimated deficit between food and extra walking instead of trying to make exercise erase meals.")
            }
        }
        item {
            GamePanel("CAMPAIGN RULES", "🛡️") {
                Text("• Missed quests do not remove XP or reset your whole campaign.")
                Text("• Streaks are feedback, not punishment. The next quest is always available.")
                Text("• Calorie and burn numbers are estimates; weight trend over time matters more than a single day.")
                Text("• If the calorie estimate would fall below 1,200 Calories/day, StridePath floors it and flags the plan instead of recommending less.")
            }
        }
    }

    if (showSleep) {
        var hours by remember { mutableStateOf(if (sleepHours > 0f) sleepHours.toString() else "") }
        var quality by remember { mutableIntStateOf(sleepQuality.coerceAtLeast(3)) }
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text("Sleep check-in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField("Hours slept", hours) { hours = it }
                    Text("How restorative did it feel?", fontWeight = FontWeight.SemiBold)
                    RatingSelector(quality) { quality = it }
                }
            },
            confirmButton = { TextButton(enabled = hours.toFloatOrNull()?.let { it in 0.5f..24f } == true, onClick = { store.saveSleep(hours.toFloat(), quality); sleepHours = store.sleepHours(); sleepQuality = store.sleepQuality(); showSleep = false; onRefresh() }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text("CANCEL") } }
        )
    }
    if (showWellness) {
        var mood by remember { mutableIntStateOf(wellness?.mood ?: 3) }
        var energy by remember { mutableIntStateOf(wellness?.energy ?: 3) }
        var stress by remember { mutableIntStateOf(wellness?.stress ?: 3) }
        var note by remember { mutableStateOf(wellness?.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showWellness = false },
            title = { Text("Daily check-in") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mood • low to great", fontWeight = FontWeight.SemiBold); RatingSelector(mood) { mood = it }
                    Text("Energy • drained to energized", fontWeight = FontWeight.SemiBold); RatingSelector(energy) { energy = it }
                    Text("Stress • calm to overwhelmed", fontWeight = FontWeight.SemiBold); RatingSelector(stress) { stress = it }
                    OutlinedTextField(note, { note = it.take(160) }, label = { Text("Optional note") }, minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { store.saveWellness(WellnessCheckIn(LocalDate.now().toString(), mood, energy, stress, note.trim())); wellness = store.wellness(); showWellness = false; onRefresh() }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showWellness = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun PlayerHud(stats: PlayerStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text("LV ${stats.level}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("XP ${stats.xp}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text("🪙 ${stats.coins}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { (stats.xpIntoLevel.toFloat() / stats.xpForNextLevel).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Text("${stats.xpIntoLevel}/${stats.xpForNextLevel} XP toward next level • ${stats.achievementsUnlocked} achievements", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FoodScreen(profile: UserProfile, plan: GoalPlan, store: AppStore, modifier: Modifier, onChanged: () -> Unit) {
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var period by rememberSaveable { mutableStateOf(TrackingPeriod.Day) }
    val selectedDay = runCatching { LocalDate.parse(selectedDate) }.getOrDefault(LocalDate.now())
    val range = trackingRange(selectedDay, period)
    val periodDays = (java.time.temporal.ChronoUnit.DAYS.between(range.first, range.second) + 1).toInt()
    var entries by remember(selectedDate, period) { mutableStateOf(store.loadFoodRange(range.first, range.second)) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingFoodDelete by remember { mutableStateOf<FoodEntry?>(null) }
    var showMealIdeas by rememberSaveable { mutableStateOf(false) }
    val weeklyIdeas = remember(plan.mealCalorieTarget) { buildWeeklyMealPlan(plan.mealCalorieTarget) }
    val total = entries.sumOf { it.calories }
    val protein = entries.sumOf { it.protein }
    val carbs = entries.sumOf { it.carbs }
    val fat = entries.sumOf { it.fat }
    val fiber = entries.sumOf { it.fiber }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("FOOD INVENTORY", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Log meals and review nutrition by day, week, or month. Foods are data—not morality.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { PeriodSelector(period) { period = it } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedDate = shiftTrackingDate(selectedDay, period, -1).toString() }) { Icon(Icons.Default.ChevronLeft, "Previous ${period.label.lowercase()}") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(trackingRangeLabel(selectedDay, period), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    if (selectedDay != LocalDate.now()) TextButton(onClick = { selectedDate = LocalDate.now().toString() }) { Text("Current period") }
                }
                IconButton(enabled = selectedDay.isBefore(LocalDate.now()), onClick = { selectedDate = shiftTrackingDate(selectedDay, period, 1).coerceAtMost(LocalDate.now()).toString() }) { Icon(Icons.Default.ChevronRight, "Next ${period.label.lowercase()}") }
            }
        }
        item {
            val calorieTarget = plan.mealCalorieTarget * periodDays
            GamePanel("${period.label.uppercase()} ENERGY", "🍱", emphasized = true) {
                Text("${total.formatComma()} / ${calorieTarget.formatComma()} Calories", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 27.sp)
                LinearProgressIndicator(progress = { (total.toFloat() / calorieTarget.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(10.dp))
                val remaining = calorieTarget - total
                Text(if (remaining >= 0) "~${remaining.formatComma()} Calories remaining in the planning estimate" else "~${(-remaining).formatComma()} Calories above the planning estimate for this period")
                if (period != TrackingPeriod.Day) Text("Daily average: ${(total.toDouble() / periodDays).roundToInt().formatComma()} Calories", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroBox(Modifier.weight(1f), "PRO", protein, plan.proteinTargetG * periodDays, "g")
                MacroBox(Modifier.weight(1f), "CARB", carbs, plan.carbsTargetG * periodDays, "g")
                MacroBox(Modifier.weight(1f), "FAT", fat, plan.fatTargetG * periodDays, "g")
                MacroBox(Modifier.weight(1f), "FIB", fiber, plan.fiberTargetG * periodDays, "g")
            }
        }
        item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("LOG FOOD / DRINK") } }
        item {
            Text("QUICK LOOT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(meals.take(8)) { meal ->
                    Card(Modifier.width(210.dp).clickable {
                        store.addFood(meal.toFoodEntry(selectedDate))
                        entries = store.loadFoodRange(range.first, range.second); onChanged()
                    }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(meal.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${meal.calories} Calories • ${meal.protein}g protein", style = MaterialTheme.typography.bodySmall)
                            Text("Tap to log", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) item { GamePanel("EMPTY INVENTORY", "🎒") { Text("Nothing logged yet. Add meals, snacks or drinks as you go. You do not need perfect accuracy for the log to be useful.") } }
        items(entries.reversed()) { entry ->
            Card {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.Bold)
                        Text("${if (period == TrackingPeriod.Day) "" else "${entry.date} • "}${entry.mealType} • ${entry.calories} Calories • P ${entry.protein}g • C ${entry.carbs}g • F ${entry.fat}g • Fiber ${entry.fiber}g", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { pendingFoodDelete = entry }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
        item { SevenDayNutrition(store, plan) }
        item {
            OutlinedButton(onClick = { showMealIdeas = !showMealIdeas }, Modifier.fillMaxWidth()) {
                Text(if (showMealIdeas) "HIDE 7-DAY MEAL IDEAS" else "SHOW 7-DAY MEAL IDEAS")
            }
        }
        if (showMealIdeas) items(weeklyIdeas) { day ->
            GamePanel(day.day.uppercase(), "🍽️") {
                Text("${day.totalCalories.formatComma()} Calories • ${day.totalProtein}g protein • ${day.totalFiber}g fiber", fontWeight = FontWeight.Bold)
                day.meals.forEach { meal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${meal.category}: ${meal.name}", fontWeight = FontWeight.SemiBold)
                            Text("${meal.calories} Calories • ${meal.ingredients}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { store.addFood(meal.toFoodEntry(selectedDate)); entries = store.loadFoodRange(range.first, range.second); onChanged() }) { Text("LOG") }
                    }
                }
            }
        }
        item { Text("Nutrition values are estimates and can vary by brand, recipe and serving size. Use package labels when available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    if (showAdd) AddFoodDialog(onDismiss = { showAdd = false }) { entry ->
        store.addFood(entry.copy(date = selectedDate))
        entries = store.loadFoodRange(range.first, range.second); showAdd = false; onChanged()
    }
    pendingFoodDelete?.let { entry ->
        ConfirmDeleteDialog("Delete ${entry.name}?", "This removes the entry from ${entry.date}.", onDismiss = { pendingFoodDelete = null }) {
            store.deleteFood(entry.id); entries = store.loadFoodRange(range.first, range.second); pendingFoodDelete = null; onChanged()
        }
    }
}

@Composable
private fun MacroBox(modifier: Modifier, name: String, value: Int, target: Int, unit: String) {
    Card(modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp)
            Text("$value$unit", fontWeight = FontWeight.Bold)
            Text("/$target$unit", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AddFoodDialog(onDismiss: () -> Unit, onAdd: (FoodEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("0") }
    var carbs by remember { mutableStateOf("0") }
    var fat by remember { mutableStateOf("0") }
    var fiber by remember { mutableStateOf("0") }
    var type by remember { mutableStateOf("Meal") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LOG INVENTORY ITEM", fontFamily = FontFamily.Monospace) },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && calories.toIntOrNull()?.let { it in 1..5000 } == true,
                onClick = {
                    onAdd(FoodEntry(newEntryId(), LocalDate.now().toString(), name.trim(), calories.toInt(), protein.toIntOrNull() ?: 0, type, carbs.toIntOrNull() ?: 0, fat.toIntOrNull() ?: 0, fiber.toIntOrNull() ?: 0))
                }
            ) { Text("ADD") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        text = {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(70) }, label = { Text("Food / meal") }, singleLine = true)
                NumberField("Calories", calories) { calories = it }
                NumberField("Protein (g)", protein) { protein = it }
                NumberField("Carbs (g)", carbs) { carbs = it }
                NumberField("Fat (g)", fat) { fat = it }
                NumberField("Fiber (g)", fiber) { fiber = it }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Meal", "Snack", "Drink").forEach { t ->
                        if (type == t) Button(onClick = { type = t }) { Text(t) } else OutlinedButton(onClick = { type = t }) { Text(t) }
                    }
                }
            }
        }
    )
}

@Composable
private fun SevenDayNutrition(store: AppStore, plan: GoalPlan) {
    val today = LocalDate.now()
    val start = today.minusDays(6)
    val range = store.loadFoodRange(start, today)
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val totals = days.map { d -> range.filter { it.date == d.toString() }.sumOf { it.calories } }
    val logged = totals.filter { it > 0 }
    GamePanel("7-DAY NUTRITION SCORECARD", "📊") {
        Text(if (logged.isEmpty()) "Log a few days to unlock your weekly average." else "Average on logged days: ${logged.average().roundToInt().formatComma()} Calories • planning target ${plan.mealCalorieTarget.formatComma()}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            totals.forEachIndexed { index, total ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(54.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val fraction = if (total == 0) 0.04f else (total.toFloat() / (plan.mealCalorieTarget * 1.3f)).coerceIn(0.05f, 1f)
                        Box(Modifier.fillMaxWidth(0.65f).fillMaxHeight(fraction).background(if (total in (plan.mealCalorieTarget - 250)..(plan.mealCalorieTarget + 250)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, RoundedCornerShape(3.dp)))
                    }
                    Text(days[index].dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MoveScreen(profile: UserProfile, plan: GoalPlan, store: AppStore, modifier: Modifier, onChanged: () -> Unit) {
    var anchorText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var period by rememberSaveable { mutableStateOf(TrackingPeriod.Day) }
    var refresh by remember { mutableIntStateOf(0) }
    var showExercise by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }
    var pendingExerciseDelete by remember { mutableStateOf<ExerciseEntry?>(null) }
    val anchor = runCatching { LocalDate.parse(anchorText) }.getOrDefault(LocalDate.now())
    val range = trackingRange(anchor, period)
    val days = generateSequence(range.first) { it.plusDays(1) }.takeWhile { !it.isAfter(range.second) }.toList()
    val steps = remember(anchorText, period, refresh) { days.map { store.dailySteps(it.toString()) } }
    val exercise = remember(anchorText, period, refresh) { store.loadExerciseRange(range.first, range.second) }
    val totalSteps = steps.sum()
    val goal = plan.balancedStepTarget * days.size.coerceAtLeast(1)
    val minutes = exercise.sumOf { it.minutes }
    val calories = exercise.sumOf { it.calories }
    val strideMiles = totalSteps * (profile.heightIn * 0.414) / 63360.0

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("MOVEMENT", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Phone steps and workouts in one place. Review any day, week, or month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { PeriodSelector(period) { period = it } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { anchorText = shiftTrackingDate(anchor, period, -1).toString() }) { Icon(Icons.Default.ChevronLeft, "Previous period") }
                Text(trackingRangeLabel(anchor, period), Modifier.weight(1f), fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(enabled = anchor.isBefore(LocalDate.now()), onClick = { anchorText = shiftTrackingDate(anchor, period, 1).coerceAtMost(LocalDate.now()).toString() }) { Icon(Icons.Default.ChevronRight, "Next period") }
            }
        }
        item {
            GamePanel("STEP PROGRESS", "👟", emphasized = true) {
                Text("${totalSteps.formatComma()} / ${goal.formatComma()} steps", fontSize = 28.sp, fontWeight = FontWeight.Black)
                LinearProgressIndicator(progress = { (totalSteps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "DISTANCE", "${strideMiles.format1()} mi", "estimated")
                    MetricBox(Modifier.weight(1f), "GOAL DAYS", "${steps.count { it >= plan.balancedStepTarget }}", "of ${days.size}")
                    MetricBox(Modifier.weight(1f), "AVERAGE", (if (steps.isEmpty()) 0 else totalSteps / steps.size).formatComma(), "steps/day")
                }
                MiniBarChart(steps, plan.balancedStepTarget)
                if (period == TrackingPeriod.Day) OutlinedButton(onClick = { showSteps = true }, Modifier.fillMaxWidth()) { Text("EDIT STEPS") }
                Text("Distance uses your estimated stride length. Health Connect is preferred when available; manual corrections stay on your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GamePanel("WORKOUTS", "🏋️") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "SESSIONS", exercise.size.toString(), "logged")
                    MetricBox(Modifier.weight(1f), "ACTIVE", "$minutes min", "logged")
                    MetricBox(Modifier.weight(1f), "ENERGY", calories.formatComma(), "Calories")
                }
                Button(onClick = { showExercise = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.FitnessCenter, null); Spacer(Modifier.width(6.dp)); Text("LOG WORKOUT") }
            }
        }
        item {
            GamePanel("WALKING RAMP", "🗺️") {
                val stageOne = profile.baselineSteps + min(1000, plan.balancedExtraSteps)
                val stageTwo = profile.baselineSteps + min(2500, plan.balancedExtraSteps)
                Text("Start • ${stageOne.formatComma()} steps/day", fontWeight = FontWeight.SemiBold)
                Text("Build • ${stageTwo.formatComma()} steps/day", fontWeight = FontWeight.SemiBold)
                Text("Current goal • ${plan.balancedStepTarget.formatComma()} steps/day", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("Move up by roughly 500–1,000 steps only when the current level feels sustainable. Stop and reassess for pain, dizziness, or unusual shortness of breath.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (exercise.isEmpty()) item { GamePanel("NO WORKOUTS LOGGED", "🌱") { Text("Walking still counts. Add strength training, cycling, chores, sports, or any other intentional movement when useful.") } }
        items(exercise.sortedByDescending { it.id }) { entry ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.Bold)
                        Text("${entry.date} • ${entry.minutes} min${if (entry.calories > 0) " • ${entry.calories} Calories" else ""}", style = MaterialTheme.typography.bodySmall)
                        if (entry.notes.isNotBlank()) Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { pendingExerciseDelete = entry }) { Icon(Icons.Default.Delete, "Delete workout") }
                }
            }
        }
    }

    if (showExercise) AddExerciseDialog(anchor.toString(), onDismiss = { showExercise = false }) {
        store.addExercise(it); showExercise = false; refresh++; onChanged()
    }
    if (showSteps) {
        var value by remember { mutableStateOf(store.dailySteps(anchor.toString()).toString()) }
        AlertDialog(
            onDismissRequest = { showSteps = false },
            title = { Text("Edit steps") },
            text = { Column { NumberField("Steps for ${anchor.format(DateTimeFormatter.ofPattern("MMM d"))}", value) { value = it }; Text("Use this only to correct missing or imported data.", style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(enabled = value.toIntOrNull()?.let { it in 0..150000 } == true, onClick = { store.setDailySteps(value.toInt(), anchor.toString()); showSteps = false; refresh++; onChanged() }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showSteps = false }) { Text("CANCEL") } }
        )
    }
    pendingExerciseDelete?.let { entry ->
        ConfirmDeleteDialog("Delete ${entry.name}?", "This removes the ${entry.minutes}-minute workout from ${entry.date}.", onDismiss = { pendingExerciseDelete = null }) {
            store.deleteExercise(entry.id); pendingExerciseDelete = null; refresh++; onChanged()
        }
    }
}

@Composable
private fun AddExerciseDialog(date: String, onDismiss: () -> Unit, onAdd: (ExerciseEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log workout") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text("Activity") }, placeholder = { Text("Walk, strength, cycling…") }, singleLine = true)
                NumberField("Minutes", minutes) { minutes = it }
                NumberField("Active Calories (optional)", calories) { calories = it }
                OutlinedTextField(notes, { notes = it.take(160) }, label = { Text("Notes (optional)") }, minLines = 2)
                Text("Calorie burn is optional because watches and machines can differ substantially.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && minutes.toIntOrNull()?.let { it in 1..1440 } == true && (calories.isBlank() || calories.toIntOrNull()?.let { it in 0..10000 } == true), onClick = {
                onAdd(ExerciseEntry(newEntryId(), date, name.trim(), minutes.toInt(), calories.toIntOrNull() ?: 0, notes.trim()))
            }) { Text("ADD") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun GameScreen(profile: UserProfile, plan: GoalPlan, store: AppStore, stats: PlayerStats, modifier: Modifier, onThemeChanged: (String) -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val water = store.waterFlOz()
    var reminderRefresh by remember { mutableIntStateOf(0) }
    val achievements = remember(stats.xp, water, taps, reminderRefresh) { GameEngine.achievements(store, profile, plan) }
    val weekly = remember(stats.xp, reminderRefresh) { GameEngine.weeklyQuests(store, profile, plan) }
    val unseen = achievements.filter { it.unlocked && it.id !in store.dismissedAchievementIds() }
    var achievementPopup by remember { mutableStateOf(unseen.firstOrNull()) }
    var buddyName by remember { mutableStateOf(store.buddyName()) }
    var renameBuddy by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val ownedCosmetics = store.ownedCosmetics()
    val equippedCosmetic = store.equippedCosmetic()

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ReminderScheduler.scheduleAll(context, store)
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ACHIEVEMENTS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Your quest history, rewards, Pip, and unlockable themes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { settingsOpen = !settingsOpen }) { Icon(Icons.Default.Notifications, "Notification settings") }
            }
        }
        item { PlayerHud(stats) }
        item {
            GamePanel("$buddyName // COMPANION", "🐾", emphasized = true) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BuddyAvatar(stats.level, GameEngine.bossHpRemaining(store.dailySteps(), plan.balancedStepTarget), taps, Modifier.size(178.dp).clickable { taps++ }, cosmetic = store.equippedCosmetic())
                    val evolution = when {
                        stats.level >= 20 -> "MYTHIC FORM"
                        stats.level >= 10 -> "HERO FORM"
                        stats.level >= 5 -> "SCOUT FORM"
                        else -> "SPROUT FORM"
                    }
                    Text(evolution, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    val pipMessages = GameEngine.buddyMessages(store, profile, plan)
                    Text(pipMessages[taps % pipMessages.size], modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { taps++ }) { Text("TALK") }
                        OutlinedButton(onClick = { renameBuddy = true }) { Text("RENAME") }
                    }
                }
            }
        }
        item {
            Text("PIP EQUIPMENT SHOP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Text("Spend coins earned from XP. Equipment is cosmetic and never changes health goals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(pipCosmetics) { cosmetic ->
            val owned = cosmetic.id in ownedCosmetics
            val levelReady = stats.level >= cosmetic.minLevel
            Card(border = if (equippedCosmetic == cosmetic.id) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(cosmetic.icon, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cosmetic.name, fontWeight = FontWeight.Bold)
                        Text(cosmetic.description, style = MaterialTheme.typography.bodySmall)
                        Text(if (!levelReady) "Unlocks at level ${cosmetic.minLevel}" else if (!owned) "${cosmetic.price} coins" else "Owned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    when {
                        equippedCosmetic == cosmetic.id -> Text("EQUIPPED", fontWeight = FontWeight.Black)
                        owned -> OutlinedButton(onClick = { store.equipCosmetic(cosmetic.id); onChanged() }) { Text("EQUIP") }
                        levelReady -> Button(enabled = stats.coins >= cosmetic.price, onClick = { if (store.buyCosmetic(cosmetic.id, cosmetic.price, stats.coins)) { store.equipCosmetic(cosmetic.id); onChanged() } }) { Text("BUY") }
                        else -> Text("🔒")
                    }
                }
            }
        }
        if (equippedCosmetic != "none") item {
            TextButton(onClick = { store.equipCosmetic("none"); onChanged() }, Modifier.fillMaxWidth()) { Text("UNEQUIP PIP ITEM") }
        }
        item {
            Text("WEEKLY RAIDS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Text("Complete any raid independently. Missing one never removes rewards from another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(weekly) { QuestCard(it) }
        item {
            Text("ACHIEVEMENT CABINET", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Text("${achievements.count { it.unlocked }} / ${achievements.size} unlocked", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(achievements) { achievement -> AchievementCard(achievement) }
        item {
            Text("UNLOCKABLE WORLDS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Text("Themes remix the presentation without using copyrighted game art or characters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(gameThemes) { theme ->
            val unlocked = stats.level >= theme.minLevel
            Card(border = if (store.selectedTheme() == theme.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(theme.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(theme.name, fontWeight = FontWeight.Bold)
                        Text(theme.subtitle, style = MaterialTheme.typography.bodySmall)
                        if (!unlocked) Text("Unlocks at level ${theme.minLevel}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                    if (unlocked) {
                        if (store.selectedTheme() == theme.id) Text("EQUIPPED", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                        else OutlinedButton(onClick = { store.setSelectedTheme(theme.id); onThemeChanged(theme.id); onChanged() }) { Text("EQUIP") }
                    } else Text("🔒")
                }
            }
        }
        if (settingsOpen) item {
            NotificationSettings(profile, store) {
                reminderRefresh++
                ReminderScheduler.scheduleAll(context, store)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    if (achievementPopup != null) {
        val a = achievementPopup!!
        AlertDialog(
            onDismissRequest = { store.markAchievementsSeen(setOf(a.id)); achievementPopup = null },
            title = { Text("ACHIEVEMENT UNLOCKED!", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(a.icon, fontSize = 64.sp); Text(a.title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(a.description); Text("+${a.xp} XP • ${a.rarity}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black) } },
            confirmButton = { Button(onClick = { store.markAchievementsSeen(setOf(a.id)); achievementPopup = null }) { Text("CLAIM") } }
        )
    }

    if (renameBuddy) {
        var newName by remember { mutableStateOf(buddyName) }
        AlertDialog(
            onDismissRequest = { renameBuddy = false },
            title = { Text("Rename companion") },
            text = { OutlinedTextField(newName, { newName = it.take(20) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { buddyName = newName.ifBlank { "Pip" }; store.setBuddyName(buddyName); renameBuddy = false }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { renameBuddy = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun NotificationSettings(profile: UserProfile, store: AppStore, onChange: () -> Unit) {
    var waterEnabled by remember { mutableStateOf(store.waterReminderEnabled()) }
    var interval by remember { mutableIntStateOf(store.waterReminderHours()) }
    var quest by remember { mutableStateOf(store.questReminderEnabled()) }
    var meal by remember { mutableStateOf(store.mealReminderEnabled()) }
    var weigh by remember { mutableStateOf(store.weighInReminderEnabled()) }
    GamePanel("NOTIFICATION LOADOUT", "🔔") {
        ToggleRow("Hydration side quests", "Repeat every $interval hour(s)", waterEnabled) { waterEnabled = it; store.setWaterReminderEnabled(it); onChange() }
        if (waterEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { h ->
                    if (h == interval) Button(onClick = { interval = h; store.setWaterReminderHours(h); onChange() }, Modifier.weight(1f)) { Text("${h}h") }
                    else OutlinedButton(onClick = { interval = h; store.setWaterReminderHours(h); onChange() }, Modifier.weight(1f)) { Text("${h}h") }
                }
            }
        }
        ToggleRow("Morning quest board", "Daily walking quest reminder", quest) { quest = it; store.setQuestReminderEnabled(it); onChange() }
        ToggleRow("Food inventory reminder", "Evening reminder to finish logging", meal) { meal = it; store.setMealReminderEnabled(it); onChange() }
        ToggleRow("Weekly checkpoint", "Sunday optional weigh-in reminder", weigh) { weigh = it; store.setWeighInReminderEnabled(it); onChange() }
        Text("Android may delay inexact reminders to save battery. Notification permission is required on newer Android versions.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AchievementCard(a: Achievement) {
    Card(colors = CardDefaults.cardColors(containerColor = if (a.unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (a.unlocked) a.icon else "🔒", fontSize = 31.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.title, fontWeight = FontWeight.Bold)
                Text(a.description, style = MaterialTheme.typography.bodySmall)
                Text(if (a.unlocked) "+${a.xp} XP • ${a.rarity}" else "${a.rarity} • ${a.xp} XP", style = MaterialTheme.typography.labelSmall, color = if (a.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a.unlocked) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
    }
}

@Composable
private fun BuddyAvatar(level: Int, bossHp: Int, taps: Int, modifier: Modifier = Modifier, cosmetic: String = "none") {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.semantics { contentDescription = "Pip, your evolving StridePath game companion" }) {
        val unit = min(size.width, size.height) / 100f
        val cx = size.width / 2f
        val bounce = if (taps % 2 == 0) 0f else -2.5f * unit
        val headY = 36f * unit + bounce
        val bodyTop = 54f * unit + bounce

        // Aura and grounded game-character shadow.
        drawCircle(if (bossHp == 0) accent.copy(alpha = 0.30f) else primary.copy(alpha = 0.14f), 42f * unit, Offset(cx, 47f * unit))
        drawOval(Color.Black.copy(alpha = 0.24f), Offset(cx - 25f * unit, 87f * unit), Size(50f * unit, 8f * unit))

        // Cape unlocks in hero form.
        if (level >= 10) {
            val cape = Path().apply {
                moveTo(cx - 20f * unit, 55f * unit)
                lineTo(cx - 31f * unit, 84f * unit)
                lineTo(cx, 76f * unit)
                lineTo(cx + 31f * unit, 84f * unit)
                lineTo(cx + 20f * unit, 55f * unit)
                close()
            }
            drawPath(cape, secondary.copy(alpha = 0.9f))
        }

        // Boots, body armor, arms, and belt.
        drawRoundRect(Color(0xFF17241F), Offset(cx - 20f * unit, 78f * unit + bounce), Size(17f * unit, 11f * unit), CornerRadius(4f * unit))
        drawRoundRect(Color(0xFF17241F), Offset(cx + 3f * unit, 78f * unit + bounce), Size(17f * unit, 11f * unit), CornerRadius(4f * unit))
        drawRoundRect(primary, Offset(cx - 22f * unit, bodyTop), Size(44f * unit, 30f * unit), CornerRadius(12f * unit))
        drawCircle(primary, 8f * unit, Offset(cx - 25f * unit, 65f * unit + bounce))
        drawCircle(primary, 8f * unit, Offset(cx + 25f * unit, 65f * unit + bounce))
        drawRoundRect(Color(0xFF263B33), Offset(cx - 22f * unit, 70f * unit + bounce), Size(44f * unit, 6f * unit), CornerRadius(2f * unit))
        drawRect(accent, Offset(cx - 4f * unit, 70f * unit + bounce), Size(8f * unit, 6f * unit))

        // Pointed ears and expressive head.
        val leftEar = Path().apply { moveTo(cx - 24f * unit, headY - 9f * unit); lineTo(cx - 18f * unit, headY - 27f * unit); lineTo(cx - 8f * unit, headY - 14f * unit); close() }
        val rightEar = Path().apply { moveTo(cx + 24f * unit, headY - 9f * unit); lineTo(cx + 18f * unit, headY - 27f * unit); lineTo(cx + 8f * unit, headY - 14f * unit); close() }
        drawPath(leftEar, secondary)
        drawPath(rightEar, secondary)
        drawCircle(primary, 25f * unit, Offset(cx, headY))
        drawCircle(primary.copy(alpha = 0.72f), 6f * unit, Offset(cx - 18f * unit, headY + 11f * unit))
        drawCircle(primary.copy(alpha = 0.72f), 6f * unit, Offset(cx + 18f * unit, headY + 11f * unit))

        // Eyes blink every third tap and the smile changes when interacted with.
        if (taps % 3 == 2) {
            drawLine(Color(0xFF071A12), Offset(cx - 13f * unit, headY - 2f * unit), Offset(cx - 5f * unit, headY - 2f * unit), 2.5f * unit, cap = StrokeCap.Round)
            drawLine(Color(0xFF071A12), Offset(cx + 5f * unit, headY - 2f * unit), Offset(cx + 13f * unit, headY - 2f * unit), 2.5f * unit, cap = StrokeCap.Round)
        } else {
            drawCircle(Color.White, 5.2f * unit, Offset(cx - 9f * unit, headY - 3f * unit))
            drawCircle(Color.White, 5.2f * unit, Offset(cx + 9f * unit, headY - 3f * unit))
            drawCircle(Color(0xFF071A12), 2.4f * unit, Offset(cx - 8f * unit, headY - 2f * unit))
            drawCircle(Color(0xFF071A12), 2.4f * unit, Offset(cx + 8f * unit, headY - 2f * unit))
            drawCircle(Color.White, 0.9f * unit, Offset(cx - 7f * unit, headY - 3f * unit))
            drawCircle(Color.White, 0.9f * unit, Offset(cx + 9f * unit, headY - 3f * unit))
        }
        drawLine(Color.White, Offset(cx - 8f * unit, headY + 9f * unit), Offset(cx, headY + 12f * unit), 2.8f * unit, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(cx, headY + 12f * unit), Offset(cx + 8f * unit, headY + 8f * unit), 2.8f * unit, cap = StrokeCap.Round)

        // Scout sword and mythic crown evolve with level.
        if (level >= 5) {
            drawLine(Color(0xFFE8EEF0), Offset(cx + 25f * unit, 64f * unit + bounce), Offset(cx + 40f * unit, 43f * unit + bounce), 4f * unit, cap = StrokeCap.Round)
            drawLine(accent, Offset(cx + 23f * unit, 57f * unit + bounce), Offset(cx + 34f * unit, 65f * unit + bounce), 4f * unit, cap = StrokeCap.Round)
        }
        if (level >= 20) {
            val crown = Path().apply {
                moveTo(cx - 13f * unit, headY - 22f * unit)
                lineTo(cx - 9f * unit, headY - 34f * unit)
                lineTo(cx, headY - 25f * unit)
                lineTo(cx + 9f * unit, headY - 34f * unit)
                lineTo(cx + 13f * unit, headY - 22f * unit)
                close()
            }
            drawPath(crown, accent)
        }
        when (cosmetic) {
            "shield" -> {
                drawCircle(secondary, 11f * unit, Offset(cx - 28f * unit, 68f * unit + bounce))
                drawCircle(accent, 8f * unit, Offset(cx - 28f * unit, 68f * unit + bounce))
                drawLine(Color.White, Offset(cx - 34f * unit, 68f * unit + bounce), Offset(cx - 22f * unit, 68f * unit + bounce), 2f * unit)
                drawLine(Color.White, Offset(cx - 28f * unit, 62f * unit + bounce), Offset(cx - 28f * unit, 74f * unit + bounce), 2f * unit)
            }
            "headphones" -> {
                drawLine(accent, Offset(cx - 20f * unit, headY - 10f * unit), Offset(cx, headY - 22f * unit), 4f * unit, cap = StrokeCap.Round)
                drawLine(accent, Offset(cx, headY - 22f * unit), Offset(cx + 20f * unit, headY - 10f * unit), 4f * unit, cap = StrokeCap.Round)
                drawRoundRect(Color(0xFF222936), Offset(cx - 27f * unit, headY - 8f * unit), Size(7f * unit, 16f * unit), CornerRadius(3f * unit))
                drawRoundRect(Color(0xFF222936), Offset(cx + 20f * unit, headY - 8f * unit), Size(7f * unit, 16f * unit), CornerRadius(3f * unit))
            }
            "wizard" -> {
                val hat = Path().apply {
                    moveTo(cx - 22f * unit, headY - 19f * unit)
                    lineTo(cx + 20f * unit, headY - 19f * unit)
                    lineTo(cx + 5f * unit, headY - 49f * unit)
                    lineTo(cx - 3f * unit, headY - 28f * unit)
                    close()
                }
                drawPath(hat, secondary)
                drawCircle(accent, 3f * unit, Offset(cx + 3f * unit, headY - 34f * unit))
            }
            "spark" -> {
                listOf(Offset(cx - 42f * unit, 44f * unit), Offset(cx + 43f * unit, 51f * unit), Offset(cx - 35f * unit, 73f * unit)).forEach { point ->
                    drawCircle(accent, 3f * unit, point)
                    drawCircle(Color.White, 1f * unit, point)
                }
            }
        }
        if (bossHp == 0) {
            listOf(Offset(cx - 36f * unit, 19f * unit), Offset(cx + 37f * unit, 25f * unit), Offset(cx + 31f * unit, 8f * unit)).forEachIndexed { index, point ->
                drawCircle(accent.copy(alpha = 0.9f), (if (index == 1) 3f else 2f) * unit, point)
            }
        }
    }
}

@Composable
private fun PlanScreen(profile: UserProfile, plan: GoalPlan, store: AppStore, modifier: Modifier, onChanged: () -> Unit) {
    val weekly = remember(plan.mealCalorieTarget) { buildWeeklyMealPlan(plan.mealCalorieTarget) }
    var expanded by rememberSaveable { mutableIntStateOf(0) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("STRATEGY GUIDE", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Your estimated nutrition targets, weekly menu ideas and walking progression.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            GamePanel("CAMPAIGN TARGETS", "🗺️", emphasized = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "CAL", plan.mealCalorieTarget.formatComma(), "Calories/day")
                    MetricBox(Modifier.weight(1f), "STEPS", plan.balancedStepTarget.formatComma(), "/day")
                    MetricBox(Modifier.weight(1f), "PACE", plan.requestedRatePerWeek.format1(), "lb/week")
                }
                Text("Approx macro HUD: ${plan.proteinTargetG}g protein • ${plan.carbsTargetG}g carbs • ${plan.fatTargetG}g fat • ${plan.fiberTargetG}g fiber")
                if (plan.mealTargetWasFloored) DangerBanner("Calorie floor active", "The estimated target would have gone below 1,200 Calories/day, so StridePath stopped there. A slower timeline is the safer app recommendation.")
            }
        }
        item {
            GamePanel("WALKING LEVEL-UP PATH", "🥾") {
                val extra = plan.balancedExtraSteps
                val stage1 = profile.baselineSteps + min(1000, extra)
                val stage2 = profile.baselineSteps + min(2500, extra)
                Text("Stage 1 • ${stage1.formatComma()} steps/day")
                Text("Stage 2 • ${stage2.formatComma()} steps/day")
                Text("Boss tier • ${plan.balancedStepTarget.formatComma()} steps/day")
                Text("Increase by roughly 500–1,000 steps when the current level feels sustainable. Pain, dizziness, or unusual shortness of breath are reasons to stop and reassess rather than grind through it.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            GamePanel("EATING STRATEGY", "🍎") {
                Text("• Build most meals around a protein source, vegetables/fruit and a high-fiber carb.")
                Text("• Use portion changes before banning favorite foods; consistency usually beats an all-or-nothing reset.")
                Text("• Keep easy high-protein snacks available for busy days.")
                Text("• Log drinks, sauces and snacks when you can—they are easy to forget, not forbidden.")
                Text("• Review weekly averages instead of trying to make every single day land exactly on one number.")
            }
        }
        item { Text("7-DAY MEAL QUEST", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black) }
        items(weekly.indices.toList()) { index ->
            val day = weekly[index]
            Card(border = if (expanded == index) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == index) -1 else index }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row {
                        Text(day.day.uppercase(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Text("${day.totalCalories} Calories • ${day.totalProtein}g P", fontWeight = FontWeight.Bold)
                    }
                    if (expanded == index) {
                        Text("Approx macros: ${day.totalCarbs}g carbs • ${day.totalFat}g fat • ${day.totalFiber}g fiber", style = MaterialTheme.typography.bodySmall)
                        Divider()
                        day.meals.forEach { meal ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${meal.category}: ${meal.name}", fontWeight = FontWeight.Bold)
                                    Text("${meal.calories} Calories • ${meal.protein}g protein", style = MaterialTheme.typography.bodySmall)
                                    Text(meal.ingredients, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { store.addFood(meal.toFoodEntry()); onChanged() }) { Text("LOG") }
                            }
                        }
                    } else Text("Tap to open loadout", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Meal ideas are examples, not a medical diet. Adjust portions, ingredients, allergies, preferences and cultural foods as needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ProgressScreen(profile: UserProfile, plan: GoalPlan, store: AppStore, modifier: Modifier, onChanged: () -> Unit) {
    val context = LocalContext.current
    var weights by remember { mutableStateOf(store.loadWeights()) }
    var showWeight by remember { mutableStateOf(false) }
    var pendingWeightDelete by remember { mutableStateOf<WeightEntry?>(null) }
    var measurements by remember { mutableStateOf(store.loadMeasurements()) }
    var showMeasurement by remember { mutableStateOf(false) }
    var pendingMeasurementDelete by remember { mutableStateOf<BodyMeasurement?>(null) }
    var period by rememberSaveable { mutableStateOf(TrackingPeriod.Week) }
    var exportStatus by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            exportStatus = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(buildHealthCsv(store)) }
                    ?: error("Could not open destination")
                "Export saved"
            }.getOrElse { "Export failed: ${it.message ?: "unknown error"}" }
        }
    }
    val latest = weights.lastOrNull()?.weightLb ?: profile.currentWeightLb
    val expected = GoalCalculator.expectedWeight(profile)
    val lost = (profile.currentWeightLb - latest)
    val goalProgress = (lost / profile.loseLb).coerceIn(0.0, 1.0)
    val today = LocalDate.now()
    val range = trackingRange(today, period)
    val periodDays = generateSequence(range.first) { it.plusDays(1) }.takeWhile { !it.isAfter(range.second) }.toList()
    val stepTotals = periodDays.map { store.dailySteps(it.toString()) }
    val foods = store.loadFoodRange(range.first, range.second)
    val calorieTotals = periodDays.map { d -> foods.filter { it.date == d.toString() }.sumOf { it.calories } }
    val stepAvg = stepTotals.average().roundToInt()
    val loggedCalDays = calorieTotals.filter { it > 0 }
    val workouts = store.loadExerciseRange(range.first, range.second)
    val waterTotals = periodDays.map { store.waterFlOz(it.toString()) }
    val sleepTotals = periodDays.map { store.sleepHours(it.toString()) }.filter { it > 0f }
    val qualityTotals = periodDays.map { store.sleepQuality(it.toString()) }.filter { it > 0 }
    val wellnessTotals = periodDays.mapNotNull { store.wellness(it.toString()) }
    val goalDate = runCatching { LocalDate.parse(profile.startDate).plusWeeks(profile.weeks.toLong()) }.getOrDefault(today)
    val trendPoints = (listOf(WeightEntry(profile.startDate, profile.currentWeightLb)) + weights).mapNotNull { entry ->
        runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry.weightLb }
    }.sortedBy { it.first }
    val trendRate = if (trendPoints.size >= 2) {
        val first = trendPoints.first(); val last = trendPoints.last()
        val days = java.time.temporal.ChronoUnit.DAYS.between(first.first, last.first)
        if (days >= 7) ((first.second - last.second) / days * 7.0) else null
    } else null
    val trendEta = trendRate?.takeIf { it > 0.1 }?.let { rate -> today.plusDays((((latest - plan.targetWeightLb).coerceAtLeast(0.0) / rate) * 7.0).roundToInt().toLong()) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("PROGRESS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Your goal forecast, weight trend, and day/week/month health summaries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { PeriodSelector(period) { period = it } }
        item {
            GamePanel("WEIGHT QUESTLINE", "💾", emphasized = true) {
                Text("${latest.format1()} lb", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 33.sp)
                LinearProgressIndicator(progress = { goalProgress.toFloat() }, Modifier.fillMaxWidth().height(10.dp))
                Text("${max(0.0, lost).format1()} / ${profile.loseLb.format1()} lb toward the campaign goal")
                WeightMilestoneTrack(goalProgress)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "TODAY PATH", "${expected.format1()}", "lb projected")
                    MetricBox(Modifier.weight(1f), "ACTUAL Δ", signed(latest - expected), if (latest <= expected) "ahead/on path" else "above path")
                }
                Button(onClick = { showWeight = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("SAVE WEIGH-IN") }
            }
        }
        item { WeightTrendChart(profile, weights) }
        item {
            val latestWaist = measurements.lastOrNull()?.waistIn
            val waistChange = if (measurements.size >= 2) latestWaist!! - measurements.first().waistIn else null
            GamePanel("BODY MEASUREMENT", "📏") {
                Text(latestWaist?.let { "Latest waist: ${it.format1()} in" } ?: "No waist measurements saved", fontWeight = FontWeight.Bold)
                if (waistChange != null) Text("Change since first measurement: ${if (waistChange > 0) "+" else ""}${waistChange.format1()} in", color = if (waistChange <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Optional measurements can show change that the scale temporarily hides. Measure consistently at the same location and time of day.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showMeasurement = true }, Modifier.fillMaxWidth()) { Text("SAVE WAIST MEASUREMENT") }
                measurements.takeLast(3).reversed().forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.date, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${entry.waistIn.format1()} in", fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { pendingMeasurementDelete = entry }) { Icon(Icons.Default.Delete, "Delete measurement") }
                    }
                }
            }
        }
        item {
            GamePanel("${period.label.uppercase()} MOVEMENT", "👟") {
                Text("Average: ${stepAvg.formatComma()} vs ${plan.balancedStepTarget.formatComma()} target • ${stepTotals.count { it >= plan.balancedStepTarget }}/${periodDays.size} goal days")
                MiniBarChart(stepTotals, plan.balancedStepTarget)
                Text("${workouts.sumOf { it.minutes }} workout minutes across ${workouts.size} session(s)", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            GamePanel("${period.label.uppercase()} NUTRITION", "🍽️") {
                Text(if (loggedCalDays.isEmpty()) "No food days logged yet." else "Average on logged days: ${loggedCalDays.average().roundToInt().formatComma()} vs ${plan.mealCalorieTarget.formatComma()} target")
                MiniBarChart(calorieTotals, plan.mealCalorieTarget)
            }
        }
        item {
            GamePanel("${period.label.uppercase()} RECOVERY", "🌙") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "HYDRATION", "${waterTotals.count { it >= profile.waterGoalFlOz }}/${periodDays.size}", "goal days")
                    MetricBox(Modifier.weight(1f), "SLEEP", if (sleepTotals.isEmpty()) "—" else "${sleepTotals.average().format1()} h", "logged avg")
                    MetricBox(Modifier.weight(1f), "QUALITY", if (qualityTotals.isEmpty()) "—" else "${qualityTotals.average().format1()}/5", "logged avg")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "MOOD", if (wellnessTotals.isEmpty()) "—" else "${wellnessTotals.map { it.mood }.average().format1()}/5", "logged avg")
                    MetricBox(Modifier.weight(1f), "ENERGY", if (wellnessTotals.isEmpty()) "—" else "${wellnessTotals.map { it.energy }.average().format1()}/5", "logged avg")
                    MetricBox(Modifier.weight(1f), "STRESS", if (wellnessTotals.isEmpty()) "—" else "${wellnessTotals.map { it.stress }.average().format1()}/5", "logged avg")
                }
                Text("Averages use logged days only. Missing sleep data is not treated as zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GamePanel("FORECAST", "🗓️") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox(Modifier.weight(1f), "PLAN DATE", goalDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), "original path")
                    MetricBox(Modifier.weight(1f), "TREND", trendRate?.let { "${it.format1()} lb/wk" } ?: "Need data", "record 2+ weeks")
                }
                Text(trendEta?.let { "At the recorded average trend, the goal estimate is around ${it.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}." } ?: "Add weigh-ins across at least 7 days to unlock a personal trend estimate.")
                Text("Forecasts are mathematical estimates, not guarantees. Normal water-weight changes can move short-term results.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            GamePanel("YOUR DATA", "📤") {
                Text("Export up to one year of locally stored steps, food totals, hydration, sleep, workouts, and weigh-ins as a CSV file.")
                Button(onClick = { exportLauncher.launch("StridePath-health-${LocalDate.now()}.csv") }, Modifier.fillMaxWidth()) { Text("EXPORT HEALTH DATA") }
                if (exportStatus.isNotBlank()) Text(exportStatus, style = MaterialTheme.typography.bodySmall, color = if (exportStatus.startsWith("Export saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
        item {
            GamePanel("WHAT 'ACTUAL VS PLAN' MEANS", "📈") {
                Text("Your projected line is a simple planning path, not a promise. Daily body weight can move from water, sodium, digestion and other factors. Look for the multi-week trend before changing the plan based on one weigh-in.")
            }
        }
        if (weights.isNotEmpty()) {
            item { Text("CHECKPOINT HISTORY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black) }
            items(weights.reversed().take(20)) { entry ->
                Card {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.date, Modifier.weight(1f))
                        Text("${entry.weightLb.format1()} lb", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { pendingWeightDelete = entry }) { Icon(Icons.Default.Delete, "Delete weigh-in") }
                    }
                }
            }
        }
    }

    if (showWeight) {
        var value by remember { mutableStateOf(latest.format1()) }
        AlertDialog(
            onDismissRequest = { showWeight = false },
            title = { Text("SAVE CHECKPOINT", fontFamily = FontFamily.Monospace) },
            text = { NumberField("Weight (lb)", value) { value = it } },
            confirmButton = { TextButton(enabled = value.toDoubleOrNull()?.let { it in 60.0..800.0 } == true, onClick = { store.addWeight(WeightEntry(LocalDate.now().toString(), value.toDouble())); weights = store.loadWeights(); showWeight = false; onChanged() }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showWeight = false }) { Text("CANCEL") } }
        )
    }
    pendingWeightDelete?.let { entry ->
        ConfirmDeleteDialog("Delete weigh-in?", "Remove ${entry.weightLb.format1()} lb from ${entry.date}?", onDismiss = { pendingWeightDelete = null }) {
            store.deleteWeight(entry.date); weights = store.loadWeights(); pendingWeightDelete = null; onChanged()
        }
    }
    if (showMeasurement) {
        var value by remember { mutableStateOf(measurements.lastOrNull()?.waistIn?.format1().orEmpty()) }
        AlertDialog(
            onDismissRequest = { showMeasurement = false },
            title = { Text("Waist measurement") },
            text = { Column { NumberField("Waist (inches)", value) { value = it }; Text("Use the same measurement location each time for a more useful trend.", style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(enabled = value.toDoubleOrNull()?.let { it in 10.0..100.0 } == true, onClick = { store.addMeasurement(BodyMeasurement(LocalDate.now().toString(), value.toDouble())); measurements = store.loadMeasurements(); showMeasurement = false; onChanged() }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { showMeasurement = false }) { Text("CANCEL") } }
        )
    }
    pendingMeasurementDelete?.let { entry ->
        ConfirmDeleteDialog("Delete measurement?", "Remove ${entry.waistIn.format1()} in from ${entry.date}?", onDismiss = { pendingMeasurementDelete = null }) {
            store.deleteMeasurement(entry.date); measurements = store.loadMeasurements(); pendingMeasurementDelete = null; onChanged()
        }
    }
}

@Composable
private fun WeightTrendChart(profile: UserProfile, weights: List<WeightEntry>) {
    val entries = if (weights.isEmpty()) listOf(WeightEntry(profile.startDate, profile.currentWeightLb)) else listOf(WeightEntry(profile.startDate, profile.currentWeightLb)) + weights
    val actualColor = MaterialTheme.colorScheme.primary
    val planColor = MaterialTheme.colorScheme.tertiary
    GamePanel("WEIGHT MAP", "🗺️") {
        Canvas(Modifier.fillMaxWidth().height(180.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)).padding(8.dp)) {
            val startDate = runCatching { LocalDate.parse(profile.startDate) }.getOrDefault(LocalDate.now())
            val endDate = startDate.plusWeeks(profile.weeks.toLong())
            val minW = min(profile.goalWeightLb, entries.minOf { it.weightLb }) - 3.0
            val maxW = max(profile.currentWeightLb, entries.maxOf { it.weightLb }) + 3.0
            fun x(date: LocalDate): Float {
                val total = max(1L, java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate))
                val elapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, date).coerceIn(0, total)
                return (elapsed.toFloat() / total) * size.width
            }
            fun y(weight: Double): Float = size.height - (((weight - minW) / (maxW - minW)).coerceIn(0.0, 1.0).toFloat() * size.height)
            drawLine(planColor, Offset(0f, y(profile.currentWeightLb)), Offset(size.width, y(profile.goalWeightLb)), strokeWidth = 5f, cap = StrokeCap.Round)
            val parsed = entries.mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e.weightLb } }.sortedBy { it.first }
            parsed.zipWithNext().forEach { (a, b) -> drawLine(actualColor, Offset(x(a.first), y(a.second)), Offset(x(b.first), y(b.second)), strokeWidth = 7f, cap = StrokeCap.Round) }
            parsed.forEach { (date, weight) -> drawCircle(actualColor, 7f, Offset(x(date), y(weight))) }
        }
        Text("Primary = actual checkpoints • accent line = projected path", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeightMilestoneTrack(progress: Double) {
    val milestones = listOf(0.10 to "10%", 0.25 to "25%", 0.50 to "50%", 0.75 to "75%", 1.0 to "GOAL")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        milestones.forEach { (target, label) ->
            val unlocked = progress >= target
            Card(
                Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = if (unlocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (unlocked) "★" else "◇", color = if (unlocked) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MiniBarChart(values: List<Int>, target: Int) {
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { value ->
            val fraction = if (value == 0) 0.03f else (value.toFloat() / (target * 1.25f).coerceAtLeast(1f)).coerceIn(0.05f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight(fraction).background(if (value >= target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
        }
    }
}

@Composable
private fun QuestCard(quest: DailyQuest) {
    Card(border = if (quest.complete) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(quest.icon, fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) { Text(quest.title, fontWeight = FontWeight.Bold); Text(quest.description, style = MaterialTheme.typography.bodySmall) }
                Text(if (quest.complete) "+${quest.xp} XP ✓" else "${quest.current.formatComma()}/${quest.target.formatComma()}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            }
            LinearProgressIndicator(progress = { quest.progress }, Modifier.fillMaxWidth().height(7.dp))
        }
    }
}

@Composable
private fun GamePanel(title: String, icon: String, emphasized: Boolean = false, content: @Composable () -> Unit) {
    Card(
        colors = if (emphasized) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
        border = BorderStroke(if (emphasized) 2.dp else 1.dp, if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 22.sp)
                Spacer(Modifier.width(7.dp))
                Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            }
            content()
        }
    }
}

@Composable
private fun MetricBox(modifier: Modifier, label: String, value: String, sub: String) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Text(value, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(sub, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun DangerBanner(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(10.dp)) { Text(title, fontWeight = FontWeight.Black); Text(body, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ConfirmDeleteDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("DELETE", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(9)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun Meal.toFoodEntry(date: String = LocalDate.now().toString()): FoodEntry = FoodEntry(
    id = newEntryId(), date = date, name = name, calories = calories,
    protein = protein, mealType = category, carbs = carbs, fat = fat, fiber = fiber
)

private fun Int.formatComma(): String = "%,d".format(this)
private fun Double.format1(): String = "%.1f".format(this)
private fun Double.format0(): String = "%.0f".format(this)
private fun signed(value: Double): String = (if (value > 0) "+" else "") + "%.1f lb".format(value)
private fun newEntryId(): Long = System.currentTimeMillis() * 1000L + (System.nanoTime() % 1000L)

private fun buildHealthCsv(store: AppStore): String {
    val today = LocalDate.now()
    val start = today.minusDays(364)
    val foods = store.loadFoodRange(start, today).groupBy { it.date }
    val exercise = store.loadExerciseRange(start, today).groupBy { it.date }
    val weights = store.loadWeights().associateBy { it.date }
    val measurements = store.loadMeasurements().associateBy { it.date }
    return buildString {
        appendLine("date,steps,food_calories,protein_g,carbs_g,fat_g,fiber_g,water_fl_oz,sleep_hours,sleep_quality,mood,energy,stress,workout_minutes,workout_calories,weight_lb,waist_in")
        (0..364).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            val key = date.toString()
            val dayFood = foods[key].orEmpty()
            val dayExercise = exercise[key].orEmpty()
            append(key).append(',')
            append(store.dailySteps(key)).append(',')
            append(dayFood.sumOf { it.calories }).append(',')
            append(dayFood.sumOf { it.protein }).append(',')
            append(dayFood.sumOf { it.carbs }).append(',')
            append(dayFood.sumOf { it.fat }).append(',')
            append(dayFood.sumOf { it.fiber }).append(',')
            append(store.waterFlOz(key)).append(',')
            append(if (store.sleepHours(key) > 0f) store.sleepHours(key) else "").append(',')
            append(if (store.sleepQuality(key) > 0) store.sleepQuality(key) else "").append(',')
            val wellness = store.wellness(key)
            append(wellness?.mood ?: "").append(',')
            append(wellness?.energy ?: "").append(',')
            append(wellness?.stress ?: "").append(',')
            append(dayExercise.sumOf { it.minutes }).append(',')
            append(dayExercise.sumOf { it.calories }).append(',')
            append(weights[key]?.weightLb ?: "").append(',')
            append(measurements[key]?.waistIn ?: "").appendLine()
        }
    }
}
