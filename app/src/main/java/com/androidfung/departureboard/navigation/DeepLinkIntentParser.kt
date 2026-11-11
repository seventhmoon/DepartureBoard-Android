package com.androidfung.departureboard.navigation

import android.content.Intent
import android.net.Uri

/**
 * Validated, length-bounded navigation payload parsed from an incoming [Intent] or `mindtheboard://` deep link.
 *
 * Security invariants:
 * 1. Only hierarchical `mindtheboard://station/...` URIs are inspected via `getQueryParameter`
 *    (preventing `UnsupportedOperationException` crashes on opaque URIs).
 * 2. `stationId` and `departureId` are restricted to safe transit-identifier characters (`[A-Za-z0-9_.:-]`),
 *    blocking path traversal (`..`, `/`) before reaching Retrofit `@Path` endpoints.
 * 3. External `BROWSABLE` deep links never set [autoSubmitAiQuery] to `true`, preventing untrusted web pages
 *    from draining daily AI quotas, injecting prompts, or triggering runtime location prompts without user consent.
 * 4. [untrustedStationNameHint] is used ONLY as a temporary fallback when no catalog/repository station matches,
 *    preventing `?name=...` spoofing on known transit stations.
 */
data class ParsedDeepLinkPayload(
    val stationId: String? = null,
    val trustedStationName: String? = null,
    val untrustedStationNameHint: String? = null,
    val departureId: String? = null,
    val aiQuery: String? = null,
    val autoSubmitAiQuery: Boolean = false
)

object DeepLinkIntentParser {

    const val SCHEME_MIND_THE_BOARD = "mindtheboard"
    const val HOST_STATION = "station"

    private const val MAX_ID_LENGTH = 96
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_AI_QUERY_LENGTH = 200

    private val SAFE_TRANSIT_ID_REGEX = Regex("""^[A-Za-z0-9_.:-]{1,96}$""")
    private val CONTROL_CHARS_REGEX = Regex("""[\u0000-\u001F\u007F]""")

    fun sanitizeTransitId(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_ID_LENGTH } ?: return null
        if (trimmed.contains("..") || !SAFE_TRANSIT_ID_REGEX.matches(trimmed)) return null
        return trimmed
    }

    fun sanitizeDisplayText(raw: String?, maxLength: Int): String? {
        return raw
            ?.replace(CONTROL_CHARS_REGEX, " ")
            ?.trim()
            ?.take(maxLength)
            ?.takeIf { it.isNotBlank() }
    }

    fun parse(
        intent: Intent?,
        extraStationIdKey: String,
        extraStationNameKey: String,
        extraDepartureIdKey: String,
        extraAiQueryKey: String,
        extraAutoSubmitAiKey: String
    ): ParsedDeepLinkPayload {
        if (intent == null) return ParsedDeepLinkPayload()

        val uri: Uri? = intent.data
        val isHierarchical = uri?.isHierarchical == true
        val uriScheme = uri?.scheme
        val uriHost = uri?.host
        val validatedStationUri = uri?.takeIf {
            isHierarchical &&
                uriScheme.equals(SCHEME_MIND_THE_BOARD, ignoreCase = true) &&
                uriHost.equals(HOST_STATION, ignoreCase = true)
        }

        return parseParameters(
            action = intent.action,
            hasBrowsableCategory = intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true,
            isHierarchicalUri = isHierarchical,
            uriScheme = uriScheme,
            uriHost = uriHost,
            uriLastPathSegment = validatedStationUri?.lastPathSegment,
            uriQueryName = validatedStationUri?.getQueryParameter("name"),
            uriQueryDepartureId = validatedStationUri?.getQueryParameter("departureId"),
            uriQueryAi = validatedStationUri?.getQueryParameter("aiQuery"),
            extraStationId = intent.getStringExtra(extraStationIdKey),
            extraStationName = intent.getStringExtra(extraStationNameKey),
            extraDepartureId = intent.getStringExtra(extraDepartureIdKey),
            extraAiQuery = intent.getStringExtra(extraAiQueryKey),
            extraAutoSubmitAi = intent.getBooleanExtra(extraAutoSubmitAiKey, false)
        )
    }

    internal fun parseParameters(
        action: String?,
        hasBrowsableCategory: Boolean,
        isHierarchicalUri: Boolean,
        uriScheme: String?,
        uriHost: String?,
        uriLastPathSegment: String?,
        uriQueryName: String?,
        uriQueryDepartureId: String?,
        uriQueryAi: String?,
        extraStationId: String?,
        extraStationName: String?,
        extraDepartureId: String?,
        extraAiQuery: String?,
        extraAutoSubmitAi: Boolean
    ): ParsedDeepLinkPayload {
        val isValidStationDeepLink = isHierarchicalUri &&
            uriScheme.equals(SCHEME_MIND_THE_BOARD, ignoreCase = true) &&
            uriHost.equals(HOST_STATION, ignoreCase = true)

        val isBrowsableExternalLink = hasBrowsableCategory ||
            (action == Intent.ACTION_VIEW && extraStationId == null && isValidStationDeepLink)

        val rawStationIdFromExtra = sanitizeTransitId(extraStationId)
        val rawStationIdFromUri = if (isValidStationDeepLink) sanitizeTransitId(uriLastPathSegment) else null
        val stationId = rawStationIdFromExtra ?: rawStationIdFromUri

        val trustedExtraName = if (!isBrowsableExternalLink) {
            sanitizeDisplayText(extraStationName, MAX_NAME_LENGTH)
        } else null

        val untrustedUriName = if (isValidStationDeepLink) {
            sanitizeDisplayText(uriQueryName, MAX_NAME_LENGTH)
        } else {
            sanitizeDisplayText(extraStationName, MAX_NAME_LENGTH)
        }

        val departureId = sanitizeTransitId(extraDepartureId)
            ?: if (isValidStationDeepLink) sanitizeTransitId(uriQueryDepartureId) else null

        val sanitizedExtraAi = sanitizeDisplayText(extraAiQuery, MAX_AI_QUERY_LENGTH)
        val sanitizedUriAi = if (isValidStationDeepLink) {
            sanitizeDisplayText(uriQueryAi, MAX_AI_QUERY_LENGTH)
        } else null
        val aiQuery = sanitizedExtraAi ?: sanitizedUriAi

        val autoSubmitAi = !isBrowsableExternalLink &&
            sanitizedExtraAi != null &&
            extraAutoSubmitAi

        return ParsedDeepLinkPayload(
            stationId = stationId,
            trustedStationName = trustedExtraName,
            untrustedStationNameHint = untrustedUriName,
            departureId = departureId,
            aiQuery = aiQuery,
            autoSubmitAiQuery = autoSubmitAi
        )
    }
}
