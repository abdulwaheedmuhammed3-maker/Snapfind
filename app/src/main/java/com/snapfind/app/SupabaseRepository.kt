package com.snapfind.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtMillis: Long
)

data class SupabaseAuthResult(
    val session: SupabaseSession?,
    val confirmationRequired: Boolean
)

class SupabaseRepository(context: Context) {
    private val sessionStore = SecureSessionStore(context)

    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") &&
            anonKey.isNotBlank() &&
            !anonKey.contains("YOUR_")

    fun savedSession(): SupabaseSession? = sessionStore.read()

    fun signUp(email: String, password: String): SupabaseAuthResult {
        requireConfigured()
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
        val response = request("POST", "/auth/v1/signup", body = body, session = null)
        val session = sessionFrom(response)
        if (session != null) saveSession(session)
        return SupabaseAuthResult(session, session == null)
    }

    fun signIn(email: String, password: String): SupabaseSession {
        requireConfigured()
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)
        val response = request(
            "POST",
            "/auth/v1/token?grant_type=password",
            body = body,
            session = null
        )
        val session = sessionFrom(response)
            ?: throw IllegalStateException("The sign-in response did not contain a session.")
        saveSession(session)
        return session
    }

    fun signOut() {
        val session = validSession()
        if (session != null) {
            try {
                request("POST", "/auth/v1/logout", body = JSONObject(), session = session)
            } finally {
                clearSession()
            }
        } else {
            clearSession()
        }
    }

    @Synchronized
    fun validSession(): SupabaseSession? {
        val session = savedSession() ?: return null
        if (session.expiresAtMillis > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)) {
            return session
        }

        return try {
            val response = request(
                "POST",
                "/auth/v1/token?grant_type=refresh_token",
                body = JSONObject().put("refresh_token", session.refreshToken),
                session = null
            )
            val refreshed = sessionFrom(response)
                ?: throw IllegalStateException("Could not refresh the sign-in session.")
            saveSession(refreshed)
            refreshed
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    fun fetchPublicProfiles(query: String = "", country: String = ""): List<SnapProfile> {
        val session = validSession()
        val body = JSONObject()
            .put("search_query", query.trim().removePrefix("@").take(40))
            .put("country_filter", country.trim().take(60))
            .put("max_results", 100)

        val response = request(
            "POST",
            "/rest/v1/rpc/search_public_profiles",
            body = body,
            session = session
        )
        val array = response.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toSnapProfile())
            }
        }
    }

    fun fetchOwnProfile(userId: String): SnapProfile? {
        val session = requireSession()
        val response = request(
            "GET",
            "/rest/v1/profiles?select=id,display_name,snapchat_username,country,interests,bio,is_public&id=eq.${encode(userId)}&limit=1",
            session = session
        )
        val array = response.optJSONArray("data") ?: JSONArray()
        if (array.length() == 0) return null
        val item = array.optJSONObject(0) ?: return null
        return item.toSnapProfile().copy(isPublished = item.optBoolean("is_public", false))
    }

    fun saveProfile(profile: SnapProfile, userId: String) {
        val session = requireSession()
        val interests = JSONArray(
            profile.interests.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        )
        val body = JSONObject()
            .put("id", userId)
            .put("display_name", profile.displayName.trim())
            .put("snapchat_username", profile.username.trim().removePrefix("@"))
            .put("country", profile.country.trim())
            .put("interests", interests)
            .put("bio", profile.bio.trim())
            .put("is_public", profile.isPublished)

        request(
            "POST",
            "/rest/v1/profiles?on_conflict=id",
            body = body,
            session = session,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    fun setProfileVisibility(userId: String, visible: Boolean) {
        val session = requireSession()
        request(
            "PATCH",
            "/rest/v1/profiles?id=eq.${encode(userId)}",
            body = JSONObject().put("is_public", visible),
            session = session,
            prefer = "return=minimal"
        )
    }

    fun deleteOwnProfile(userId: String) {
        val session = requireSession()
        request(
            "DELETE",
            "/rest/v1/profiles?id=eq.${encode(userId)}",
            session = session,
            prefer = "return=minimal"
        )
    }

    fun reportProfile(profileId: String, reason: String, details: String = "") {
        val session = requireSession()
        val reporterId = session.userId
        require(reporterId != profileId) { "You cannot report your own listing." }
        val body = JSONObject()
            .put("reporter_id", reporterId)
            .put("reported_profile_id", profileId)
            .put("reason", reason)
            .put("details", details.take(500))
            .put("status", "open")
        request(
            "POST",
            "/rest/v1/reports",
            body = body,
            session = session,
            prefer = "return=minimal"
        )
    }

    fun blockProfile(profileId: String) {
        val session = requireSession()
        request(
            "POST",
            "/rest/v1/blocks",
            body = JSONObject()
                .put("blocker_id", session.userId)
                .put("blocked_profile_id", profileId),
            session = session,
            prefer = "return=minimal"
        )
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        session: SupabaseSession?,
        prefer: String? = null
    ): JSONObject {
        requireConfigured()
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session?.accessToken ?: anonKey}")
            .header("Accept", "application/json")

        if (prefer != null) builder.header("Prefer", prefer)
        val requestBody = body?.toString()?.toRequestBody(jsonMediaType)

        val request = when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post(requestBody ?: "".toRequestBody(jsonMediaType)).build()
            "PATCH" -> builder.patch(
                requestBody ?: throw IllegalArgumentException("PATCH requests require a JSON body.")
            ).build()
            "DELETE" -> builder.delete().build()
            else -> builder.method(method, requestBody).build()
        }

        httpClient.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    val error = JSONObject(responseText)
                    error.optString(
                        "msg",
                        error.optString(
                            "message",
                            error.optString("error_description", error.optString("error", "Request failed (${response.code})."))
                        )
                    )
                } catch (_: Exception) {
                    "Request failed (${response.code})."
                }
                throw IllegalStateException(message)
            }

            if (responseText.isBlank()) return JSONObject()
            return try {
                JSONObject().put("data", JSONArray(responseText))
            } catch (_: Exception) {
                JSONObject(responseText)
            }
        }
    }

    private fun sessionFrom(json: JSONObject): SupabaseSession? {
        val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val refresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: return null
        val user = json.optJSONObject("user") ?: return null
        val userId = user.optString("id").takeIf { it.isNotBlank() } ?: return null
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = json.optLong("expires_at", 0L).let {
            if (it > 0) it * 1000L else System.currentTimeMillis() + expiresIn * 1000L
        }
        return SupabaseSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = user.optString("email"),
            expiresAtMillis = expiresAt
        )
    }

    private fun saveSession(session: SupabaseSession) {
        sessionStore.save(session)
    }

    private fun clearSession() {
        sessionStore.clear()
    }

    private fun requireSession(): SupabaseSession =
        validSession() ?: throw IllegalStateException("Please sign in again to continue.")

    private fun requireConfigured() {
        check(isConfigured) {
            "Supabase is not configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY to the local build configuration."
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun JSONObject.toSnapProfile(): SnapProfile {
        val interestArray = optJSONArray("interests") ?: JSONArray()
        val interests = (0 until interestArray.length())
            .mapNotNull { interestArray.optString(it).takeIf(String::isNotBlank) }
            .joinToString(", ")
        return SnapProfile(
            id = optString("id"),
            displayName = optString("display_name"),
            username = optString("snapchat_username"),
            country = optString("country"),
            interests = interests,
            bio = optString("bio"),
            isPreview = false
        )
    }
}
