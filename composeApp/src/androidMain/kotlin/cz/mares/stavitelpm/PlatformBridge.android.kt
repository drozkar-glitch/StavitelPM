package cz.mares.stavitelpm

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object StavitelAndroidHold {
    var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }
}

private const val TREZOR_PREFS = "stavitel_pm_trezor_v1"
private const val KLIC_OWNER = "gh_owner"
private const val KLIC_TOKEN = "gh_token"
private const val KLIC_MOBIL_STYL = "mobil_zoom_fit"

private fun zakodujBase64Utf8(text: String): String {
    return Base64.encodeToString(text.encodeToByteArray(), Base64.NO_WRAP)
}

private class StavitelJsMost(
    private val context: Context,
    private val hlaseniCallback: (String, Boolean) -> Unit
) {
    private val hlavniHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun ulozSouborZWebu(puvodniNazev: String, mimeTyp: String, cistaBase64: String) {
        try {
            val cistyNazev = puvodniNazev.trim()
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .ifEmpty { "Stavitel_soubor_${System.currentTimeMillis()}.apk" }
            val bajty = Base64.decode(cistaBase64, Base64.DEFAULT)
            val cilovyMime = mimeTyp.ifBlank {
                if (cistyNazev.endsWith(".apk", ignoreCase = true)) {
                    "application/vnd.android.package-archive"
                } else {
                    "application/octet-stream"
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val hodnoty = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cistyNazev)
                    put(MediaStore.MediaColumns.MIME_TYPE, cilovyMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val polozkaUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, hodnoty)
                    ?: throw IllegalStateException("Nelze vytvořit položku ve složce Stažené soubory.")
                resolver.openOutputStream(polozkaUri)?.use { proud ->
                    proud.write(bajty)
                    proud.flush()
                }
                val hotovo = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(polozkaUri, hotovo, null, null)
            } else {
                val slozka = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                if (!slozka.exists()) slozka.mkdirs()
                FileOutputStream(File(slozka, cistyNazev)).use { proud ->
                    proud.write(bajty)
                    proud.flush()
                }
            }

            hlavniHandler.post {
                hlaseniCallback("📥 Uloženo do Stažených souborů: $cistyNazev (klepni nahoře na 📥)", false)
            }
        } catch (chyba: Exception) {
            hlavniHandler.post {
                hlaseniCallback("❌ Chyba uložení souboru: ${chyba.message}", true)
            }
        }
    }
}

actual object NativeBridge {
    actual fun loadVaultConfig(): VaultConfig {
        val ctx = StavitelAndroidHold.appCtx
            ?: return VaultConfig(ghOwner = "drozkar-glitch", ghToken = "", mobileZoomFit = true)
        val sp = ctx.getSharedPreferences(TREZOR_PREFS, Context.MODE_PRIVATE)
        return VaultConfig(
            ghOwner = sp.getString(KLIC_OWNER, "drozkar-glitch") ?: "drozkar-glitch",
            ghToken = sp.getString(KLIC_TOKEN, "") ?: "",
            mobileZoomFit = sp.getBoolean(KLIC_MOBIL_STYL, true)
        )
    }

    actual fun saveVaultConfig(config: VaultConfig) {
        val ctx = StavitelAndroidHold.appCtx ?: return
        ctx.getSharedPreferences(TREZOR_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KLIC_OWNER, config.ghOwner.trim())
            .putString(KLIC_TOKEN, config.ghToken.trim())
            .putBoolean(KLIC_MOBIL_STYL, config.mobileZoomFit)
            .apply()
    }

    actual fun readClipboardText(): String {
        val ctx = StavitelAndroidHold.appCtx ?: return ""
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        val klip = cm.primaryClip ?: return ""
        if (klip.itemCount <= 0) return ""
        return klip.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
    }

    actual fun copyToClipboard(text: String) {
        val ctx = StavitelAndroidHold.appCtx ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("stavitel_pm", text))
    }

    actual fun openDownloadsScreen() {
        val ctx = StavitelAndroidHold.appCtx ?: return
        runCatching {
            val zamer = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(zamer)
        }
    }

    actual fun openExternalBrowser(url: String) {
        val ctx = StavitelAndroidHold.appCtx ?: return
        runCatching {
            val zamer = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(zamer)
        }
    }

    actual suspend fun verifyGitHubToken(ghToken: String): GhVerifyResult = withContext(Dispatchers.IO) {
        val cistyToken = ghToken.trim()
        if (cistyToken.isEmpty()) {
            return@withContext GhVerifyResult(false, "", "Token je prázdný.")
        }
        var spojeni: HttpURLConnection? = null
        try {
            spojeni = (URL("https://api.github.com/user").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Authorization", "Bearer $cistyToken")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "StavitelPM-Android")
            }
            val kod = spojeni.responseCode
            val odpoved = (if (kod in 200..299) spojeni.inputStream else spojeni.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (kod == 200) {
                val shoda = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"").find(odpoved)
                val uzivatel = shoda?.groupValues?.getOrNull(1).orEmpty()
                GhVerifyResult(true, uzivatel, "✅ Spojení ověřeno! Přihlášen GitHub účet @$uzivatel.")
            } else {
                GhVerifyResult(false, "", "GitHub vrátil stav HTTP $kod — zkontroluj platnost tokenu.")
            }
        } catch (e: Exception) {
            GhVerifyResult(false, "", "Chyba sítě při ověřování: ${e.message}")
        } finally {
            spojeni?.disconnect()
        }
    }
}

