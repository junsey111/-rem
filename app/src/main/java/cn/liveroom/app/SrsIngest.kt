package cn.liveroom.app

/**
 * Ordinary broadcast ingest for `ossrs/srs:6` `conf/docker.conf`.
 * HLS playback is `{playback}/live/{liveSessionId}.m3u8`; the matching publish URL is
 * `rtmp://<host>:<port>/live/{liveSessionId}` (app `live`, stream = Revenue live session id).
 * RTMP is the ingest that docker.conf turns into HLS directly. WebRTC WHIP would also need
 * `rtc_to_rtmp`, and a second WebRTC stack would clash with the LiveKit SDK.
 */
internal const val SRS_STREAM_APP = "live"

internal fun srsIngestUrl(rtmpBase: String, roomId: String): String? {
    val base = rtmpBase.trim().trimEnd('/')
    val scheme = base.substringBefore("://", missingDelimiterValue = "")
    if (scheme != "rtmp" && scheme != "rtmps") return null
    val authority = base.substringAfter("://", "")
    if (authority.isBlank() || authority.startsWith("/") || authority.contains('/') || authority.contains('@')) return null
    if (authority.substringBefore(':').isBlank()) return null
    if (!Regex("[1-9][0-9]{0,15}").matches(roomId)) return null
    return "$base/$SRS_STREAM_APP/$roomId"
}

/**
 * The host camera is published to SRS for their own ordinary live.
 * An active PK or an accepted/muted co-host seat keeps the existing LiveKit publish path,
 * because the device camera cannot be opened by both capturers at once.
 */
internal fun hostPublishesOrdinarySrs(isHost: Boolean, matchId: Long?, acceptedCohost: Boolean): Boolean =
    isHost && matchId == null && !acceptedCohost

/** Seats that already share the host's live A/V. Pending requests do not take the camera. */
internal fun cohostSeatNeedsLiveKit(status: String): Boolean = status == "ACCEPTED" || status == "MUTED"
