package sensevox.asr.voiceinputmethod

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class SherpaAsrResponse(
    val status: String,
    val result: String?,
    val message: String?
)

class ASRClient {

    private val client = OkHttpClient()
    private val gson = Gson()

    fun recognize(url: String, audioWavData: ByteArray, callback: (String) -> Unit) {
        if (url.isBlank()) {
            callback("[错误: 未设置URL]")
            return
        }

        // 创建请求体，内容是WAV文件的二进制数据
        val requestBody = audioWavData.toRequestBody("audio/wav".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        // 异步执行网络请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback("[网络错误]")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        // 使用Gson解析返回的JSON数据
                        val asrResponse = gson.fromJson(body, SherpaAsrResponse::class.java)
                        if (asrResponse.status == "success") {
                            callback(asrResponse.result ?: "")
                        } else {
                            callback("[错误: ${asrResponse.message}]")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback("[解析错误]")
                    }
                } else {
                    callback("[服务错误: ${response.code}]")
                }
            }
        })
    }
}