private fun sestavSynchronizacniJs(cfg: VaultConfig): String {
    val b64Owner = zakodujBase64Utf8(cfg.ghOwner.trim().ifEmpty { "drozkar-glitch" })
    val b64Token = zakodujBase64Utf8(cfg.ghToken.trim())
    val mobilRezim = if (cfg.mobileZoomFit) "1" else "0"

    return """
        (function() {
          try {
            var decUtf8 = function(b64) {
              if (!b64) return "";
              var bin = atob(b64);
              var arr = new Uint8Array(bin.length);
              for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
              return new TextDecoder("utf-8").decode(arr);
            };
            var ghUsr = decUtf8("$b64Owner");
            var ghTkn = decUtf8("$b64Token");

            if (ghUsr) {
              localStorage.setItem("stavitel_gh_user", ghUsr);
              localStorage.setItem("ghOwner", ghUsr);
              var uEl = document.getElementById("ghUsername");
              if (uEl && uEl.value !== ghUsr) uEl.value = ghUsr;
            }
            if (ghTkn) {
              localStorage.setItem("stavitel_gh_token", ghTkn);
              localStorage.setItem("ghToken", ghTkn);
              var tEl = document.getElementById("ghToken");
              if (tEl && tEl.value !== ghTkn) tEl.value = ghTkn;
            }

            if (!window.__stavitelPmBlobZachyt) {
              window.__stavitelPmBlobZachyt = true;
              var puvodniKlik = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function() {
                var odkaz = String(this.href || "");
                var jmeno = String(this.download || "Stavitel_soubor.apk");
                if (odkaz.indexOf("blob:") === 0 && window.StavitelMobilMost) {
                  fetch(odkaz).then(function(r) { return r.blob(); }).then(function(bl) {
                    var ctecka = new FileReader();
                    ctecka.onloadend = function() {
                      var dataUrl = String(ctecka.result || "");
                      var carka = dataUrl.indexOf(",");
                      var surovaB64 = carka >= 0 ? dataUrl.slice(carka + 1) : dataUrl;
                      window.StavitelMobilMost.ulozSouborZWebu(jmeno, bl.type || "", surovaB64);
                    };
                    ctecka.readAsDataURL(bl);
                  });
                  return;
                }
                return puvodniKlik.apply(this, arguments);
              };
            }

            var stylId = "stavitelPmMobilniCss";
            var staryStyl = document.getElementById(stylId);
            if ("$mobilRezim" === "1") {
              if (!staryStyl) {
                staryStyl = document.createElement("style");
                staryStyl.id = stylId;
                staryStyl.textContent = [
                  "html, body { max-width: 100vw !important; overflow-x: hidden !important; -webkit-text-size-adjust: 100%; }",
                  "button, select, input[type='text'], input[type='password'] { min-height: 44px !important; font-size: 15px !important; }",
                  "textarea { font-size: 13px !important; min-height: 150px !important; }",
                  ".tools-buttons { display: flex !important; flex-wrap: wrap !important; gap: 8px !important; }",
                  ".tools-buttons > button { flex: 1 1 46% !important; white-space: normal !important; height: auto !important; padding: 10px 8px !important; }"
                ].join("\n");
                document.head.appendChild(staryStyl);
              }
            } else if (staryStyl) {
              staryStyl.remove();
            }

            if (ghTkn && !window.__stavitelPmPrvniSync) {
              window.__stavitelPmPrvniSync = true;
              if (typeof window.checkVaultKeysStatus === "function") window.checkVaultKeysStatus(true);
              var sel = document.getElementById("projectSelect");
              if (sel && sel.options.length <= 1 && typeof window.fetchGitHubRepos === "function") {
                window.fetchGitHubRepos(true);
              }
            }
          } catch (err) {}
        })();
    """.trimIndent()
}

