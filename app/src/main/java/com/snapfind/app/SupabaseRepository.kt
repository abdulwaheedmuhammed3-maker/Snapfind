package com.snapfind.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
        val safeQuery = query.trim()
            .replace("*", "")
            .replace(",", " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace("%", "")
            .take(40)

        val filters = mutableListOf(
            "select=id,display_name,snapchat_username,country,interests,bio",
            "order=created_at.desc",
            "limit=100"
        )
        if (country.isNotBlank() && !country.equals("All", ignoreCase = true)) {
            filters += "country=eq.${encode(country.trim())}"
        }
        if (safeQuery.isNotBlank()) {
            val pattern = "*${safeQuery}*"
            filters += "or=(display_name.ilike.${encode(pattern)},snapchat_username.ilike.${encode(pattern)},country.ilike.${encode(pattern)},bio.ilike.${encode(pattern)})"
        }

        val response = request(
            "GET",
            "/rest/v1/public_profiles?${filters.joinToString("&")}",
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
            .put("is_public", true)

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
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer ${session?.accessToken ?: anonKey}")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (prefer != null) setRequestProperty("Prefer", prefer)
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            if (status !in 200..299) {
                val message = try {
                    val error = JSONObject(responseText)
                    error.optString(
                        "msg",
                        error.optString(
                            "message",
                            error.optString("error_description", error.optString("error", "Request failed ($status)."))
                        )
                    )
                } catch (_: Exception) {
                    "Request failed ($status)."
                }
                throw IllegalStateException(message)
            }

            if (responseText.isBlank()) return JSONObject()
            return try {
                JSONObject().put("data", JSONArray(responseText))
            } catch (_: Exception) {
                JSONObject(responseText)
            }
        } finally {
            connection.disconnect()
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
