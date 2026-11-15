package cz.mares.stavitelpm

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App() {
    StavitelTheme {
        var vaultConfig by remember { mutableStateOf(NativeBridge.loadVaultConfig()) }
        var showVaultDialog by remember { mutableStateOf(vaultConfig.ghToken.isBlank()) }
        var tokenVerifiedLogin by remember { mutableStateOf("") }
        var tokenVerifiedOk by remember { mutableStateOf<Boolean?>(null) }
        var pageProgress by remember { mutableStateOf(0) }
        var bannerMessage by remember { mutableStateOf("") }
        var bannerIsError by remember { mutableStateOf(false) }
        var bannerSeq by remember { mutableStateOf(0) }
        var cmdCounter by remember { mutableStateOf(1L) }
        var activeCommand by remember { mutableStateOf<WebCommand?>(null) }

        val sendWebCommand: (WebCommandType, String) -> Unit = { type, payload ->
            val nextId = cmdCounter + 1L
            cmdCounter = nextId
            activeCommand = WebCommand(id = nextId, type = type, payload = payload)
        }

        val showBanner: (String, Boolean) -> Unit = { msg, isErr ->
            bannerMessage = msg
            bannerIsError = isErr
            bannerSeq += 1
        }

        LaunchedEffect(bannerSeq) {
            if (bannerMessage.isNotEmpty()) {
                delay(3600)
                bannerMessage = ""
            }
        }

        LaunchedEffect(vaultConfig.ghToken) {
            val tok = vaultConfig.ghToken.trim()
            if (tok.isEmpty()) {
                tokenVerifiedOk = false
                tokenVerifiedLogin = ""
            } else {
                val res = NativeBridge.verifyGitHubToken(tok)
                tokenVerifiedOk = res.ok
                tokenVerifiedLogin = res.login
            }
        }

        val effectiveOwner = vaultConfig.ghOwner.trim().ifEmpty { "drozkar-glitch" }
        val liveUrl = "https://$effectiveOwner.github.io/StavitelAplikaci/"

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Horni kompaktni lista
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showVaultDialog = true }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Stavitel PM",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusTxt = when {
                                        vaultConfig.ghToken.isBlank() -> "⚠️ Klepni pro vložení GH tokenu"
                                        tokenVerifiedOk == true -> "🔐 @$tokenVerifiedLogin • Živý GH"
                                        tokenVerifiedOk == false -> "⚠️ Token neověřen • Klepni"
                                        else -> "⏳ Ověřuji GitHub token..."
                                    }
                                    val statusColor = when {
                                        vaultConfig.ghToken.isBlank() || tokenVerifiedOk == false -> MaterialTheme.colorScheme.error
                                        tokenVerifiedOk == true -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = statusTxt,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TopSmallChip(
                                    label = if (vaultConfig.mobileZoomFit) "📱 Mobil" else "🖥️ 1:1",
                                    highlighted = vaultConfig.mobileZoomFit,
                                    onClick = {
                                        val updated = vaultConfig.copy(mobileZoomFit = !vaultConfig.mobileZoomFit)
                                        vaultConfig = updated
                                        NativeBridge.saveVaultConfig(updated)
                                        sendWebCommand(
                                            WebCommandType.APPLY_MOBILE_LAYOUT,
                                            if (updated.mobileZoomFit) "1" else "0"
                                        )
                                        showBanner(
                                            if (updated.mobileZoomFit) "Zapnuto mobilní zvětšení tlačítek" else "Přepnuto na původní 1:1 zobrazení",
                                            false
                                        )
                                    }
                                )
                                TopSmallChip(
                                    label = "🔄",
                                    highlighted = false,
                                    onClick = {
                                        sendWebCommand(WebCommandType.RELOAD_FRESH, "")
                                        showBanner("Načítám nejnovější verzi Stavitele z GitHubu...", false)
                                    }
                                )
                                TopSmallChip(
                                    label = "📥",
                                    highlighted = false,
                                    onClick = {
                                        NativeBridge.openDownloadsScreen()
                                    }
                                )
                                TopSmallChip(
                                    label = "🔑",
                                    highlighted = vaultConfig.ghToken.isBlank(),
                                    onClick = { showVaultDialog = true }
                                )
                            }
                        }

                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }

                // Hlavni zivy pohled Stavitele z GitHub Pages
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    StavitelWebView(
                        url = liveUrl,
                        vaultConfig = vaultConfig,
                        command = activeCommand,
                        onCommandConsumed = { doneId ->
                            if (activeCommand?.id == doneId) {
                                activeCommand = null
                            }
                        },
                        onPageProgress = { p -> pageProgress = p },
                        onStatusMessage = { msg, isErr -> showBanner(msg, isErr) },
                        modifier = Modifier.fillMaxSize()
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = bannerMessage.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (bannerIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(
                                1.dp,
                                if (bannerIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = bannerMessage,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (bannerIsError) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Spodni ergonomicka lista pro ovladani jednim palcem (bez vkladani ze schranky)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomThumbButton(
                                label = "1️⃣ Projekt",
                                modifier = Modifier.weight(1f),
                                isPrimary = false,
                                isSecondaryAccent = false,
                                onClick = { sendWebCommand(WebCommandType.SCROLL_STEP_1, "") }
                            )
                            BottomThumbButton(
                                label = "2️⃣ Obchody",
                                modifier = Modifier.weight(1f),
                                isPrimary = false,
                                isSecondaryAccent = false,
                                onClick = { sendWebCommand(WebCommandType.SCROLL_STEP_2, "") }
                            )
                            BottomThumbButton(
                                label = "🚀 Spustit",
                                modifier = Modifier.weight(1.1f),
                                isPrimary = true,
                                isSecondaryAccent = false,
                                onClick = { sendWebCommand(WebCommandType.TRIGGER_RUN_MAIN, "") }
                            )
                        }
                    }
                }
            }

            if (showVaultDialog) {
                TokenVaultDialog(
                    initialConfig = vaultConfig,
                    onDismiss = { showVaultDialog = false },
                    onSave = { newCfg, verifiedLogin ->
                        vaultConfig = newCfg
                        NativeBridge.saveVaultConfig(newCfg)
                        if (verifiedLogin.isNotBlank()) {
                            tokenVerifiedOk = true
                            tokenVerifiedLogin = verifiedLogin
                        }
                        showVaultDialog = false
                        sendWebCommand(WebCommandType.INJECT_CREDENTIALS, "")
                        showBanner("🔐 GitHub token uložen v mobilu a propojen se Stavitelem!", false)
                    },
                    onOpenExternal = { NativeBridge.openExternalBrowser(liveUrl) }
                )
            }
        }
    }
}