private fun provedWebPrikaz(
    wv: WebView,
    prikaz: WebCommand,
    cfg: VaultConfig,
    zakladniUrl: String,
    hlaseni: (String, Boolean) -> Unit
) {
    when (prikaz.type) {
        WebCommandType.RELOAD_FRESH -> {
            wv.clearCache(true)
            wv.loadUrl("$zakladniUrl?_m=${System.currentTimeMillis()}")
        }
        WebCommandType.INJECT_CREDENTIALS -> {
            wv.evaluateJavascript(
                "window.__stavitelPmPrvniSync = false;\n" + sestavSynchronizacniJs(cfg),
                null
            )
        }
        WebCommandType.APPLY_MOBILE_LAYOUT -> {
            wv.evaluateJavascript(sestavSynchronizacniJs(cfg), null)
        }
        WebCommandType.SCROLL_STEP_1 -> {
            wv.evaluateJavascript(
                "(function(){ var e = document.getElementById('projectSelect'); if(e) e.scrollIntoView({behavior:'smooth', block:'center'}); })();",
                null
            )
        }
        WebCommandType.SCROLL_STEP_2 -> {
            wv.evaluateJavascript(
                "(function(){ var e = document.getElementById('selectiveStoreTogglesBox') || document.getElementById('cmPublishMode'); if(e && e.parentElement) e.parentElement.scrollIntoView({behavior:'smooth', block:'center'}); })();",
                null
            )
        }
        WebCommandType.SCROLL_LOG -> {
            wv.evaluateJavascript(
                "(function(){ var e = document.getElementById('logBox'); if(e) e.scrollIntoView({behavior:'smooth', block:'center'}); })();",
                null
            )
        }
        WebCommandType.PASTE_CODE_AND_PARSE -> {
            val b64Kod = zakodujBase64Utf8(prikaz.payload)
            val skriptVlozeni = """
                (function() {
                  try {
                    var bin = atob("$b64Kod");
                    var arr = new Uint8Array(bin.length);
                    for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
                    var obsah = new TextDecoder("utf-8").decode(arr);
                    var pole = document.getElementById("rawInput") || document.querySelector("textarea");
                    if (!pole) return "MISSING";
                    pole.value = obsah;
                    var attr = String(pole.getAttribute("oninput") || "");
                    pole.dispatchEvent(new Event("input", { bubbles: true }));
                    if (attr.indexOf("handlePasteInput") === -1 && typeof window.handlePasteInput === "function") {
                      window.handlePasteInput();
                    }
                    pole.scrollIntoView({ behavior: "smooth", block: "center" });
                    return "OK";
                  } catch (e) {
                    return "ERR:" + e.message;
                  }
                })();
            """.trimIndent()
            wv.evaluateJavascript(skriptVlozeni) { vysledek ->
                val cisty = (vysledek ?: "").trim('"')
                if (cisty == "OK") {
                    hlaseni("📋 Kód ze schránky vložen do Kroku 3 a připraven!", false)
                } else {
                    hlaseni("⚠️ Nepodařilo se najít vstupní pole Kroku 3 ($cisty)", true)
                }
            }
        }
        WebCommandType.TRIGGER_RUN_MAIN -> {
            wv.evaluateJavascript(
                "(function(){ var b = document.getElementById('btnAll'); if(b){ b.scrollIntoView({behavior:'smooth', block:'center'}); b.click(); return 'OK'; } return 'NONE'; })();"
            ) { vysl ->
                if ((vysl ?: "").contains("OK")) {
                    hlaseni("🚀 Spouštím vybranou akci Stavitele...", false)
                } else {
                    hlaseni("⚠️ Hlavní tlačítko Stavitele nebylo nalezeno.", true)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
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
    val aktualniCfg by rememberUpdatedState(vaultConfig)
    val aktualniHlaseni by rememberUpdatedState(onStatusMessage)
    val aktualniProgres by rememberUpdatedState(onPageProgress)

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lzeJitZpet by remember { mutableStateOf(false) }
    var cekajiciSouborCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val vyberSouboruLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { vybraneUri ->
        val cb = cekajiciSouborCallback
        cekajiciSouborCallback = null
        if (vybraneUri != null) {
            cb?.onReceiveValue(arrayOf(vybraneUri))
        } else {
            cb?.onReceiveValue(null)
        }
    }

    BackHandler(enabled = lzeJitZpet) {
        webViewInstance?.goBack()
    }

    LaunchedEffect(vaultConfig) {
        webViewInstance?.evaluateJavascript(sestavSynchronizacniJs(vaultConfig), null)
    }

    LaunchedEffect(command?.id) {
        val cmd = command ?: return@LaunchedEffect
        val wv = webViewInstance ?: return@LaunchedEffect
        provedWebPrikaz(wv, cmd, aktualniCfg, url) { zprava, jeChyba ->
            aktualniHlaseni(zprava, jeChyba)
        }
        onCommandConsumed(cmd.id)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                }

                addJavascriptInterface(
                    StavitelJsMost(ctx.applicationContext) { msg, err ->
                        aktualniHlaseni(msg, err)
                    },
                    "StavitelMobilMost"
                )

                setDownloadListener { stahovaneUrl, userAgent, contentDisposition, mimeType, _ ->
                    if (stahovaneUrl.startsWith("http://") || stahovaneUrl.startsWith("https://")) {
                        runCatching {
                            val jmenoSouboru = URLUtil.guessFileName(stahovaneUrl, contentDisposition, mimeType)
                            val dotaz = DownloadManager.Request(Uri.parse(stahovaneUrl)).apply {
                                setMimeType(mimeType)
                                CookieManager.getInstance().getCookie(stahovaneUrl)?.let {
                                    addRequestHeader("Cookie", it)
                                }
                                addRequestHeader("User-Agent", userAgent)
                                setTitle(jmenoSouboru)
                                setDescription("Stahování ze Stavitele PM")
                                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, jmenoSouboru)
                            }
                            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                            dm.enqueue(dotaz)
                            aktualniHlaseni("📲 Stahuji $jmenoSouboru do Stažených souborů...", false)
                        }.onFailure {
                            NativeBridge.openExternalBrowser(stahovaneUrl)
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val cil = request?.url?.toString() ?: return false
                        if (cil.endsWith(".apk", ignoreCase = true) || cil.contains("/releases/download/")) {
                            return false
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        lzeJitZpet = view?.canGoBack() == true
                        view?.evaluateJavascript(sestavSynchronizacniJs(aktualniCfg), null)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        aktualniProgres(newProgress)
                        lzeJitZpet = view?.canGoBack() == true
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        cekajiciSouborCallback?.onReceiveValue(null)
                        cekajiciSouborCallback = filePathCallback
                        runCatching {
                            vyberSouboruLauncher.launch("*/*")
                        }.onFailure {
                            cekajiciSouborCallback = null
                            return false
                        }
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?
                    ): Boolean {
                        val kontextOkna = view?.context ?: return false
                        AlertDialog.Builder(kontextOkna)
                            .setTitle("Stavitel PM")
                            .setMessage(message.orEmpty())
                            .setPositiveButton("OK") { _, _ -> result?.confirm() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?
                    ): Boolean {
                        val kontextOkna = view?.context ?: return false
                        AlertDialog.Builder(kontextOkna)
                            .setTitle("Potvrzení akce")
                            .setMessage(message.orEmpty())
                            .setPositiveButton("Potvrdit") { _, _ -> result?.confirm() }
                            .setNegativeButton("Zrušit") { _, _ -> result?.cancel() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }
                }

                webViewInstance = this
                loadUrl("$url?_m=${System.currentTimeMillis()}")
            }
        },
        update = { wv ->
            webViewInstance = wv
        }
    )
}
