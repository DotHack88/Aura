package com.muse.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface YoutubeApiService {

    @GET("youtube/v3/search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") categoryId: String? = "10", // Musica
        @Query("maxResults") maxResults: Int = 25,
        @Query("key") apiKey: String
    ): YoutubeSearchResponse

    @GET("youtube/v3/search")
    suspend fun searchChannels(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "channel",
        @Query("maxResults") maxResults: Int = 3,
        @Query("key") apiKey: String
    ): YoutubeSearchResponse

    @GET("youtube/v3/channels")
    suspend fun getChannelDetails(
        @Query("part") part: String = "snippet,statistics,brandingSettings",
        @Query("id") channelIds: String,
        @Query("key") apiKey: String
    ): YoutubeChannelDetailsResponse

    @GET("youtube/v3/videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,contentDetails,statistics",
        @Query("id") videoIds: String,
        @Query("key") apiKey: String
    ): YoutubeVideoDetailsResponse
}

data class YoutubeSearchResponse(
    @SerializedName("items") val items: List<SearchItemDto>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class SearchItemDto(
    @SerializedName("id") val id: ResourceIdDto?,
    @SerializedName("snippet") val snippet: SnippetDto?
)

data class ResourceIdDto(
    @SerializedName("videoId") val videoId: String?,
    @SerializedName("channelId") val channelId: String?
)

data class SnippetDto(
    @SerializedName("title") val title: String?,
    @SerializedName("channelTitle") val channelTitle: String?,
    @SerializedName("channelId") val channelId: String?,
    @SerializedName("customUrl") val customUrl: String?,
    @SerializedName("thumbnails") val thumbnails: ThumbnailsDto?
)

data class ThumbnailsDto(
    @SerializedName("high") val high: ThumbnailDto?,
    @SerializedName("medium") val medium: ThumbnailDto?,
    @SerializedName("default") val default: ThumbnailDto?
)

data class ThumbnailDto(
    @SerializedName("url") val url: String?
)

data class YoutubeVideoDetailsResponse(
    @SerializedName("items") val items: List<VideoDetailItemDto>?
)

data class VideoDetailItemDto(
    @SerializedName("id") val id: String?,
    @SerializedName("snippet") val snippet: SnippetDto?,
    @SerializedName("contentDetails") val contentDetails: ContentDetailsDto?,
    @SerializedName("statistics") val statistics: VideoStatisticsDto?
)

data class VideoStatisticsDto(
    @SerializedName("viewCount") val viewCount: String?
)

data class YoutubeChannelDetailsResponse(
    @SerializedName("items") val items: List<ChannelDetailItemDto>?
)

data class ChannelDetailItemDto(
    @SerializedName("id") val id: String?,
    @SerializedName("snippet") val snippet: SnippetDto?,
    @SerializedName("statistics") val statistics: ChannelStatisticsDto?,
    @SerializedName("brandingSettings") val brandingSettings: ChannelBrandingSettingsDto?
)

data class ChannelStatisticsDto(
    @SerializedName("subscriberCount") val subscriberCount: String?,
    @SerializedName("videoCount") val videoCount: String?
)

data class ChannelBrandingSettingsDto(
    @SerializedName("image") val image: ChannelImageDto?
)

data class ChannelImageDto(
    @SerializedName("bannerExternalUrl") val bannerExternalUrl: String?
)

data class ContentDetailsDto(
    @SerializedName("duration") val duration: String? // ISO 8601, es. PT3M20S
)
