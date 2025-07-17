package sensevox.asr.voiceinputmethod

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.concurrent.thread

@SuppressLint("MissingPermission")
class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private lateinit var recordingThread: Thread
    private val outputStream = ByteArrayOutputStream()

    // --- WAV文件参数 ---
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bitsPerSample: Short = 16

    fun startRecording() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // 在输入法服务中，我们假设权限已经被授予
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true
        outputStream.reset() // 清空上次的录音数据

        recordingThread = thread {
            val data = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    outputStream.write(data, 0, read)
                }
            }
        }
    }

    fun stopRecording(): ByteArray? {
        if (!isRecording) return null
        isRecording = false

        // 等待录音线程结束
        recordingThread.join()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmData = outputStream.toByteArray()
        return if (pcmData.isEmpty()) null else addWavHeader(pcmData)
    }

    /**
     * 为原始PCM数据添加WAV文件头
     */
    @Throws(IOException::class)
    private fun addWavHeader(pcmData: ByteArray): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val channels: Short = 1 // 单声道
        val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()

        // 'fmt ' chunk
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Sub-chunk size (16 for PCM)
        header[20] = 1; header[21] = 0 // Audio format (1 for PCM)
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte(); header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0 // Block align
        header[34] = bitsPerSample.toByte(); header[35] = 0 // Bits per sample

        // 'data' sub-chunk
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte(); header[41] = (pcmData.size shr 8 and 0xff).toByte()
        header[42] = (pcmData.size shr 16 and 0xff).toByte(); header[43] = (pcmData.size shr 24 and 0xff).toByte()

        // 合并文件头和PCM数据
        val wavData = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, wavData, 0, header.size)
        System.arraycopy(pcmData, 0, wavData, header.size, pcmData.size)
        return wavData
    }
}
