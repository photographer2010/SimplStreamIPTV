package com.simplstudios.simplstream.data.remote.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Extracts m3u8 stream URLs from embed providers using a hidden WebView.
 *
 * Flow:
 * 1. Load embed URL (videasy/vidfast/111movies) in an invisible WebView
 * 2. Intercept all network requests via WebViewClient
 * 3. Capture any request containing .m3u8
 * 4. Return the m3u8 URL, destroy the WebView
 *
 * The user NEVER sees the WebView — it's a disposable m3u8 sniffer.
 * ExoPlayer plays the extracted URL directly = no ads, native playback.
 */
@Singleton
class WebViewExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WebViewExtractor"
        private const val EXTRACTION_TIMEOUT_MS = 20_000L // 20 seconds per provider

        // Ad/tracking domains to block (speeds up extraction)
        private val BLOCKED_DOMAINS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "facebook.net", "facebook.com",
            "ads", "analytics", "tracker", "pixel", "popads",
            "popunder", "adserver", "cloudflareinsights",
            "histats.com", "disable-devtool", "ainouzaudre",
            "asgard-a.net", "juicyads.com", "exoclick.com",
            "trafficjunky.com", "ad.plus", "adsterra"
        )
    }

    /**
     * Provider configuration for embed URLs
     * Each provider takes a TMDB ID and returns an embed URL
     */
    enum class Provider(val displayName: String) {
        VIDEASY("Videasy"),
        VIDFAST("VidFast"),
        MOVIES111("111Movies"),
        EMBEDSU("Embed.su"),
        AUTOEMBED("AutoEmbed"),
        MULTIEMBED("MultiEmbed");

        fun getMovieUrl(tmdbId: Int): String = when (this) {
            VIDEASY -> "https://player.videasy.net/movie/$tmdbId"
            VIDFAST -> "https://vidfast.pro/movie/$tmdbId"
            MOVIES111 -> "https://111movies.net/movie/$tmdbId"
            EMBEDSU -> "https://embed.su/embed/movie/$tmdbId"
            AUTOEMBED -> "https://player.autoembed.cc/embed/movie/$tmdbId"
            MULTIEMBED -> "https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1"
        }

        fun getTvUrl(tmdbId: Int, season: Int, episode: Int): String = when (this) {
            VIDEASY -> "https://player.videasy.net/tv/$tmdbId/$season/$episode"
            VIDFAST -> "https://vidfast.pro/tv/$tmdbId/$season/$episode"
            MOVIES111 -> "https://111movies.net/tv/$tmdbId/$season/$episode"
            EMBEDSU -> "https://embed.su/embed/tv/$tmdbId/$season/$episode"
            AUTOEMBED -> "https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode"
            MULTIEMBED -> "https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1&s=$season&e=$episode"
        }
    }

    /**
     * A captured subtitle track from the embed provider
     */
    data class CapturedSubtitle(
        val url: String,
        val language: String,
        val label: String
    )

    /**
     * Result of an extraction attempt
     */
    data class ExtractionResult(
        val m3u8Url: String,
        val referer: String,
        val provider: Provider,
        val headers: Map<String, String> = emptyMap(),
        val subtitles: List<CapturedSubtitle> = emptyList()
    )

    /**
     * Extract m3u8 URL for a movie, trying all providers in order
     */
    suspend fun extractMovieStream(tmdbId: Int): ExtractionResult? {
        for (provider in Provider.entries) {
            val url = provider.getMovieUrl(tmdbId)
            Log.d(TAG, "Trying ${provider.displayName} for movie $tmdbId...")
            val result = extractFromUrl(url, provider)
            if (result != null) {
                Log.d(TAG, "${provider.displayName} SUCCESS: ${result.m3u8Url.take(80)}")
                return result
            }
            Log.w(TAG, "${provider.displayName} failed for movie $tmdbId")
        }
        return null
    }

    /**
     * Extract m3u8 URL for a TV episode, trying all providers in order
     */
    suspend fun extractTvStream(tmdbId: Int, season: Int, episode: Int): ExtractionResult? {
        for (provider in Provider.entries) {
            val url = provider.getTvUrl(tmdbId, season, episode)
            Log.d(TAG, "Trying ${provider.displayName} for S${season}E${episode}...")
            val result = extractFromUrl(url, provider)
            if (result != null) {
                Log.d(TAG, "${provider.displayName} SUCCESS: ${result.m3u8Url.take(80)}")
                return result
            }
            Log.w(TAG, "${provider.displayName} failed for S${season}E${episode}")
        }
        return null
    }

    /**
     * Core extraction: load URL in hidden WebView, intercept m3u8 and subtitle requests
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun extractFromUrl(embedUrl: String, provider: Provider): ExtractionResult? {
        return withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { continuation ->
                    var webView: WebView? = null
                    var resumed = false
                    val capturedSubtitles = mutableListOf<CapturedSubtitle>()
                    var pendingResult: ExtractionResult? = null

                    fun cleanup() {
                        webView?.let { wv ->
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.destroy()
                        }
                        webView = null
                    }

                    fun resumeOnce(result: ExtractionResult?) {
                        if (!resumed) {
                            resumed = true
                            cleanup()
                            continuation.resume(result)
                        }
                    }

                    fun finalizeResult(result: ExtractionResult) {
                        // Wait a brief moment to collect subtitles that load alongside the m3u8
                        pendingResult = result
                        Handler(Looper.getMainLooper()).postDelayed({
                            val finalResult = pendingResult?.copy(subtitles = capturedSubtitles.toList())
                            if (capturedSubtitles.isNotEmpty()) {
                                Log.d(TAG, "Captured ${capturedSubtitles.size} subtitle tracks")
                            }
                            resumeOnce(finalResult)
                        }, 2000) // 2 second delay to capture subtitle requests
                    }

                    try {
                        webView = WebView(context).apply {
                            // Make it invisible — 0x0 pixels, not added to any layout
                            layout(0, 0, 0, 0)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/122.0.0.0 Safari/537.36"
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                allowContentAccess = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url?.toString() ?: return null

                                    // Block ads/tracking to speed up extraction
                                    if (BLOCKED_DOMAINS.any { blocked -> url.contains(blocked, ignoreCase = true) }) {
                                        return WebResourceResponse("text/plain", "UTF-8", null)
                                    }

                                    // Capture subtitle URLs (.vtt, .srt, .ass)
                                    val lowerUrl = url.lowercase()
                                    if (lowerUrl.contains(".vtt") || lowerUrl.contains(".srt") ||
                                        lowerUrl.contains(".ass") || lowerUrl.contains("subtitle") ||
                                        lowerUrl.contains("caption")) {
                                        // Don't capture thumbnail/preview VTT (used for seek previews)
                                        if (!lowerUrl.contains("thumbnail") && !lowerUrl.contains("sprite") &&
                                            !lowerUrl.contains("preview")) {
                                            val langInfo = extractSubtitleLanguageFromUrl(url)
                                            val subtitle = CapturedSubtitle(
                                                url = url,
                                                language = langInfo.first,
                                                label = langInfo.second
                                            )
                                            capturedSubtitles.add(subtitle)
                                            Log.d(TAG, "CAUGHT subtitle: ${langInfo.second} -> ${url.take(80)}")
                                        }
                                    }

                                    // Catch m3u8 URLs
                                    if (url.contains(".m3u8") && !url.contains("thumbnail") && !url.contains("preview")) {
                                        Log.d(TAG, "CAUGHT m3u8: $url")

                                        val requestReferer = request?.requestHeaders?.get("Referer")
                                            ?: request?.requestHeaders?.get("referer")
                                        val actualReferer = requestReferer ?: embedUrl

                                        val origin = try {
                                            val uri = android.net.Uri.parse(actualReferer)
                                            "${uri.scheme}://${uri.host}"
                                        } catch (e: Exception) {
                                            actualReferer
                                        }

                                        Log.d(TAG, "Referer from request: $requestReferer")
                                        Log.d(TAG, "Using referer: $actualReferer, origin: $origin")

                                        Handler(Looper.getMainLooper()).post {
                                            finalizeResult(
                                                ExtractionResult(
                                                    m3u8Url = url,
                                                    referer = actualReferer,
                                                    provider = provider,
                                                    headers = mapOf(
                                                        "Referer" to actualReferer,
                                                        "Origin" to origin
                                                    )
                                                )
                                            )
                                        }
                                    }

                                    return super.shouldInterceptRequest(view, request)
                                }

                                @SuppressLint("WebViewClientOnReceivedSslError")
                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.proceed()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    // Only fail on main frame errors
                                    if (request?.isForMainFrame == true) {
                                        Log.e(TAG, "Main frame error: ${error?.description}")
                                        Handler(Looper.getMainLooper()).post {
                                            resumeOnce(null)
                                        }
                                    }
                                }
                            }
                        }

                        continuation.invokeOnCancellation {
                            cleanup()
                        }

                        Log.d(TAG, "Loading: $embedUrl")
                        webView?.loadUrl(embedUrl)

                    } catch (e: Exception) {
                        Log.e(TAG, "WebView creation failed: ${e.message}")
                        resumeOnce(null)
                    }
                }
            }
        }
    }

    /**
     * Extract language info from a subtitle URL.
     * Common patterns: /en.vtt, /English.vtt, /subs/eng.vtt, /subtitles?lang=en
     */
    private fun extractSubtitleLanguageFromUrl(url: String): Pair<String, String> {
        val lowerUrl = url.lowercase()

        // Common language codes/names in subtitle URLs
        val languageMap = mapOf(
            "english" to ("en" to "English"),
            "spanish" to ("es" to "Spanish"),
            "french" to ("fr" to "French"),
            "german" to ("de" to "German"),
            "italian" to ("it" to "Italian"),
            "portuguese" to ("pt" to "Portuguese"),
            "russian" to ("ru" to "Russian"),
            "japanese" to ("ja" to "Japanese"),
            "korean" to ("ko" to "Korean"),
            "chinese" to ("zh" to "Chinese"),
            "arabic" to ("ar" to "Arabic"),
            "hindi" to ("hi" to "Hindi"),
            "dutch" to ("nl" to "Dutch"),
            "polish" to ("pl" to "Polish"),
            "turkish" to ("tr" to "Turkish"),
            "thai" to ("th" to "Thai"),
            "vietnamese" to ("vi" to "Vietnamese"),
            "indonesian" to ("id" to "Indonesian"),
            "swedish" to ("sv" to "Swedish"),
            "danish" to ("da" to "Danish"),
            "norwegian" to ("no" to "Norwegian"),
            "finnish" to ("fi" to "Finnish"),
            "czech" to ("cs" to "Czech"),
            "greek" to ("el" to "Greek"),
            "hebrew" to ("he" to "Hebrew"),
            "hungarian" to ("hu" to "Hungarian"),
            "romanian" to ("ro" to "Romanian"),
            "bulgarian" to ("bg" to "Bulgarian"),
            "croatian" to ("hr" to "Croatian"),
            "malay" to ("ms" to "Malay")
        )

        // Check for language names in URL
        for ((name, codes) in languageMap) {
            if (lowerUrl.contains(name)) return codes
        }

        // Check for 2-3 letter language codes in common patterns
        val codePatterns = listOf(
            Regex("[/._-](en|eng)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](es|spa)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](fr|fra|fre)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](de|deu|ger)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](it|ita)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](pt|por)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](ru|rus)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](ja|jpn)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](ko|kor)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](zh|zho|chi)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](ar|ara)[/._-]", RegexOption.IGNORE_CASE),
            Regex("[/._-](hi|hin)[/._-]", RegexOption.IGNORE_CASE)
        )

        val codeToLang = mapOf(
            "en" to "English", "eng" to "English",
            "es" to "Spanish", "spa" to "Spanish",
            "fr" to "French", "fra" to "French", "fre" to "French",
            "de" to "German", "deu" to "German", "ger" to "German",
            "it" to "Italian", "ita" to "Italian",
            "pt" to "Portuguese", "por" to "Portuguese",
            "ru" to "Russian", "rus" to "Russian",
            "ja" to "Japanese", "jpn" to "Japanese",
            "ko" to "Korean", "kor" to "Korean",
            "zh" to "Chinese", "zho" to "Chinese", "chi" to "Chinese",
            "ar" to "Arabic", "ara" to "Arabic",
            "hi" to "Hindi", "hin" to "Hindi"
        )

        for (pattern in codePatterns) {
            val match = pattern.find(url)
            if (match != null) {
                val code = match.groupValues[1].lowercase()
                val lang = codeToLang[code] ?: code.uppercase()
                return code to lang
            }
        }

        // Also check query params like ?lang=en or ?language=English
        val langParam = Regex("[?&](?:lang|language)=([^&]+)").find(lowerUrl)
        if (langParam != null) {
            val value = langParam.groupValues[1]
            val lang = codeToLang[value] ?: languageMap[value]?.second ?: value.replaceFirstChar { it.uppercase() }
            return value to lang
        }

        // Default: assume English for single unnamed subtitle
        return "en" to "English"
    }
}
