package com.notifmate.helper

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern

suspend fun resolveMapsShortLink(shortUrl: String, context: Context): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try {
        var url = URL(shortUrl)
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; Pixel 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0 Mobile Safari/537.36"
            )
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                Log.d("MYDEBUG", "Redirected to: $location")

                if (location != null) {
                    if (location.startsWith("intent://")) {
                        val fallbackPrefix = "S.browser_fallback_url="
                        val fallbackIndex = location.indexOf(fallbackPrefix)
                        if (fallbackIndex != -1) {
                            val fallbackEncoded = location.substring(fallbackIndex + fallbackPrefix.length)
                            val fallbackUrl = URLDecoder.decode(fallbackEncoded, "UTF-8")
                            Log.d("MYDEBUG", "Extracted fallback URL: $fallbackUrl")
                            url = URL(fallbackUrl)
                            break
                        }
                    } else {
                        url = URL(location)
                    }
                    redirectCount++
                } else {
                    break
                }

                connection.disconnect()
            } else {
                break
            }
        }

        val finalUrl = url.toString()
        Log.d("MYDEBUG", "Final resolved URL: $finalUrl")

        val coordRegex = Pattern.compile("(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        val matcher = coordRegex.matcher(finalUrl)
        if (matcher.find()) {
            val lat = matcher.group(1).toDouble()
            val lon = matcher.group(2).toDouble()
            Log.d("MYDEBUG", "Extracted coordinates from URL: $lat, $lon")
            return@withContext Pair(lat, lon)
        }

        val placeName = extractPlaceNameFromUrl(finalUrl)
        if (placeName != null) {
            Log.d("MYDEBUG", "Trying to geocode place name: $placeName")
            return@withContext geocodePlaceName(context, placeName)
        } else {
            Log.w("MYDEBUG", "No coordinates or place name found.")
            null
        }
    } catch (e: IOException) {
        Log.e("MYDEBUG", "Error resolving link: ${e.message}")
        null
    }
}

fun extractPlaceNameFromUrl(url: String): String? {
    val regex = Regex("/maps/place/([^/?]+)")
    val match = regex.find(url)
    return match?.groupValues?.get(1)?.replace("+", " ")
}

suspend fun geocodePlaceName(context: Context, name: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val results = geocoder.getFromLocationName(name, 1)
        if (!results.isNullOrEmpty()) {
            val loc = results[0]
            Log.d("MYDEBUG", "Geocoded: $name → ${loc.latitude}, ${loc.longitude}")
            Pair(loc.latitude, loc.longitude)
        } else {
            Log.w("MYDEBUG", "Geocoder returned no results for: $name")
            null
        }
    } catch (e: Exception) {
        Log.e("MYDEBUG", "Geocoding failed: ${e.message}")
        null
    }
}