@Composable
private fun TopSmallChip(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun BottomThumbButton(
    label: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean,
    isSecondaryAccent: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when {
        isPrimary -> MaterialTheme.colorScheme.primary
        isSecondaryAccent -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        isSecondaryAccent -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TokenVaultDialog(
    initialConfig: VaultConfig,
    onDismiss: () -> Unit,
    onSave: (VaultConfig, String) -> Unit,
    onOpenExternal: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var ownerInput by remember { mutableStateOf(initialConfig.ghOwner.ifBlank { "drozkar-glitch" }) }
    var tokenInput by remember { mutableStateOf(initialConfig.ghToken) }
    var mobileFit by remember { mutableStateOf(initialConfig.mobileZoomFit) }
    var revealToken by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkMsg by remember { mutableStateOf("") }
    var checkOk by remember { mutableStateOf<Boolean?>(null) }
    var verifiedUser by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔐 Nativní trezor Stavitele PM",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Token zůstává trvale uložený přímo v paměti telefonu a při každém spuštění se automaticky doplní do živého Stavitele z GitHubu.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = ownerInput,
                    onValueChange = { ownerInput = it },
                    label = { Text("GitHub účet (owner)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        checkOk = null
                    },
                    label = { Text("GitHub Personal Access Token") },
                    singleLine = true,
                    visualTransformation = if (revealToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { revealToken = !revealToken }) {
                            Text(if (revealToken) "🙈 Skrýt" else "👁️ Ukázat", fontSize = 12.sp)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        val tok = tokenInput.trim()
                        if (tok.isBlank()) {
                            checkOk = false
                            checkMsg = "Nejprve vlož GitHub token."
                        } else {
                            checking = true
                            checkMsg = "Ověřuji spojení s GitHub API..."
                            scope.launch {
                                val res = NativeBridge.verifyGitHubToken(tok)
                                checking = false
                                checkOk = res.ok
                                checkMsg = res.message
                                if (res.ok && res.login.isNotBlank()) {
                                    verifiedUser = res.login
                                    ownerInput = res.login
                                }
                            }
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (checking) "⏳ Ověřuji spojení..." else "🔍 Ověřit token",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (checkMsg.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (checkOk == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = checkMsg,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (checkOk == true) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mobilní zvětšení ovládacích prvků",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zpřehlední tlačítka a roletky Stavitele pro palec",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = mobileFit,
                        onCheckedChange = { mobileFit = it }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Zavřít")
                    }
                    Button(
                        onClick = {
                            val cleanOwner = ownerInput.trim().ifEmpty { "drozkar-glitch" }
                            val cleanToken = tokenInput.trim()
                            onSave(
                                VaultConfig(
                                    ghOwner = cleanOwner,
                                    ghToken = cleanToken,
                                    mobileZoomFit = mobileFit
                                ),
                                verifiedUser
                            )
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("💾 Uložit trezor", fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onOpenExternal) {
                        Text("🌐 Otevřít v prohlížeči", fontSize = 12.sp)
                    }
                    Text(
                        text = "© 2026 Created by Pavel Mareš",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
