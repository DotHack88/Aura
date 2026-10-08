package com.muse.app.utils

/**
 * Upgrades a YouTube / YouTube Music thumbnail URL to the highest possible resolution.
 *
 * YouTube and YouTube Music use two URL patterns:
 *
 * 1. i.ytimg.com  (video thumbnails):
 *    https://i.ytimg.com/vi/{videoId}/hqdefault.jpg
 *    → replaced with maxresdefault.jpg (1280×720)
 *
 * 2. yt3.googleusercontent.com (album/artist art):
 *    https://yt3.googleusercontent.com/…=w226-h226-s-l90-rj
 *    → size suffix replaced with =w1080-h1080-l90-rj (1080×1080)
 *
 * If neither pattern matches the URL is returned unchanged.
 */
fun String.toHighResThumbnail(): String {
    if (isBlank()) return this

    return when {
        // Pattern 1: i.ytimg.com video thumbnails
        // Per i mix e video YouTube, usiamo hqdefault che è quasi sempre disponibile.
        // Se l'URL punta già a hqdefault o maxresdefault, non tocchiamo nulla.
        contains("i.ytimg.com") -> {
            when {
                contains("maxresdefault") -> this // già al massimo
                contains("hqdefault") -> this // già buono
                else -> replace("mqdefault.jpg", "hqdefault.jpg")
                    .replace("sddefault.jpg", "hqdefault.jpg")
                    .replace(Regex("/(default|[0-9]+)\\.jpg"), "/hqdefault.jpg")
            }
        }

        // Pattern 2: yt3.googleusercontent.com with size params like =w226-h226-s-l90-rj
        contains("yt3.googleusercontent.com") || contains("yt3.ggpht.com") -> {
            // Strip any existing size suffix and apply a high-res one
            val baseUrl = this.substringBefore("=w").substringBefore("=s")
            if (baseUrl == this) {
                // No size param found, append high-res param
                "$this=w1080-h1080-l90-rj"
            } else {
                "$baseUrl=w1080-h1080-l90-rj"
            }
        }

        else -> this
    }
}

/**
 * Dato un videoId di YouTube, costruisce un URL thumbnail garantito disponibile.
 * Utile come fallback quando il campo thumbnailUrl arriva vuoto.
 */
fun buildYoutubeThumbnailUrl(videoId: String): String {
    return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
