package com.wellnesscompanion.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import com.wellnesscompanion.app.data.model.Category
import com.wellnesscompanion.app.sync.SyncStatus
import com.wellnesscompanion.app.sync.SyncViewModel
import com.wellnesscompanion.app.ui.components.CategoryCard
import com.wellnesscompanion.app.ui.theme.WaterText
import com.wellnesscompanion.app.util.greetingForHour
import com.wellnesscompanion.app.util.todayDisplayString

// Multi-hue garden gradient: sakura blush → wisteria → morning sage
private val DashboardBgTop = Color(0xFFF5DFE3)
private val DashboardBgMid = Color(0xFFE8DDF2)
private val DashboardBgBottom = Color(0xFFDCE8DA)
private val GreetingColor = Color(0xFF3D3262)

@Composable
fun DashboardScreen(
    onCategoryClick: (Category) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val summaries by viewModel.summaries.collectAsState()
    val streaks by viewModel.streaks.collectAsState()
    val syncStatus by syncViewModel.status.collectAsState()

    val totalStreak = streaks.values.maxOrNull() ?: 0
    val categoriesLogged = streaks.count { it.value > 0 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DashboardBgTop, DashboardBgMid, DashboardBgBottom)
                )
            )
    ) {
        // Rich background — sakura, Fuji, torii
        DashboardBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header section
            Column(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = greetingForHour() + " \uD83C\uDF38",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GreetingColor
                )
                Text(
                    text = todayDisplayString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = GreetingColor.copy(alpha = 0.5f)
                )
            }

            // Sync section (hidden by default)
            var showSync by remember { mutableStateOf(false) }

            Text(
                text = if (showSync) "\uD83D\uDD04 Hide sync" else "\uD83D\uDD04 Sync",
                style = MaterialTheme.typography.labelSmall,
                color = GreetingColor.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clickable { showSync = !showSync }
            )

            if (showSync) {
                Spacer(modifier = Modifier.height(6.dp))

                val isSyncing = syncStatus is SyncStatus.Discovering || syncStatus is SyncStatus.Connecting || syncStatus is SyncStatus.Syncing
                val manualIp by syncViewModel.manualIp.collectAsState()

                val syncLabel = when (syncStatus) {
                    is SyncStatus.Idle -> "\uD83D\uDD04 Sync"
                    is SyncStatus.Discovering -> "Searching..."
                    is SyncStatus.Connecting -> "Connecting..."
                    is SyncStatus.Syncing -> "Syncing..."
                    is SyncStatus.NeedsPairing -> "Enter code"
                    is SyncStatus.Done -> "\u2705 Done"
                    is SyncStatus.Error -> "\u274C Error"
                }
                val syncDetail = when (syncStatus) {
                    is SyncStatus.Done -> (syncStatus as SyncStatus.Done).message
                    is SyncStatus.Error -> (syncStatus as SyncStatus.Error).message
                    is SyncStatus.NeedsPairing ->
                        "Enter the pairing code shown in the PC app's sidebar"
                    else -> null
                }

                // Pairing code. The PC refuses every request until this matches,
                // so nothing leaves the phone before it is set.
                val pairingCode by syncViewModel.pairingCode.collectAsState()
                val isPaired by syncViewModel.isPaired.collectAsState()

                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = pairingCode,
                        onValueChange = { syncViewModel.setPairingCode(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(
                            color = GreetingColor,
                            letterSpacing = 2.sp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { innerTextField ->
                            if (pairingCode.isEmpty()) {
                                Text(
                                    "Pairing code from the PC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GreetingColor.copy(alpha = 0.4f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Text(
                        text = if (isPaired) "\u2713 Paired" else "Pair",
                        style = MaterialTheme.typography.labelSmall,
                        color = GreetingColor.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .clickable { syncViewModel.savePairingCode() }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Manual IP input
                    androidx.compose.foundation.text.BasicTextField(
                        value = manualIp,
                        onValueChange = { syncViewModel.setManualIp(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(color = GreetingColor),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { innerTextField ->
                            if (manualIp.isEmpty()) {
                                Text("PC IP (e.g. 192.168.1.5)", style = MaterialTheme.typography.labelSmall, color = GreetingColor.copy(alpha = 0.4f))
                            }
                            innerTextField()
                        }
                    )

                    // Connect button
                    Text(
                        text = syncLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = GreetingColor.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = if (isSyncing) 0.2f else 0.35f))
                            .clickable(enabled = !isSyncing) {
                                if (manualIp.isNotBlank()) syncViewModel.syncManual()
                                else syncViewModel.syncAuto()
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
                if (syncDetail != null) {
                    Text(
                        text = syncDetail,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = GreetingColor.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Daily overview pill
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.35f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini progress dots for each category
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Category.entries.forEach { cat ->
                        val hasData = (streaks[cat] ?: 0) > 0 ||
                            summaries[cat]?.startsWith("0") == false
                        Canvas(modifier = Modifier.size(10.dp)) {
                            drawCircle(
                                color = if (hasData) cat.colors.textColor.copy(alpha = 0.7f)
                                else cat.colors.cardBg.copy(alpha = 0.5f),
                                radius = size.minDimension / 2
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "$categoriesLogged/9 tracked",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreetingColor.copy(alpha = 0.5f)
                )

                if (totalStreak > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "\uD83D\uDD25 $totalStreak",
                        fontSize = 11.sp,
                        color = GreetingColor.copy(alpha = 0.45f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3x3 grid with colored cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(Category.entries.toList()) { category ->
                    CategoryCard(
                        category = category,
                        summary = summaries[category] ?: "No data",
                        streak = streaks[category] ?: 0,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Watercolor wash blobs — soft color pools from category palettes
        drawCircle(Color(0xFFED93B1).copy(alpha = 0.08f), w * 0.35f, Offset(w * 0.15f, h * 0.20f))  // sakura pink
        drawCircle(Color(0xFF5DCAA5).copy(alpha = 0.06f), w * 0.30f, Offset(w * 0.80f, h * 0.55f))  // teal
        drawCircle(Color(0xFFF0997B).copy(alpha = 0.06f), w * 0.25f, Offset(w * 0.50f, h * 0.75f))  // coral
        drawCircle(Color(0xFFDEDCF7).copy(alpha = 0.08f), w * 0.28f, Offset(w * 0.30f, h * 0.50f))  // lavender
        drawCircle(Color(0xFFF5E5C4).copy(alpha = 0.07f), w * 0.22f, Offset(w * 0.70f, h * 0.25f))  // warm tan

        // Layered misty mountains (3 layers)
        val mistFar = Path().apply {
            moveTo(0f, h * 0.72f)
            cubicTo(w * 0.12f, h * 0.65f, w * 0.28f, h * 0.68f, w * 0.42f, h * 0.64f)
            cubicTo(w * 0.58f, h * 0.60f, w * 0.75f, h * 0.66f, w, h * 0.70f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(mistFar, Color(0xFF7BAFD4).copy(alpha = 0.08f))

        val mistMid = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.2f, h * 0.72f, w * 0.4f, h * 0.75f, w * 0.6f, h * 0.73f)
            cubicTo(w * 0.8f, h * 0.70f, w * 0.9f, h * 0.76f, w, h * 0.78f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(mistMid, Color(0xFF7BAFD4).copy(alpha = 0.10f))

        val hillNear = Path().apply {
            moveTo(0f, h * 0.86f)
            cubicTo(w * 0.25f, h * 0.80f, w * 0.55f, h * 0.83f, w * 0.75f, h * 0.81f)
            cubicTo(w * 0.9f, h * 0.79f, w, h * 0.84f, w, h * 0.88f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hillNear, Color(0xFF7BAFD4).copy(alpha = 0.12f))

        // Mt. Fuji
        val fujiPath = Path().apply {
            moveTo(w * 0.25f, h * 0.88f)
            cubicTo(w * 0.32f, h * 0.82f, w * 0.42f, h * 0.72f, w * 0.5f, h * 0.64f)
            cubicTo(w * 0.58f, h * 0.72f, w * 0.68f, h * 0.82f, w * 0.75f, h * 0.88f)
            close()
        }
        drawPath(fujiPath, Color(0xFF5A7EA0).copy(alpha = 0.15f))
        // Snow cap
        val snowPath = Path().apply {
            moveTo(w * 0.42f, h * 0.73f)
            lineTo(w * 0.5f, h * 0.64f)
            lineTo(w * 0.58f, h * 0.73f)
            cubicTo(w * 0.56f, h * 0.74f, w * 0.53f, h * 0.72f, w * 0.5f, h * 0.74f)
            cubicTo(w * 0.47f, h * 0.72f, w * 0.44f, h * 0.74f, w * 0.42f, h * 0.73f)
        }
        drawPath(snowPath, Color.White.copy(alpha = 0.22f))
        // Cloud band
        drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(w * 0.30f, h * 0.76f), Size(w * 0.40f, h * 0.015f), CornerRadius(8f))

        // Stone lantern (toro)
        val toroColor = Color(0xFF5A7EA0).copy(alpha = 0.13f)
        drawLine(toroColor, Offset(w * 0.08f, h * 0.90f), Offset(w * 0.08f, h * 0.82f), strokeWidth = 4f)
        val toroRoof = Path().apply {
            moveTo(w * 0.03f, h * 0.82f)
            lineTo(w * 0.08f, h * 0.78f)
            lineTo(w * 0.13f, h * 0.82f)
            close()
        }
        drawPath(toroRoof, toroColor)
        drawRoundRect(Color(0xFFF5E5C4).copy(alpha = 0.12f), Offset(w * 0.06f, h * 0.82f), Size(w * 0.04f, h * 0.02f), CornerRadius(2f))

        // Torii gate
        val toriiColor = Color(0xFFD4728E).copy(alpha = 0.22f)
        drawLine(toriiColor, Offset(w * 0.82f, h * 0.80f), Offset(w * 0.82f, h * 0.95f), strokeWidth = 5f)
        drawLine(toriiColor, Offset(w * 0.96f, h * 0.80f), Offset(w * 0.96f, h * 0.95f), strokeWidth = 5f)
        val kasagiPath = Path().apply {
            moveTo(w * 0.78f, h * 0.805f)
            quadraticTo(w * 0.80f, h * 0.79f, w * 0.82f, h * 0.80f)
            lineTo(w * 0.96f, h * 0.80f)
            quadraticTo(w * 0.98f, h * 0.79f, w, h * 0.805f)
            lineTo(w, h * 0.815f)
            lineTo(w * 0.78f, h * 0.815f)
            close()
        }
        drawPath(kasagiPath, toriiColor)
        drawLine(toriiColor, Offset(w * 0.80f, h * 0.835f), Offset(w * 0.98f, h * 0.835f), strokeWidth = 3f)

        // Sakura tree
        val branchColor = Color(0xFF8B6B5A).copy(alpha = 0.15f)
        val thinBranch = Color(0xFF8B6B5A).copy(alpha = 0.10f)
        drawLine(branchColor, Offset(w * 0.92f, h * 0.95f), Offset(w * 0.90f, h * 0.50f), strokeWidth = 6f)
        drawLine(branchColor, Offset(w * 0.90f, h * 0.50f), Offset(w * 0.78f, h * 0.35f), strokeWidth = 4f)
        drawLine(branchColor, Offset(w * 0.90f, h * 0.55f), Offset(w * 0.98f, h * 0.38f), strokeWidth = 3f)
        drawLine(thinBranch, Offset(w * 0.90f, h * 0.60f), Offset(w * 0.82f, h * 0.50f), strokeWidth = 2f)
        drawLine(thinBranch, Offset(w * 0.84f, h * 0.42f), Offset(w * 0.75f, h * 0.32f), strokeWidth = 2f)
        drawLine(thinBranch, Offset(w * 0.86f, h * 0.38f), Offset(w * 0.92f, h * 0.28f), strokeWidth = 1.5f)

        // Cherry blossom petals — bold
        val petalColor = Color(0xFFED93B1).copy(alpha = 0.26f)
        val petalLight = Color(0xFFF5C4D8).copy(alpha = 0.20f)
        listOf(
            Offset(w * 0.78f, h * 0.34f) to 14f, Offset(w * 0.74f, h * 0.31f) to 10f,
            Offset(w * 0.82f, h * 0.36f) to 8f, Offset(w * 0.98f, h * 0.36f) to 12f,
            Offset(w * 0.95f, h * 0.32f) to 9f, Offset(w * 0.82f, h * 0.49f) to 10f,
            Offset(w * 0.92f, h * 0.26f) to 8f, Offset(w * 0.75f, h * 0.30f) to 7f,
        ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }

        listOf(
            Offset(w * 0.06f, h * 0.06f) to 10f, Offset(w * 0.12f, h * 0.03f) to 7f,
            Offset(w * 0.18f, h * 0.08f) to 5f, Offset(w * 0.30f, h * 0.04f) to 6f,
            Offset(w * 0.45f, h * 0.02f) to 4f, Offset(w * 0.55f, h * 0.06f) to 5f,
            Offset(w * 0.65f, h * 0.10f) to 4f, Offset(w * 0.40f, h * 0.12f) to 3f,
        ).forEach { (pos, r) -> drawCircle(petalColor, r, pos) }

        listOf(
            Offset(w * 0.70f, h * 0.14f) to 4f, Offset(w * 0.25f, h * 0.10f) to 3f,
            Offset(w * 0.50f, h * 0.08f) to 4f, Offset(w * 0.15f, h * 0.14f) to 3f,
        ).forEach { (pos, r) -> drawCircle(petalLight, r, pos) }

        // Cloud wisps
        val cloudColor = Color.White.copy(alpha = 0.10f)
        drawRoundRect(cloudColor, Offset(w * 0.02f, h * 0.18f), Size(70f, 10f), CornerRadius(5f))
        drawRoundRect(cloudColor, Offset(w * 0.35f, h * 0.22f), Size(55f, 8f), CornerRadius(4f))
        drawRoundRect(cloudColor, Offset(w * 0.60f, h * 0.16f), Size(45f, 9f), CornerRadius(4f))
    }
}
