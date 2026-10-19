package com.snuggle.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.PlaybackException
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.snuggle.music.constants.AudioQuality
import com.snuggle.music.constants.PlaybackEngine
import com.snuggle.music.utils.cipher.CipherDeobfuscator
import com.snuggle.music.utils.potoken.PoTokenGenerator
import com.snuggle.music.utils.potoken.PoTokenResult
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"
    private const val YT_RESOLVE_TAG = "YT Resolve"

    @Volatile
    var playbackEngine: PlaybackEngine = PlaybackEngine.AUTO

    @Volatile
    var forceOpusEnabled: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val poTokenGenerator = PoTokenGenerator()

    private const val VALIDATION_CHUNK_LENGTH = 512 * 1024L

    private fun describeStreamUrl(url: String): String =
        try {
            val uri = Uri.parse(url)
            val expire = uri.getQueryParameter("expire")?.toLongOrNull()
            val nowSec = System.currentTimeMillis() / 1000
            buildString {
                append("host=").append(uri.host ?: "?")
                append(" itag=").append(uri.getQueryParameter("itag") ?: "-")
                append(" mime=").append(uri.getQueryParameter("mime") ?: "-")
                append(" c=").append(uri.getQueryParameter("c") ?: "-")
                append(" expire=").append(expire ?: "-")
                if (expire != null) append("(in ").append(expire - nowSec).append("s)")
                append(" hasPot=").append(uri.getQueryParameter("pot") != null)
                append(" nLen=").append(uri.getQueryParameter("n")?.length ?: -1)
                append(" cpn=").append(uri.getQueryParameter("cpn") ?: "-")
                append(" lmt=").append(uri.getQueryParameter("lmt") ?: "-")
                append(" sabr=").append(uri.getQueryParameter("sabr") ?: "-")
                append(" clen=").append(uri.getQueryParameter("clen") ?: "-")
            }
        } catch (e: Exception) {
            "unparseable url (${e.javaClass.simpleName})"
        }

    private fun describeResponse(client: YouTubeClient, response: PlayerResponse?): String =
        try {
            if (response == null) {
                Fix403.kv("client" to client.clientName, "response" to "NULL(requestFailed)")
            } else {
                val adaptive = response.streamingData?.adaptiveFormats.orEmpty()
                val audio = adaptive.filter { it.isAudio }
                Fix403.kv(
                    "client" to client.clientName,
                    "clientVersion" to client.clientVersion,
                    "status" to response.playabilityStatus.status,
                    "reason" to response.playabilityStatus.reason,
                    "hasStreamingData" to (response.streamingData != null),
                    "expiresInSeconds" to response.streamingData?.expiresInSeconds,
                    "adaptiveFormats" to adaptive.size,
                    "audioFormats" to audio.size,
                    "urls" to adaptive.count { !it.url.isNullOrEmpty() },
                    "ciphers" to adaptive.count { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() },
                    "bare" to adaptive.count {
                        it.url.isNullOrEmpty() && it.signatureCipher.isNullOrEmpty() && it.cipher.isNullOrEmpty()
                    },
                    "audioItags" to audio.joinToString("/") { it.itag.toString() }.ifEmpty { "-" },
                    "musicVideoType" to response.videoDetails?.musicVideoType,
                    "title" to response.videoDetails?.title,
                )
            }
        } catch (e: Exception) {
            "describeResponse failed (${e.javaClass.simpleName}: ${e.message})"
        }

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        ANDROID_VR_1_65_10,
        TVHTML5,
        ANDROID_VR_1_43_32,
        IPADOS,
        IOS,
        WEB_CREATOR
    )

    private val NORMAL_CONTENT_STREAM_START_INDEX: Int = 0

    private val PRIVATE_TRACK_STREAM_START_INDEX: Int =
        STREAM_FALLBACK_CLIENTS.indexOf(TVHTML5).takeIf { it >= 0 } ?: 0

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        val fx = Fix403.nextId("res")
        
        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Video: $videoId")
        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Main client: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "[YT Resolve] Video: $videoId", "Main client: ${MAIN_CLIENT.clientName}")

        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true
        val isLoggedIn = YouTube.cookie != null

        Fix403.i(
            fx, "resolve.begin",
            Fix403.kv(
                "videoId" to videoId,
                "playlistId" to playlistId,
                "quality" to audioQuality,
                "uploadedTrack" to isUploadedTrack,
                "loggedIn" to isLoggedIn,
                "thread" to Thread.currentThread().name,
            ),
        )

        Fix403.i(
            fx, "resolve.session",
            Fix403.kv(
                "cookie" to Fix403.redact(YouTube.cookie),
                "visitorData" to Fix403.redact(YouTube.visitorData),
                "dataSyncId" to Fix403.redact(YouTube.dataSyncId),
                "proxy" to (YouTube.proxy?.toString() ?: "none"),
                "locale" to "${YouTube.locale.hl}/${YouTube.locale.gl}",
            ),
        )

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Fix403.i(
            fx, "resolve.sts",
            Fix403.kv("sts" to signatureTimestamp.timestamp, "source" to "NewPipeExtractor"),
        )

        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        val mainClientNeedsPoToken = MAIN_CLIENT.useWebPoTokens
        Fix403.i(
            fx, "potoken.decide",
            Fix403.kv(
                "mainClientNeedsPoToken" to mainClientNeedsPoToken,
                "sessionIdSource" to if (isLoggedIn) "dataSyncId" else "visitorData",
                "sessionId" to Fix403.redact(sessionId),
                "sessionIdEmpty" to (sessionId != null && sessionId.isEmpty()),
            ),
        )
        if (mainClientNeedsPoToken && !sessionId.isNullOrEmpty()) {
            try {
                poToken = Fix403.timed(fx, "potoken.generate") {
                    poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                }
                Fix403.i(
                    fx, "potoken.result",
                    Fix403.kv(
                        "obtained" to (poToken != null),
                        "playerRequestPoToken" to Fix403.redact(poToken?.playerRequestPoToken),
                        "streamingDataPoToken" to Fix403.redact(poToken?.streamingDataPoToken),
                    ),
                )
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "PoToken generation failed: ${e.message}")
                Fix403.fail(fx, "potoken.generate.failed", e)
            }
        }

        var mainPlayerResponse: PlayerResponse? = null
        try {
            mainPlayerResponse = YouTube.player(
                videoId,
                playlistId,
                MAIN_CLIENT,
                signatureTimestamp.timestamp,
                poToken?.playerRequestPoToken
            ).getOrNull()
            Fix403.i(fx, "mainClient.response", describeResponse(MAIN_CLIENT, mainPlayerResponse))
        } catch (e: Exception) {
            Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] Main client (${MAIN_CLIENT.clientName}) fetch failed: ${e.message}")
            Fix403.w(fx, "mainClient.failed", Fix403.kv("error" to (e.message ?: "Unknown")))
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val mainStatus = mainPlayerResponse?.playabilityStatus?.status
        val wasOriginallyAgeRestricted = mainStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")

        if (wasOriginallyAgeRestricted && isLoggedIn) {
            Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Age-restricted detected, trying WEB_CREATOR")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] WEB_CREATOR authenticated player response OK")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        var audioConfig = mainPlayerResponse?.playerConfig?.audioConfig
        var videoDetails = mainPlayerResponse?.videoDetails
        var playbackTracking = mainPlayerResponse?.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        val isPrivateTrack = mainPlayerResponse?.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        val startIndex = when {
            isPrivateTrack -> PRIVATE_TRACK_STREAM_START_INDEX
            wasOriginallyAgeRestricted -> 0
            mainPlayerResponse == null -> 0
            else -> NORMAL_CONTENT_STREAM_START_INDEX
        }

        val cascade = mutableListOf<String>()
        fun logCascade(outcome: String) = Fix403.i(
            fx, "cascade.$outcome",
            Fix403.kv("videoId" to videoId, "tried" to cascade.size) + " :: " + cascade.joinToString(" | "),
        )

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Stream client attempt: ${client.clientName}")
            } else {
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Stream client attempt: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] ${client.clientName} failed: LOGIN_REQUIRED (user not logged in)")
                    cascade += "${client.clientName}=SKIP(loginRequired)"
                    Fix403.w(fx, "client.skip", Fix403.kv("client" to client.clientName, "reason" to "loginRequiredButAnonymous"))
                    if (clientIndex + 1 < STREAM_FALLBACK_CLIENTS.size) {
                        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Trying ${STREAM_FALLBACK_CLIENTS[clientIndex + 1].clientName}")
                    }
                    continue
                }

                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                Fix403.i(
                    fx, "client.request",
                    Fix403.kv(
                        "idx" to "${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}",
                        "client" to client.clientName,
                        "clientVersion" to client.clientVersion,
                        "loginSupported" to client.loginSupported,
                        "useWebPoTokens" to client.useWebPoTokens,
                        "sts" to clientSigTimestamp,
                        "poToken" to Fix403.redact(clientPoToken),
                    ),
                )
                streamPlayerResponse = Fix403.trap(fx, "client.request.${client.clientName}") {
                    Fix403.timed(fx, "client.http.${client.clientName}") {
                        YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken)
                            .onFailure { Fix403.fail(fx, "client.player.failed.${client.clientName}", it) }
                            .getOrNull()
                    }
                }
                Fix403.i(fx, "client.response", describeResponse(client, streamPlayerResponse))
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Player response: OK (${client.clientName})")

                if (audioConfig == null) audioConfig = streamPlayerResponse.playerConfig?.audioConfig
                if (videoDetails == null) videoDetails = streamPlayerResponse.videoDetails
                if (playbackTracking == null) playbackTracking = streamPlayerResponse.playbackTracking

                val responseToUse = if (wasOriginallyAgeRestricted) {
                    streamPlayerResponse
                } else {
                    val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                    newPipeResponse ?: streamPlayerResponse
                }

                val adaptiveFormats = responseToUse.streamingData?.adaptiveFormats.orEmpty()
                val audioFormats = adaptiveFormats.filter { it.isAudio }
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Adaptive formats: ${adaptiveFormats.size}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Audio formats: ${audioFormats.size}")

                format = findFormat(
                    responseToUse,
                    audioQuality,
                    connectivityManager,
                )

                if (format == null) {
                    Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] ${client.clientName} failed: No suitable audio format")
                    cascade += "${client.clientName}=NO_FORMAT"
                    Fix403.w(
                        fx, "client.noFormat",
                        Fix403.kv("client" to client.clientName, "quality" to audioQuality) + " " +
                            describeResponse(client, responseToUse),
                    )
                    if (clientIndex + 1 < STREAM_FALLBACK_CLIENTS.size) {
                        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Trying ${STREAM_FALLBACK_CLIENTS[clientIndex + 1].clientName}")
                    }
                    continue
                }

                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Selected itag: ${format.itag}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] MIME: ${format.mimeType}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Bitrate: ${format.bitrate}")

                val urlSource = when {
                    !format.url.isNullOrEmpty() -> "FORMAT_URL"
                    !format.signatureCipher.isNullOrEmpty() || !format.cipher.isNullOrEmpty() -> "SIG_CIPHER"
                    else -> "NEWPIPE_OR_NONE"
                }
                streamUrl = Fix403.trap(fx, "findUrl.${client.clientName}") {
                    findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                }
                Fix403.i(
                    fx, "client.url",
                    Fix403.kv(
                        "client" to client.clientName,
                        "itag" to format.itag,
                        "mime" to format.mimeType,
                        "bitrate" to format.bitrate,
                        "urlSource" to urlSource,
                        "resolved" to (streamUrl != null),
                    ) + if (streamUrl != null) " " + describeStreamUrl(streamUrl!!) else "",
                )
                if (streamUrl == null) {
                    Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] ${client.clientName} failed: Stream URL resolution failed ($urlSource)")
                    cascade += "${client.clientName}=NO_URL($urlSource)"
                    Fix403.w(fx, "client.noUrl", Fix403.kv("client" to client.clientName, "urlSource" to urlSource))
                    if (clientIndex + 1 < STREAM_FALLBACK_CLIENTS.size) {
                        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Trying ${STREAM_FALLBACK_CLIENTS[clientIndex + 1].clientName}")
                    }
                    continue
                }

                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                val needsNTransform = currentClient.useWebPoTokens ||
                    currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5") ||
                    isPrivatelyOwnedTrack

                val needsPoToken = (currentClient.useWebPoTokens || isPrivatelyOwnedTrack) && poToken?.streamingDataPoToken != null

                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Direct URL: ${if (!format.url.isNullOrEmpty()) "YES" else "NO"}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Signature processed: ${if (format.url.isNullOrEmpty() && (!format.signatureCipher.isNullOrEmpty() || !format.cipher.isNullOrEmpty())) "YES" else "NO"}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] N processed: ${if (needsNTransform) "YES" else "NO"}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] PoToken: ${if (needsPoToken) "ATTACHED" else if (currentClient.useWebPoTokens) "MISSING" else "NOT_REQUIRED"}")
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] SABR: ${if (format.url.isNullOrEmpty() && format.signatureCipher.isNullOrEmpty() && format.cipher.isNullOrEmpty()) "YES" else "NO"}")

                if (needsNTransform) {
                    try {
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl!!)
                        if (needsPoToken) {
                            val separator = if ("?" in streamUrl!!) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${Uri.encode(poToken!!.streamingDataPoToken)}"
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "N-transform or pot append failed: ${e.message}")
                    }
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] ${client.clientName} failed: Missing expiresInSeconds")
                    cascade += "${client.clientName}=NO_EXPIRE"
                    Fix403.w(
                        fx, "client.noExpire",
                        Fix403.kv("client" to client.clientName, "hasStreamingData" to (streamPlayerResponse.streamingData != null)),
                    )
                    if (clientIndex + 1 < STREAM_FALLBACK_CLIENTS.size) {
                        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Trying ${STREAM_FALLBACK_CLIENTS[clientIndex + 1].clientName}")
                    }
                    continue
                }

                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1 || isPrivatelyOwned) {
                    Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Playback URL accepted: ${currentClient.clientName} (itag ${format.itag}, unvalidated fallback)")
                    PlaybackLogManager.log(PlaybackLogLevel.INFO, "[YT Resolve] Playback URL accepted", "${currentClient.clientName} (itag ${format.itag})")
                    cascade += "${currentClient.clientName}=ACCEPTED(unvalidated)"
                    Fix403.i(
                        fx, "client.accepted",
                        Fix403.kv(
                            "client" to currentClient.clientName,
                            "validated" to false,
                            "why" to if (isPrivatelyOwned) "privatelyOwnedTrack" else "lastFallbackClient",
                            "expiresInSeconds" to streamExpiresInSeconds,
                        ) + " " + describeStreamUrl(streamUrl!!),
                    )
                    logCascade("resolved")
                    break
                }

                val clen = format.contentLength
                val validationResult = validateStatusWithCodes(streamUrl!!, clen)
                Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Range probe 0: ${validationResult.code0}")
                if (validationResult.codeLast != null) {
                    Timber.tag(YT_RESOLVE_TAG).i("[YT Stream] Range probe last-byte: ${validationResult.codeLast}")
                }

                if (validationResult.isAccepted) {
                    Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Playback URL accepted: ${currentClient.clientName} (itag ${format.itag})")
                    PlaybackLogManager.log(PlaybackLogLevel.INFO, "[YT Resolve] Playback URL accepted", "${currentClient.clientName} (itag ${format.itag})")
                    cascade += "${currentClient.clientName}=ACCEPTED"
                    Fix403.i(
                        fx, "client.accepted",
                        Fix403.kv(
                            "client" to currentClient.clientName,
                            "validated" to true,
                            "expiresInSeconds" to streamExpiresInSeconds,
                        ) + " " + describeStreamUrl(streamUrl!!),
                    )
                    logCascade("resolved")
                    break
                } else {
                    Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] ${currentClient.clientName} failed: Stream URL range validation rejected")
                    cascade += "${currentClient.clientName}=REJECTED(validate)"
                    if (clientIndex + 1 < STREAM_FALLBACK_CLIENTS.size) {
                        Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Trying ${STREAM_FALLBACK_CLIENTS[clientIndex + 1].clientName}")
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "NULL"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "Unknown"
                Timber.tag(YT_RESOLVE_TAG).w("[YT Resolve] ${client.clientName} failed: $status - $reason")
                cascade += "${client.clientName}=NOT_OK($status)"
                Fix403.w(
                    fx, "client.notOk",
                    Fix403.kv(
                        "client" to client.clientName,
                        "status" to status,
                        "reason" to reason,
                    ),
                )
                if (clientIndex + 1 < STREAM_FALLBACK_CLIENTS.size) {
                    Timber.tag(YT_RESOLVE_TAG).i("[YT Resolve] Trying ${STREAM_FALLBACK_CLIENTS[clientIndex + 1].clientName}")
                }
            }
        }

        if (streamPlayerResponse == null) {
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "badStreamPlayerResponse"))
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason ?: "Playability status not OK"
            logCascade("exhausted")
            Fix403.e(
                fx, "resolve.failed",
                Fix403.kv(
                    "videoId" to videoId,
                    "why" to "playabilityNotOk",
                    "status" to streamPlayerResponse.playabilityStatus.status,
                    "reason" to errorReason,
                ),
            )
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "missingExpireTime"))
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "noFormat"))
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            logCascade("exhausted")
            Fix403.e(fx, "resolve.failed", Fix403.kv("videoId" to videoId, "why" to "noStreamUrl"))
            throw Exception("Could not find stream url")
        }

        Fix403.i(
            fx, "resolve.success",
            Fix403.kv("videoId" to videoId, "itag" to format.itag, "expiresInSeconds" to streamExpiresInSeconds) +
                " " + describeStreamUrl(streamUrl!!),
        )

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        e.printStackTrace()
        Fix403.fail(
            Fix403.nextId("resolve-fail"), "resolve.exception", e,
            Fix403.kv("videoId" to videoId, "playlistId" to playlistId),
        )
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?: emptyList()

        if (audioFormats.isEmpty()) return null

        var candidateFormats = audioFormats
        if (forceOpusEnabled) {
            val opusFormats = candidateFormats.filter { it.mimeType.contains("audio/webm") }
            if (opusFormats.isNotEmpty()) {
                candidateFormats = opusFormats
                Timber.tag(logTag).d("Forcing Opus: Filtered down to ${opusFormats.size} webm formats")
            }
        }

        // Prioritize itag 251 (Opus 160kbps), then highest bitrate webm/audio
        val itag251Format = candidateFormats.find { it.itag == 251 }
        if (itag251Format != null) {
            return itag251Format
        }

        return candidateFormats
            .filter { it.mimeType.startsWith("audio/webm") }
            .maxByOrNull { it.bitrate }
            ?: candidateFormats.maxByOrNull { it.bitrate }
    }

    data class ValidationResult(
        val isAccepted: Boolean,
        val code0: Int,
        val codeLast: Int?
    )

    private fun validateStatusWithCodes(url: String, contentLength: Long? = null): ValidationResult {
        try {
            val req0 = okhttp3.Request.Builder()
                .head()
                .url(url)
                .addHeader("Range", "bytes=0-${VALIDATION_CHUNK_LENGTH - 1}")
            YouTube.cookie?.let { req0.addHeader("Cookie", it) }

            val res0 = httpClient.newCall(req0.build()).execute()
            val code0 = res0.code
            val is0Accepted = res0.isSuccessful || code0 == 405
            res0.close()

            if (!is0Accepted) {
                return ValidationResult(isAccepted = false, code0 = code0, codeLast = null)
            }

            if (contentLength != null && contentLength > 0) {
                val reqLast = okhttp3.Request.Builder()
                    .head()
                    .url(url)
                    .addHeader("Range", "bytes=${contentLength - 1}-${contentLength - 1}")
                YouTube.cookie?.let { reqLast.addHeader("Cookie", it) }

                val resLast = httpClient.newCall(reqLast.build()).execute()
                val codeLast = resLast.code
                val isLastAccepted = resLast.isSuccessful || codeLast == 405
                resLast.close()

                return ValidationResult(isAccepted = isLastAccepted, code0 = code0, codeLast = codeLast)
            }

            return ValidationResult(isAccepted = true, code0 = code0, codeLast = null)
        } catch (e: java.io.IOException) {
            Timber.tag(logTag).w(e, "Stream URL HEAD probe failed (IO); accepting optimistically")
            return ValidationResult(isAccepted = true, code0 = 200, codeLast = null)
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
            return ValidationResult(isAccepted = false, code0 = 500, codeLast = null)
        }
    }

    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (!isAgeRestricted) {
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        val engine = playbackEngine

        if (!format.url.isNullOrEmpty()) {
            return format.url
        }

        val useCipher = engine == PlaybackEngine.POTOKEN || engine == PlaybackEngine.AUTO
        if (useCipher) {
            val signatureCipher = format.signatureCipher ?: format.cipher
            if (!signatureCipher.isNullOrEmpty()) {
                try {
                    val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
                    if (customDeobfuscatedUrl != null) {
                        return customDeobfuscatedUrl
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).e(e, "Custom cipher deobfuscation failed")
                }
            }
        }

        val useBravePipe = engine == PlaybackEngine.BRAVEPIPE || engine == PlaybackEngine.AUTO
        if (useBravePipe) {
            if (!skipNewPipe) {
                try {
                    val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
                    if (deobfuscatedUrl != null) {
                        return deobfuscatedUrl
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).e(e, "NewPipe deobfuscation failed")
                }

                try {
                    val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
                    if (streamUrls.isNotEmpty()) {
                        val streamUrl = streamUrls.find { it.first == format.itag }?.second
                        if (streamUrl != null) {
                            return streamUrl
                        }

                        val audioStream = streamUrls.find { urlPair ->
                            playerResponse.streamingData?.adaptiveFormats?.any {
                                it.itag == urlPair.first && it.isAudio
                            } == true
                        }?.second

                        if (audioStream != null) {
                            return audioStream
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).e(e, "StreamInfo fallback failed")
                }
            }
        }

        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }
}
