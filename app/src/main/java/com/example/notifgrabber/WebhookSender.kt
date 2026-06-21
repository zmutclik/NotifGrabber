package com.example.notifgrabber

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object WebhookSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Kirim payload ke webhook.
     * @param onResult callback opsional: (success: Boolean, info: String) — dipanggil di thread OkHttp.
     *                 success=true jika HTTP 2xx, false jika error jaringan atau non-2xx.
     */
    fun send(url: String, payload: JSONObject, onResult: ((Boolean, String) -> Unit)? = null) {
        val body = payload.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult?.invoke(false, e.message ?: "network error")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val code = response.code
                response.close()
                onResult?.invoke(code in 200..299, "HTTP $code")
            }
        })
    }
}
