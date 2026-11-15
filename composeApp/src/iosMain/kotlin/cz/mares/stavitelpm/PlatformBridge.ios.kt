package cz.mares.stavitelpm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readValue
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectZero
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val IOS_KLIC_OWNER = "stavitel_pm_ios_owner"
private const val IOS_KLIC_TOKEN = "stavitel_pm_ios_token"
private const val IOS_KLIC_MOBIL = "stavitel_pm_ios_mobil"

@OptIn(ExperimentalEncodingApi::class)
private fun iosZakodujB64(vstup: String): String {
    return Base64.encode(vstup.encodeToByteArray())
}

@OptIn(ExperimentalForeignApi::class)
private fun nsDataNaRetezec(data: NSData?): String {
    if (data == null) return ""
    val delka = data.length.toInt()
    if (delka <= 0) return ""
    val pole = ByteArray(delka)
    pole.usePinned { pripnute ->
        memcpy(pripnute.addressOf(0), data.bytes, data.length)
    }
    return pole.decodeToString()
}

actual object NativeBridge {
    actual fun loadVaultConfig(): VaultConfig {
        val def = NSUserDefaults.standardUserDefaults
        val ulozenyOwner = def.stringForKey(IOS_KLIC_OWNER) ?: "drozkar-glitch"
        val ulozenyToken = def.stringForKey(IOS_KLIC_TOKEN) ?: ""
        val maMobilKlic = def.objectForKey(IOS_KLIC_MOBIL) != null
        val mobilStyl = if (maMobilKlic) def.boolForKey(IOS_KLIC_MOBIL) else true
        return VaultConfig(
            ghOwner = ulozenyOwner,
            ghToken = ulozenyToken,
            mobileZoomFit = mobilStyl
        )
    }

    actual fun saveVaultConfig(config: VaultConfig) {
        val def = NSUserDefaults.standardUserDefaults
        def.setObject(config.ghOwner.trim(), forKey = IOS_KLIC_OWNER)
        def.setObject(config.ghToken.trim(), forKey = IOS_KLIC_TOKEN)
        def.setBool(config.mobileZoomFit, forKey = IOS_KLIC_MOBIL)
        def.synchronize()
    }

    actual fun readClipboardText(): String {
        return UIPasteboard.generalPasteboard.string ?: ""
    }

    actual fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    actual fun openDownloadsScreen() {
        val cil = NSURL.URLWithString("shareddocuments://") ?: return
        UIApplication.sharedApplication.openURL(
            cil,
            options = kotlin.collections.emptyMap<Any?, Any>(),
            completionHandler = null
        )
    }

    actual fun openExternalBrowser(url: String) {
        val cil = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(
            cil,
            options = kotlin.collections.emptyMap<Any?, Any>(),
            completionHandler = null
        )
    }

    actual suspend fun verifyGitHubToken(ghToken: String): GhVerifyResult =
        suspendCancellableCoroutine { pokracovani ->
            val cistyToken = ghToken.trim()
            val cilUrl = NSURL.URLWithString("https://api.github.com/user")
            if (cistyToken.isEmpty() || cilUrl == null) {
                pokracovani.resume(GhVerifyResult(false, "", "Token je prázdný."))
                return@suspendCancellableCoroutine
            }

            val dotaz = NSMutableURLRequest.requestWithURL(cilUrl).apply {
                setHTTPMethod("GET")
                setValue("Bearer $cistyToken", forHTTPHeaderField = "Authorization")
                setValue("application/vnd.github+json", forHTTPHeaderField = "Accept")
                setValue("StavitelPM-iOS", forHTTPHeaderField = "User-Agent")
            }

            val uloha = NSURLSession.sharedSession.dataTaskWithRequest(dotaz) { data, response, error ->
                if (!pokracovani.isActive) return@dataTaskWithRequest
                if (error != null) {
                    pokracovani.resume(
                        GhVerifyResult(false, "", "Chyba sítě: ${error.localizedDescription}")
                    )
                    return@dataTaskWithRequest
                }
                val stav = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
                val telo = nsDataNaRetezec(data)
                if (stav == 200) {
                    val shoda = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(telo)
                    val login = shoda?.groupValues?.getOrNull(1).orEmpty()
                    pokracovani.resume(
                        GhVerifyResult(true, login, "✅ Spojení ověřeno! Přihlášen účet @$login.")
                    )
                } else {
                    pokracovani.resume(
                        GhVerifyResult(false, "", "GitHub vrátil HTTP $stav — zkontroluj platnost tokenu.")
                    )
                }
            }
            pokracovani.invokeOnCancellation { uloha.cancel() }
            uloha.resume()
        }
}

