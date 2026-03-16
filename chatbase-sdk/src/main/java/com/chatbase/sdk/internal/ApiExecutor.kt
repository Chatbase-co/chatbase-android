package com.chatbase.sdk.internal

import com.chatbase.sdk.exception.ApiException
import com.chatbase.sdk.exception.NetworkException
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

internal class ApiExecutor(
    private val httpClient: OkHttpClient,
    private val baseUrl: String
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val parsedBaseUrl: HttpUrl = baseUrl.toHttpUrl()

    fun buildGetRequest(path: String, queryParams: Map<String, String?> = emptyMap()): Request {
        val urlBuilder = buildUrl(path)
        queryParams.forEach { (key, value) ->
            if (value != null) urlBuilder.addQueryParameter(key, value)
        }
        return Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()
    }

    fun buildPostRequest(path: String, body: String): Request {
        return Request.Builder()
            .url(buildUrl(path).build())
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    fun buildPatchRequest(path: String, body: String): Request {
        return Request.Builder()
            .url(buildUrl(path).build())
            .patch(body.toRequestBody(jsonMediaType))
            .build()
    }

    suspend fun executeRequest(request: Request): String {
        val response: Response
        try {
            response = httpClient.newCall(request).await()
        } catch (e: NetworkException) {
            throw e
        } catch (e: IOException) {
            throw NetworkException("Network request failed: ${e.message}", e)
        }

        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw parseApiError(response.code, body)
        }

        return body
    }

    private fun buildUrl(path: String): HttpUrl.Builder {
        return parsedBaseUrl.newBuilder()
            .encodedPath(parsedBaseUrl.encodedPath + path)
    }

    companion object {
        fun parseApiError(httpStatus: Int, body: String): ApiException {
            return try {
                val json = chatbaseJson.parseToJsonElement(body).jsonObject
                val error = json["error"]?.jsonObject
                val code = error?.get("code")?.jsonPrimitive?.content ?: "UNKNOWN_ERROR"
                val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                val details = error?.get("details")?.jsonObject?.let { detailsObj ->
                    detailsObj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
                }
                ApiException(httpStatus, code, message, details)
            } catch (_: Exception) {
                ApiException(httpStatus, "UNKNOWN_ERROR", body.ifBlank { "HTTP $httpStatus" })
            }
        }
    }
}
