package com.notifmate.helper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.notifmate.helper.CustomUtils.truncate
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

object MapsShortLinkResolver {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(context: Context, shortUrl: String): Triple<Double, Double, String>? =
        suspendCancellableCoroutine { cont ->

            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10)"

            fun extractCoordinates(url: String): Triple<Double, Double, String>? {
                val decodedUrl = if (url.startsWith("intent://") && url.contains("S.browser_fallback_url=")) {
                    val fallbackPrefix = "S.browser_fallback_url="
                    val startIndex = url.indexOf(fallbackPrefix) + fallbackPrefix.length
                    val endIndex = url.indexOf(";end;", startIndex).takeIf { it != -1 } ?: url.length
                    val encoded = url.substring(startIndex, endIndex)
                    val decoded = URLDecoder.decode(encoded, "UTF-8")
                    Log.d("MYDEBUG MSLR", "Decoded fallback URL: $decoded")
                    decoded
                } else url

                val patterns = listOf(
                    Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)"),
                    Pattern.compile("(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
                )

                for (pattern in patterns) {
                    val matcher = pattern.matcher(decodedUrl)
                    if (matcher.find()) {
                        val lat = matcher.group(1).toDouble()
                        val lon = matcher.group(2).toDouble()
                        Log.d("MYDEBUG MSLR", "Matched coordinates: $lat, $lon")
                        return Triple(lat, lon, "Custom Pin")
                    }
                }

                // Try to extract place name
                val placeRegex = Regex("/maps/place/([^/?]+)")
                val nameMatch = placeRegex.find(decodedUrl)
                val name = nameMatch?.groupValues?.get(1)?.replace("+", " ")?.replace(",", "")

                Log.d("MYDEBUG MSLR", "Trying to geocode place name: $name")
                if (!name.isNullOrBlank()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val results = geocoder.getFromLocationName(name, 1)
                    if (!results.isNullOrEmpty()) {
                        val loc = results[0]
                        Log.d("MYDEBUG MSLR", "Geocoded: $name → ${loc.latitude}, ${loc.longitude}")
                        return Triple(loc.latitude, loc.longitude, name.truncate(20))
                    } else {
                        Log.w("MYDEBUG MSLR", "Geocoder returned no results for: $name")
                    }
                }

                Log.d("MYDEBUG MSLR", "No match in: $decodedUrl")
                return null
            }


            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.toString()?.let {
                        extractCoordinates(it)?.let { coords ->
                            if (cont.isActive) cont.resume(coords)
                            webView.destroy()
                        }
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    url?.let {
                        extractCoordinates(it)?.let { coords ->
                            if (cont.isActive) cont.resume(coords)
                            webView.destroy()
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let {
                        extractCoordinates(it)?.let { coords ->
                            if (cont.isActive) cont.resume(coords)
                            webView.destroy()
                        } ?: run {
                            if (cont.isActive) cont.resume(null)
                            webView.destroy()
                        }
                    }
                }
            }

            Log.d("MYDEBUG MSLR", "Loading short URL: $shortUrl")
            webView.loadUrl(shortUrl)

            cont.invokeOnCancellation {
                Log.d("MYDEBUG MSLR", "Cancelled. WebView destroyed.")
                webView.destroy()
            }
        }
}