private fun iosSyncSkript(cfg: VaultConfig): String {
    val b64Owner = iosZakodujB64(cfg.ghOwner.trim().ifEmpty { "drozkar-glitch" })
    val b64Token = iosZakodujB64(cfg.ghToken.trim())
    val mobil = if (cfg.mobileZoomFit) "1" else "0"
    return """
        (function() {
          try {
            var dec = function(b) {
              if (!b) return "";
              var bin = atob(b);
              var u = new Uint8Array(bin.length);
              for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
              return new TextDecoder("utf-8").decode(u);
            };
            var uName = dec("$b64Owner");
            var uTok = dec("$b64Token");
            if (uName) {
              localStorage.setItem("stavitel_gh_user", uName);
              var elU = document.getElementById("ghUsername");
              if (elU) elU.value = uName;
            }
            if (uTok) {
              localStorage.setItem("stavitel_gh_token", uTok);
              var elT = document.getElementById("ghToken");
              if (elT) elT.value = uTok;
            }
            var sid = "stavitelPmIosCss";
            var st = document.getElementById(sid);
            if ("$mobil" === "1") {
              if (!st) {
                st = document.createElement("style");
                st.id = sid;
                st.textContent = "button, select, input { min-height: 44px !important; font-size: 15px !important; }";
                document.head.appendChild(st);
              }
            } else if (st) {
              st.remove();
            }
          } catch (e) {}
        })();
    """.trimIndent()
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun StavitelWebView(
    url: String,
    vaultConfig: VaultConfig,
    command: WebCommand?,
    onCommandConsumed: (Long) -> Unit,
    onPageProgress: (Int) -> Unit,
    onStatusMessage: (String, Boolean) -> Unit,
    modifier: Modifier
) {
    val aktualniHlaseni by rememberUpdatedState(onStatusMessage)
    var wkInstance by remember { mutableStateOf<WKWebView?>(null) }

    LaunchedEffect(vaultConfig) {
        wkInstance?.evaluateJavaScript(iosSyncSkript(vaultConfig), completionHandler = null)
    }

    LaunchedEffect(command?.id) {
        val cmd = command ?: return@LaunchedEffect
        val wk = wkInstance ?: return@LaunchedEffect
        when (cmd.type) {
            WebCommandType.RELOAD_FRESH -> {
                NSURL.URLWithString("$url?_m=${NSDate().timeIntervalSince1970.toLong()}")?.let { cil ->
                    val req = NSURLRequest.requestWithURL(
                        cil,
                        cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
                        timeoutInterval = 30.0
                    )
                    wk.loadRequest(req)
                }
            }
            WebCommandType.INJECT_CREDENTIALS, WebCommandType.APPLY_MOBILE_LAYOUT -> {
                wk.evaluateJavaScript(iosSyncSkript(vaultConfig), completionHandler = null)
            }
            WebCommandType.SCROLL_STEP_1 -> {
                wk.evaluateJavaScript(
                    "document.getElementById('projectSelect')?.scrollIntoView({behavior:'smooth',block:'center'});",
                    completionHandler = null
                )
            }
            WebCommandType.SCROLL_STEP_2 -> {
                wk.evaluateJavaScript(
                    "(document.getElementById('selectiveStoreTogglesBox') || document.getElementById('cmPublishMode'))?.scrollIntoView({behavior:'smooth',block:'center'});",
                    completionHandler = null
                )
            }
            WebCommandType.SCROLL_LOG -> {
                wk.evaluateJavaScript(
                    "document.getElementById('logBox')?.scrollIntoView({behavior:'smooth',block:'center'});",
                    completionHandler = null
                )
            }
            WebCommandType.PASTE_CODE_AND_PARSE -> {
                val b64 = iosZakodujB64(cmd.payload)
                val js = """
                    (function(){
                      var bin = atob("$b64");
                      var arr = new Uint8Array(bin.length);
                      for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
                      var txt = new TextDecoder("utf-8").decode(arr);
                      var el = document.getElementById("rawInput") || document.querySelector("textarea");
                      if (!el) return;
                      el.value = txt;
                      el.dispatchEvent(new Event("input", { bubbles: true }));
                    })();
                """.trimIndent()
                wk.evaluateJavaScript(js) { _, _ ->
                    aktualniHlaseni("📋 Kód ze schránky vložen do Kroku 3!", false)
                }
            }
            WebCommandType.TRIGGER_RUN_MAIN -> {
                wk.evaluateJavaScript("document.getElementById('btnAll')?.click();") { _, _ ->
                    aktualniHlaseni("🚀 Spouštím vybranou akci Stavitele...", false)
                }
            }
        }
        onCommandConsumed(cmd.id)
    }

    UIKitView(
        factory = {
            val konfigurace = WKWebViewConfiguration()
            WKWebView(frame = CGRectZero.readValue(), configuration = konfigurace).apply {
                NSURL.URLWithString(url)?.let { cil ->
                    val req = NSURLRequest.requestWithURL(
                        cil,
                        cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
                        timeoutInterval = 30.0
                    )
                    loadRequest(req)
                }
                onPageProgress(100)
                wkInstance = this
            }
        },
        modifier = modifier,
        update = { pohled ->
            wkInstance = pohled
        }
    )
}
