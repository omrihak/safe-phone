package com.safephone.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.safephone.BreakTimeFormatter
import com.safephone.BuildConfig
import com.safephone.R
import com.safephone.update.InternalUpdateScheduler
import com.safephone.SafePhoneApp
import com.safephone.data.AppBudgetEntity
import com.safephone.data.BlockStatsAggregateRow
import com.safephone.data.BlockedAppEntity
import com.safephone.data.DomainRuleEntity
import com.safephone.github.VibeCodingGitHub
import com.safephone.export.RulesExporter
import com.safephone.service.BreakManager
import com.safephone.service.FocusEnforcementService
import com.safephone.service.PartnerBlockAlert
import com.safephone.ui.theme.SafePhoneTheme
import com.safephone.widget.FocusWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private data class HomeDestination(
    val route: String,
    val title: String,
    val subtitle: String,
    val imageVector: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureScaffold(
    nav: NavController,
    title: String,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding -> content(padding) }
}

@Composable
private fun PackageIcon(
    packageName: String,
    sizeDp: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { sizeDp.roundToPx().coerceAtLeast(1) }
    val bitmap = remember(packageName, px) {
        context.applicationIcon(packageName)?.toBitmap(px, px)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(sizeDp),
        )
    } else {
        Icon(
            Icons.Outlined.Android,
            contentDescription = null,
            modifier = Modifier.size(sizeDp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafePhoneTheme {
                val nav = rememberNavController()
                val app = application as SafePhoneApp
                val prefs = app.prefs
                val onboarded by prefs.onboardingCompleted.collectAsState(initial = false)
                NavHost(
                    navController = nav,
                    startDestination = if (onboarded) "home" else "onboarding",
                ) {
                    composable("onboarding") {
                        OnboardingRoute(nav, prefs)
                    }
                    composable("home") {
                        HomeRoute(nav, app)
                    }
                    composable("blocked") {
                        FeatureScaffold(nav, "Blocked apps") { padding ->
                            BlockedListRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("domains") {
                        FeatureScaffold(nav, "Domain rules") { padding ->
                            DomainListRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("budgets") {
                        FeatureScaffold(nav, "Daily budgets") { padding ->
                            BudgetsRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("breaks") {
                        FeatureScaffold(nav, "Break policy") { padding ->
                            BreaksRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("export") {
                        FeatureScaffold(nav, "Export rules") { padding ->
                            ExportRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("block_stats") {
                        FeatureScaffold(nav, "Block metrics") { padding ->
                            BlockStatsRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("system_grayscale") {
                        FeatureScaffold(nav, stringResource(R.string.system_grayscale_title)) { padding ->
                            SystemGrayscaleRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("partner_alert") {
                        FeatureScaffold(nav, stringResource(R.string.partner_alert_title)) { padding ->
                            PartnerAlertRoute(app, Modifier.padding(padding))
                        }
                    }
                    composable("vibe_coding") {
                        FeatureScaffold(nav, stringResource(R.string.vibe_coding_title)) { padding ->
                            VibeCodingRoute(Modifier.padding(padding))
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        InternalUpdateScheduler.enqueueSessionCheck(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingRoute(nav: androidx.navigation.NavController, prefs: com.safephone.data.FocusPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Welcome", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("SafePhone setup", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Grant the permissions below so focus rules run entirely on your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            @Composable
            fun StepCard(title: String, body: String, button: String, onClick: () -> Unit) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilledTonalButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                            Text(button)
                        }
                    }
                }
            }
            StepCard(
                title = "Usage access",
                body = "Needed to see which app is in the foreground.",
                button = "Open Usage access",
            ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            StepCard(
                title = "Display over other apps",
                body = "Shows the focus overlay when something is blocked.",
                button = "Open overlay settings",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
            StepCard(
                title = "Battery",
                body = "Avoids the system killing focus enforcement in the background.",
                button = "Ignore battery optimization",
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }
            StepCard(
                title = "Accessibility",
                body = "SafePhone uses the accessibility service to detect the active app and enforce rules.",
                button = "Open Accessibility settings",
            ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                StepCard(
                    title = "Notifications",
                    body = "Optional: status and break reminders.",
                    button = "Allow notifications",
                ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            val calPerm = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            StepCard(
                title = "Calendar (optional)",
                body = "Lets SafePhone tighten rules when a calendar title matches your focus keywords.",
                button = "Allow calendar read",
            ) { calPerm.launch(Manifest.permission.READ_CALENDAR) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                StepCard(
                    title = "Exact alarms (optional)",
                    body = "More reliable break timers on newer Android versions.",
                    button = "Open alarm settings",
                ) { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
            }
            Button(
                onClick = {
                    scope.launch {
                        prefs.setOnboardingCompleted(true)
                        FocusEnforcementService.start(context.applicationContext)
                        // DataStore may resume this coroutine off the main thread; NavController requires main.
                        withContext(Dispatchers.Main) {
                            nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag(SafePhoneTestTags.ONBOARDING_FINISH),
            ) { Text("Finish setup") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoute(nav: androidx.navigation.NavController, app: SafePhoneApp) {
    val context = LocalContext.current
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val breakPolicy by app.database.breakPolicyDao().observe().collectAsState(initial = null)
    val breakEnd by prefs.breakEndEpochMs.collectAsState(initial = null)
    val breaksUsed by prefs.breaksUsedToday.collectAsState(initial = 0)
    val breakDay by prefs.breakDayEpochDay.collectAsState(initial = 0L)
    val lastBreakEndedMs by prefs.lastBreakEndedEpochMs.collectAsState(initial = 0L)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(breakPolicy?.minGapBetweenBreaksMinutes) {
        FocusEnforcementService.start(context.applicationContext)
        while (isActive) {
            now = System.currentTimeMillis()
            val end = prefs.breakEndEpochMs.first()
            val ticking = end != null && now < end
            val lastEnd = prefs.lastBreakEndedEpochMs.first()
            val gapMinutes = breakPolicy?.minGapBetweenBreaksMinutes ?: 30
            val waitingGap = lastEnd > 0L && (now - lastEnd) < gapMinutes * 60_000L
            delay(if (ticking || waitingGap) 1_000L else 15_000L)
        }
    }
    val onBreak = breakEnd != null && now < breakEnd!!
    val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    val usedToday = if (breakDay != today) 0 else breaksUsed
    val maxBreaks = breakPolicy?.maxBreaksPerDay ?: 5
    val breaksLeft = (maxBreaks - usedToday).coerceAtLeast(0)
    val gapMinutes = breakPolicy?.minGapBetweenBreaksMinutes ?: 30
    val gapOk =
        lastBreakEndedMs <= 0L || (now - lastBreakEndedMs) >= gapMinutes * 60_000L
    val canStartBreakNow = breaksLeft > 0 && !onBreak && gapOk
    val gapWaitMinutes =
        if (lastBreakEndedMs > 0L && !gapOk) {
            val requiredMs = gapMinutes * 60_000L
            val elapsed = now - lastBreakEndedMs
            ((requiredMs - elapsed + 59_999) / 60_000).toInt().coerceAtLeast(1)
        } else {
            0
        }
    val destinations = listOf(
        HomeDestination("blocked", "Blocked apps", "Always blocked when enforcing", Icons.Filled.Block),
        HomeDestination("domains", "Domain rules", "Block sites in the browser", Icons.Outlined.Language),
        HomeDestination("budgets", "Daily budgets", "Per-app time limits", Icons.Outlined.Timer),
        HomeDestination(
            "block_stats",
            "Block metrics",
            "Block counts by day, week, or month",
            Icons.Outlined.BarChart,
        ),
        HomeDestination(
            "partner_alert",
            stringResource(R.string.partner_alert_title),
            stringResource(R.string.partner_alert_subtitle),
            Icons.Outlined.Email,
        ),
        HomeDestination("breaks", "Break policy", "How breaks work", Icons.Outlined.Coffee),
        HomeDestination("export", "Export rules", "Share JSON backup", Icons.Outlined.UploadFile),
        HomeDestination(
            "system_grayscale",
            "Grayscale display",
            "System color correction during focus",
            Icons.Outlined.InvertColors,
        ),
    ) + if (BuildConfig.GITHUB_ISSUES_OWNER.isNotEmpty() && BuildConfig.GITHUB_ISSUES_REPO.isNotEmpty()) {
        listOf(
            HomeDestination(
                "vibe_coding",
                stringResource(R.string.vibe_coding_title),
                stringResource(R.string.vibe_coding_nav_subtitle),
                Icons.Outlined.AutoAwesome,
            ),
        )
    } else {
        emptyList()
    }
    val testTagForRoute = remember {
        mapOf(
            "blocked" to SafePhoneTestTags.HOME_NAV_BLOCKED,
            "domains" to SafePhoneTestTags.HOME_NAV_DOMAINS,
            "budgets" to SafePhoneTestTags.HOME_NAV_BUDGETS,
            "block_stats" to SafePhoneTestTags.HOME_NAV_BLOCK_STATS,
            "partner_alert" to SafePhoneTestTags.HOME_NAV_PARTNER_ALERT,
            "breaks" to SafePhoneTestTags.HOME_NAV_BREAKS,
            "export" to SafePhoneTestTags.HOME_NAV_EXPORT,
            "system_grayscale" to SafePhoneTestTags.HOME_NAV_SYSTEM_GRAYSCALE,
            "vibe_coding" to SafePhoneTestTags.HOME_NAV_VIBE_CODING,
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("SafePhone", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("onboarding") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Setup")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            onBreak -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    ),
                ) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            if (onBreak) "On a break" else "Focus is on",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (onBreak) {
                                stringResource(
                                    R.string.home_break_time_remaining,
                                    BreakTimeFormatter.formatMmSs(breakEnd!! - now),
                                )
                            } else {
                                "Block and budget rules stay active. Take a break only when you need a pause."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            pluralStringResource(
                                com.safephone.R.plurals.home_breaks_balance,
                                breaksLeft,
                                breaksLeft,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag(SafePhoneTestTags.HOME_BREAKS_BALANCE),
                        )
                        if (gapWaitMinutes > 0) {
                            Text(
                                stringResource(R.string.home_break_gap_wait, gapWaitMinutes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val policy = breakPolicy ?: com.safephone.data.BreakPolicyEntity()
                                    val mgr = BreakManager(context.applicationContext, prefs)
                                    if (mgr.startBreak(policy.breakDurationMinutes, policy)) {
                                        FocusEnforcementService.start(context.applicationContext)
                                        FocusWidgetReceiver.refreshAll(context.applicationContext)
                                    }
                                }
                            },
                            enabled = canStartBreakNow,
                            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.HOME_START_BREAK),
                        ) { Text("Start break (${breakPolicy?.breakDurationMinutes ?: 10} min)") }
                    }
                }
            }
            item {
                Text(
                    "Rules & settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                destinations.chunked(2),
                key = { row -> row.joinToString("-") { it.route } },
            ) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { d ->
                        val tag = testTagForRoute[d.route]
                        Card(
                            onClick = { nav.navigate(d.route) },
                            modifier = Modifier
                                .weight(1f)
                                .height(104.dp)
                                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    d.imageVector,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(d.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    d.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemGrayscaleRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val automation by prefs.systemMonochromeAutomationEnabled.collectAsState(initial = true)
    var secureGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2_000)
            secureGranted = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
    val adbCommand = remember {
        "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS"
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.system_grayscale_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (secureGranted) {
                stringResource(R.string.system_grayscale_perm_granted)
            } else {
                stringResource(R.string.system_grayscale_perm_missing)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        RowSwitch(
            label = stringResource(R.string.system_grayscale_toggle),
            checked = automation,
            onChecked = { v -> scope.launch { prefs.setSystemMonochromeAutomationEnabled(v) } },
        )
        Text(
            stringResource(R.string.system_grayscale_adb_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = adbCommand,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", adbCommand))
                Toast.makeText(context, R.string.system_grayscale_copied, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.system_grayscale_copy))
        }
    }
}

@Composable
private fun PartnerAlertRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = app.prefs
    val scope = rememberCoroutineScope()
    val enabled by prefs.partnerBlockAlertEnabled.collectAsState(initial = false)
    val thresholdStored by prefs.partnerBlockAlertThreshold.collectAsState(initial = 5)
    val storedPhone by prefs.partnerAlertPhoneDigits.collectAsState(initial = "")

    var phoneDraft by remember { mutableStateOf("") }
    var thresholdDraft by remember { mutableStateOf("5") }
    LaunchedEffect(storedPhone) {
        phoneDraft = storedPhone
    }
    LaunchedEffect(thresholdStored) {
        thresholdDraft = thresholdStored.toString()
    }

    var partnerSmsGranted by remember {
        mutableStateOf(PartnerBlockAlert.hasSendSmsPermission(context))
    }
    val partnerSmsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        partnerSmsGranted = PartnerBlockAlert.hasSendSmsPermission(context)
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2_000)
            partnerSmsGranted = PartnerBlockAlert.hasSendSmsPermission(context)
        }
    }

    val canSendTest =
        partnerSmsGranted && PartnerBlockAlert.normalizeSmsDestination(phoneDraft) != null

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.partner_alert_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowSwitch(
            label = stringResource(R.string.partner_alert_enable),
            checked = enabled,
            onChecked = { v -> scope.launch { prefs.setPartnerBlockAlertEnabled(v) } },
        )
        OutlinedTextField(
            value = phoneDraft,
            onValueChange = { phoneDraft = it },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.PARTNER_ALERT_PHONE_FIELD),
            label = { Text(stringResource(R.string.partner_alert_phone_label)) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        OutlinedTextField(
            value = thresholdDraft,
            onValueChange = { thresholdDraft = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.PARTNER_ALERT_THRESHOLD_FIELD),
            label = { Text(stringResource(R.string.partner_alert_threshold_label)) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (partnerSmsGranted) {
            Text(
                stringResource(R.string.partner_alert_sms_granted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(
                onClick = {
                    partnerSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.partner_alert_grant_sms))
            }
        }
        OutlinedButton(
            onClick = { PartnerBlockAlert.sendTestSmsNow(context, phoneDraft) },
            enabled = canSendTest,
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.PARTNER_ALERT_SEND_TEST),
        ) {
            Text(stringResource(R.string.partner_alert_send_test))
        }
        Button(
            onClick = {
                scope.launch {
                    prefs.setPartnerAlertPhoneDigits(phoneDraft)
                    val t = thresholdDraft.toIntOrNull()?.coerceAtLeast(1) ?: 5
                    prefs.setPartnerBlockAlertThreshold(t)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.PARTNER_ALERT_SAVE),
        ) {
            Text(stringResource(R.string.partner_alert_save))
        }
    }
}

@Composable
private fun VibeCodingRoute(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var vibeRequestText by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.vibe_coding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = vibeRequestText,
            onValueChange = { vibeRequestText = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SafePhoneTestTags.HOME_VIBE_REQUEST_FIELD),
            label = { Text(stringResource(R.string.vibe_coding_field_label)) },
            minLines = 3,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        )
        FilledTonalButton(
            onClick = {
                val uri = VibeCodingGitHub.newIssueUri(
                    BuildConfig.GITHUB_ISSUES_OWNER,
                    BuildConfig.GITHUB_ISSUES_REPO,
                    vibeRequestText,
                )
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(
                            context,
                            R.string.vibe_coding_no_browser,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            enabled = vibeRequestText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SafePhoneTestTags.HOME_VIBE_OPEN_GITHUB),
        ) { Text(stringResource(R.string.vibe_coding_open_github)) }
    }
}

@Composable
private fun BlockedListRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = app.database
    val blocked by db.blockedAppDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var manualPackage by remember { mutableStateOf("") }
    var launchable by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        launchable = context.loadLaunchableApps()
    }
    val blockedSet = remember(blocked) { blocked.map { it.packageName }.toSet() }
    val filteredLaunchable = remember(launchable, searchQuery, blockedSet) {
        val q = searchQuery.trim()
        launchable
            .filter { it.packageName !in blockedSet }
            .filter {
                q.isEmpty() ||
                    it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
            .take(100)
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Search or enter a package manually, then scroll to block from your installed apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BLOCKED_SEARCH_FIELD),
                label = { Text("Search installed apps") },
                placeholder = { Text("App name or package") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag(SafePhoneTestTags.BLOCKED_MANUAL_SECTION),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manual entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "If an app does not appear (work profile, hidden), enter its package name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        manualPackage,
                        { manualPackage = it },
                        label = { Text("Package to block") },
                        modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BLOCKED_PACKAGE_FIELD),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .testTag(SafePhoneTestTags.BLOCKED_ADD)
                            .semantics(mergeDescendants = false) {},
                    ) {
                        Button(
                            onClick = {
                                val p = manualPackage.trim()
                                if (p.isNotEmpty()) {
                                    scope.launch { db.blockedAppDao().upsert(BlockedAppEntity(p)) }
                                    manualPackage = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add package") }
                    }
                }
            }
        }
        item {
            Text(
                "Blocked (${blocked.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (blocked.isEmpty()) {
            item {
                Text(
                    "No apps blocked yet. Add from the list or enter a package manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(blocked, key = { it.packageName }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PackageIcon(row.packageName, 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                launchable.find { it.packageName == row.packageName }?.label ?: row.packageName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                row.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            )
                        }
                        IconButton(
                            onClick = { scope.launch { db.blockedAppDao().delete(row.packageName) } },
                            modifier = Modifier.testTag(SafePhoneTestTags.BLOCKED_REMOVE_PREFIX + row.packageName),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove from blocked list")
                        }
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            Text(
                "Add from installed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(filteredLaunchable, key = { it.packageName }) { appInfo ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PackageIcon(appInfo.packageName, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(appInfo.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            appInfo.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            scope.launch { db.blockedAppDao().upsert(BlockedAppEntity(appInfo.packageName)) }
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Block")
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainListRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val db = app.database
    val list by db.domainRuleDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pattern by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Match browser hostnames. Use a leading dot for suffixes (e.g. .example.com blocks all subdomains).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            pattern,
            { pattern = it },
            label = { Text("Host pattern") },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.DOMAIN_PATTERN_FIELD),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
        )
        Button(
            onClick = {
                scope.launch {
                    db.domainRuleDao().upsert(DomainRuleEntity(pattern = pattern.trim(), isAllowlist = false))
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.DOMAIN_ADD),
        ) { Text("Add blocked site") }
        Text("Blocked", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        list.forEach { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp)) {
                        Text(
                            row.pattern,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { db.domainRuleDao().delete(row.id) } },
                        modifier = Modifier.testTag(SafePhoneTestTags.DOMAIN_REMOVE_PREFIX + row.id),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove rule")
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSwitch(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    switchTestTag: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier,
            )
        }
    }
}

@Composable
private fun BudgetsRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = app.database
    val list by db.appBudgetDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var pickerMins by remember { mutableStateOf("30") }
    var pickerOpens by remember { mutableStateOf("0") }
    var manualPkg by remember { mutableStateOf("") }
    var manualMins by remember { mutableStateOf("30") }
    var manualOpens by remember { mutableStateOf("0") }
    var launchable by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var usageMs by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var usageOpens by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(Unit) {
        launchable = context.loadLaunchableApps()
    }
    LaunchedEffect(app.usageStatsRepository) {
        while (isActive) {
            val zone = ZoneId.systemDefault()
            val ms = withContext(Dispatchers.Default) {
                app.usageStatsRepository.usageMsSinceLocalMidnight(zone)
            }
            val opens = withContext(Dispatchers.Default) {
                app.usageStatsRepository.opensSinceLocalMidnight(zone)
            }
            usageMs = ms
            usageOpens = opens
            delay(10_000)
        }
    }
    val budgetByPackage = remember(list) { list.associateBy { it.packageName } }
    val filteredLaunchable = remember(launchable, searchQuery, budgetByPackage) {
        val q = searchQuery.trim()
        launchable
            .filter { it.packageName !in budgetByPackage }
            .filter {
                q.isEmpty() ||
                    it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
            .take(100)
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Cap daily screen time and/or how often an app may open while enforcement is on. " +
                    "0 minutes = no time cap; 0 opens = no open cap. Stats refresh about every 10 seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = pickerMins,
                    onValueChange = { pickerMins = it },
                    label = { Text("Min/day") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pickerOpens,
                    onValueChange = { pickerOpens = it },
                    label = { Text("Max opens") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search installed apps") },
                placeholder = { Text("App name or package") },
                modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BUDGET_SEARCH_FIELD),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
        }
        item {
            Text(
                "Current budgets (${list.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (list.isEmpty()) {
            item {
                Text(
                    "No budgets yet. Add from the list or enter a package manually below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(list, key = { it.packageName }) { row ->
                BudgetRow(
                    row = row,
                    label = launchable.find { it.packageName == row.packageName }?.label,
                    usedMsToday = usageMs[row.packageName] ?: 0L,
                    opensToday = usageOpens[row.packageName] ?: 0,
                    onUpdate = { mins, opens ->
                        scope.launch {
                            db.appBudgetDao().upsert(
                                AppBudgetEntity(
                                    row.packageName,
                                    mins.coerceAtLeast(0),
                                    opens.coerceAtLeast(0),
                                ),
                            )
                        }
                    },
                    onRemove = {
                        scope.launch { db.appBudgetDao().delete(row.packageName) }
                    },
                )
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item {
            Text(
                "Add from installed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(filteredLaunchable, key = { it.packageName }) { appInfo ->
            val m = pickerMins.toIntOrNull() ?: 0
            val o = pickerOpens.toIntOrNull() ?: 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PackageIcon(appInfo.packageName, 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(appInfo.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(
                            appInfo.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                db.appBudgetDao().upsert(
                                    AppBudgetEntity(appInfo.packageName, m.coerceAtLeast(0), o.coerceAtLeast(0)),
                                )
                            }
                        },
                        enabled = m >= 0 && o >= 0 && (m > 0 || o > 0),
                    ) {
                        Text("Add")
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manual entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "If an app does not appear, enter its package name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        manualPkg,
                        { manualPkg = it },
                        label = { Text("Package name") },
                        modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BUDGET_PACKAGE_FIELD),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            manualMins,
                            { manualMins = it },
                            label = { Text("Min/day (0=off)") },
                            modifier = Modifier.weight(1f).testTag(SafePhoneTestTags.BUDGET_MINUTES_FIELD),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            manualOpens,
                            { manualOpens = it },
                            label = { Text("Opens (0=off)") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                        )
                    }
                    Button(
                        onClick = {
                            val p = manualPkg.trim()
                            if (p.isNotEmpty()) {
                                val mm = manualMins.toIntOrNull() ?: 0
                                val mo = manualOpens.toIntOrNull() ?: 0
                                scope.launch {
                                    db.appBudgetDao().upsert(
                                        AppBudgetEntity(p, mm.coerceAtLeast(0), mo.coerceAtLeast(0)),
                                    )
                                }
                            }
                        },
                        enabled = (manualMins.toIntOrNull() ?: 0) > 0 || (manualOpens.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BUDGET_SAVE),
                    ) { Text("Save budget") }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    row: AppBudgetEntity,
    label: String?,
    usedMsToday: Long,
    opensToday: Int,
    onUpdate: (maxMinutes: Int, maxOpens: Int) -> Unit,
    onRemove: () -> Unit,
) {
    var localMins by remember(row.packageName, row.maxMinutesPerDay) {
        mutableStateOf(row.maxMinutesPerDay.toString())
    }
    var localOpens by remember(row.packageName, row.maxOpensPerDay) {
        mutableStateOf(row.maxOpensPerDay.toString())
    }
    LaunchedEffect(row.maxMinutesPerDay) {
        localMins = row.maxMinutesPerDay.toString()
    }
    LaunchedEffect(row.maxOpensPerDay) {
        localOpens = row.maxOpensPerDay.toString()
    }
    val usedMinutesToday = (usedMsToday / 60_000L).toInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                PackageIcon(row.packageName, 44.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .widthIn(min = 0.dp)
                        .padding(start = 10.dp, end = 4.dp),
                ) {
                    Text(
                        text = label ?: row.packageName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val pkgScroll = rememberScrollState()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(pkgScroll),
                    ) {
                        Text(
                            text = row.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(SafePhoneTestTags.BUDGET_REMOVE_PREFIX + row.packageName),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove budget")
                }
            }
            if (row.maxMinutesPerDay > 0) {
                val leftMin = (row.maxMinutesPerDay - usedMinutesToday).coerceAtLeast(0)
                Text(
                    "Time today: $usedMinutesToday / ${row.maxMinutesPerDay} min ($leftMin min left)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Time today: $usedMinutesToday min (no daily time cap)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.maxOpensPerDay > 0) {
                val leftOpens = (row.maxOpensPerDay - opensToday).coerceAtLeast(0)
                Text(
                    "Opens today: $opensToday / ${row.maxOpensPerDay} ($leftOpens left before block)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = localMins,
                    onValueChange = { localMins = it.filter { ch -> ch.isDigit() }.take(4) },
                    modifier = Modifier.width(76.dp),
                    placeholder = { Text("Min") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = localOpens,
                    onValueChange = { localOpens = it.filter { ch -> ch.isDigit() }.take(3) },
                    modifier = Modifier.width(76.dp),
                    placeholder = { Text("Opens") },
                    singleLine = true,
                )
                FilledTonalButton(
                    onClick = {
                        onUpdate(localMins.toIntOrNull() ?: 0, localOpens.toIntOrNull() ?: 0)
                    },
                ) {
                    Text("OK", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun BreaksRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = app.database
    val policy by db.breakPolicyDao().observe().collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var max by remember { mutableStateOf(policy?.maxBreaksPerDay?.toString() ?: "5") }
    var dur by remember { mutableStateOf(policy?.breakDurationMinutes?.toString() ?: "10") }
    var gap by remember { mutableStateOf(policy?.minGapBetweenBreaksMinutes?.toString() ?: "30") }
    LaunchedEffect(policy) {
        policy?.let {
            max = it.maxBreaksPerDay.toString()
            dur = it.breakDurationMinutes.toString()
            gap = it.minGapBetweenBreaksMinutes.toString()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Control how many timed breaks you get per day to relax the rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            max,
            { max = it },
            label = { Text("Max breaks per day") },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BREAKS_MAX_FIELD),
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            dur,
            { dur = it },
            label = { Text("Break length (minutes)") },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BREAKS_DURATION_FIELD),
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            gap,
            { gap = it },
            label = { Text("Minimum gap between breaks (minutes)") },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BREAKS_GAP_FIELD),
            shape = RoundedCornerShape(12.dp),
        )
        Button(
            onClick = {
                scope.launch {
                    db.breakPolicyDao().upsert(
                        com.safephone.data.BreakPolicyEntity(
                            maxBreaksPerDay = max.toIntOrNull() ?: 5,
                            breakDurationMinutes = dur.toIntOrNull() ?: 10,
                            minGapBetweenBreaksMinutes = gap.toIntOrNull() ?: 30,
                        ),
                    )
                    FocusWidgetReceiver.refreshAll(context.applicationContext)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.BREAKS_SAVE),
        ) { Text("Save policy") }
    }
}

private enum class BlockStatsPeriod(val daysInclusive: Int, val chipLabel: String) {
    DAY(1, "Day"),
    WEEK(7, "Week"),
    MONTH(30, "Month"),
}

private fun BlockStatsPeriod.rangePhrase(): String =
    when (this) {
        BlockStatsPeriod.DAY -> "today"
        BlockStatsPeriod.WEEK -> "the last 7 days"
        BlockStatsPeriod.MONTH -> "the last 30 days"
    }

private fun BlockStatsPeriod.emptyMessage(): String =
    when (this) {
        BlockStatsPeriod.DAY -> "No blocks recorded today."
        BlockStatsPeriod.WEEK -> "No blocks recorded in the last 7 days."
        BlockStatsPeriod.MONTH -> "No blocks recorded in the last 30 days."
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockStatsRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var period by remember { mutableStateOf(BlockStatsPeriod.MONTH) }
    val zone = ZoneId.systemDefault()
    val sinceEpochDay = remember(period) {
        LocalDate.now(zone).minusDays(period.daysInclusive - 1L).toEpochDay()
    }
    val rows by app.database.blockStatsDao().observeAggregatedSince(sinceEpochDay).collectAsState(initial = emptyList())
    fun targetLabel(row: BlockStatsAggregateRow): String = when (row.kind) {
        "web" -> row.targetKey
        else -> runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(row.targetKey, 0)
            pm.getApplicationLabel(info).toString().trim().ifEmpty { row.targetKey }
        }.getOrElse { row.targetKey }
    }
    fun kindLabel(kind: String): String = when (kind) {
        "app" -> "App"
        "web" -> "Website"
        "browser_lock" -> "Browser (strict lock)"
        else -> kind
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BlockStatsPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(p.chipLabel) },
                        modifier = Modifier.testTag(
                            when (p) {
                                BlockStatsPeriod.DAY -> SafePhoneTestTags.BLOCK_STATS_PERIOD_DAY
                                BlockStatsPeriod.WEEK -> SafePhoneTestTags.BLOCK_STATS_PERIOD_WEEK
                                BlockStatsPeriod.MONTH -> SafePhoneTestTags.BLOCK_STATS_PERIOD_MONTH
                            },
                        ),
                    )
                }
            }
        }
        item {
            Text(
                "Each row sums how often you hit the block screen for that app or site in ${period.rangePhrase()}. " +
                    "Leaving and returning counts again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    period.emptyMessage(),
                    modifier = Modifier.testTag(SafePhoneTestTags.BLOCK_STATS_EMPTY),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(rows, key = { "${it.kind}:${it.targetKey}" }) { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                targetLabel(row),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                kindLabel(row.kind),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${row.count}×",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportRoute(app: SafePhoneApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var json by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Export your rules as JSON for backup or sharing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                scope.launch {
                    json = RulesExporter(app.database).exportJson()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.EXPORT_GENERATE),
        ) { Text("Generate JSON") }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, json)
                    putExtra(Intent.EXTRA_SUBJECT, "SafePhone rules export")
                }
                context.startActivity(Intent.createChooser(send, "Share rules JSON"))
            },
            enabled = json.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag(SafePhoneTestTags.EXPORT_SHARE),
        ) { Text("Share") }
        if (json.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Text(
                    json,
                    modifier = Modifier.padding(14.dp).testTag(SafePhoneTestTags.EXPORT_JSON_TEXT),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
