package cz.mares.stavitelpm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class VaultConfig(
    val ghOwner: String,
    val ghToken: String,
    val mobileZoomFit: Boolean
)

data class GhVerifyResult(
    val ok: Boolean,
    val login: String,
    val message: String
)

enum class WebCommandType {
    RELOAD_FRESH,
    INJECT_CREDENTIALS,
    PASTE_CODE_AND_PARSE,
    SCROLL_STEP_1,
    SCROLL_STEP_2,
    SCROLL_LOG,
    TRIGGER_RUN_MAIN,
    APPLY_MOBILE_LAYOUT
}

data class WebCommand(
    val id: Long,
    val type: WebCommandType,
    val payload: String = ""
)

expect object NativeBridge {
    fun loadVaultConfig(): VaultConfig
    fun saveVaultConfig(config: VaultConfig)
    fun readClipboardText(): String
    fun copyToClipboard(text: String)
    fun openDownloadsScreen()
    fun openExternalBrowser(url: String)
    suspend fun verifyGitHubToken(ghToken: String): GhVerifyResult
}

@Composable
expect fun StavitelWebView(
    url: String,
    vaultConfig: VaultConfig,
    command: WebCommand?,
    onCommandConsumed: (Long) -> Unit,
    onPageProgress: (Int) -> Unit,
    onStatusMessage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
)
