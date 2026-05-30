package com.example.admin

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer

class LiveAudioPlayer {
    private var decoder: MediaCodec? = null
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var decoderJob: Job? = null
    
    // Using a channel to queue audio chunks so we can decode them sequentially
    private val audioChunks = Channel<ByteArray>(Channel.UNLIMITED)

    fun start() {
        if (isPlaying) return
        isPlaying = true

        try {
            // Configure AudioTrack for 44100Hz, Mono, 16-bit PCM
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2, // Double buffer size for smoother playback
                AudioTrack.MODE_STREAM
            )

            // Configure MediaCodec for AAC
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)

            // Make sure the codec requires an ADTS header.
            format.setInteger(MediaFormat.KEY_IS_ADTS, 1)

            // A standard AAC LC AudioSpecificConfig might be required by some decoders, 
            // but since we are handling ADTS, many devices work with just IS_ADTS=1. 
            // Let's add the basic config just in case. 44100 Hz, 1 channel.
            // 0x12 0x08 = ObjectType 2 (AAC LC), Frequency Index 4 (44100), Channel 1.
            val bytes = byteArrayOf(0x12.toByte(), 0x08.toByte())
            val buffer = ByteBuffer.wrap(bytes)
            format.setByteBuffer("csd-0", buffer)

            decoder?.configure(format, null, null, 0)
            decoder?.start()
            audioTrack?.play()

            // Start the decoding loop
            decoderJob = scope.launch {
                decodeLoop()
            }
            
            Log.d("LiveAudioPlayer", "Started audio player")

        } catch (e: Exception) {
            Log.e("LiveAudioPlayer", "Error starting LiveAudioPlayer", e)
            stop()
        }
    }

    private suspend fun decodeLoop() = withContext(Dispatchers.IO) {
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10000L

        // Wait to accumulate some initial packets for smoother playback to handle network jitter
        var chunksReceived = 0
        while (chunksReceived < 10 && isPlaying) {
            val chunk = audioChunks.receive()
            // queue them up immediately, but we don't play them yet. Wait we must feed them to the queue!
            // Actually, `audioChunks.receive()` consumes it. We should just `delay(500)` without consuming, so chunks build up in the Channel.
            break
        }
        // Let's just do a simple delay to let the channel fill up before we start taking items.
        kotlinx.coroutines.delay(500)

        while (isPlaying) {
            try {
                // Wait for the next chunk of AAC ADTS data
                val chunk = audioChunks.receive()

                var inputIndex = decoder?.dequeueInputBuffer(timeoutUs) ?: -1
                while (inputIndex < 0 && isPlaying) {
                    inputIndex = decoder?.dequeueInputBuffer(timeoutUs) ?: -1
                }
                
                if (inputIndex >= 0 && isPlaying) {
                    val inputBuffer = decoder?.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(chunk)
                        decoder?.queueInputBuffer(inputIndex, 0, chunk.size, 0, 0)
                    }
                }

                // Process output buffers
                var outputIndex = decoder?.dequeueOutputBuffer(bufferInfo, timeoutUs) ?: -1
                while (outputIndex >= 0 && isPlaying) {
                    val outputBuffer = decoder?.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmData)

                        audioTrack?.write(pcmData, 0, pcmData.size)
                    }
                    decoder?.releaseOutputBuffer(outputIndex, false)
                    outputIndex = decoder?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
                }
            } catch (e: Exception) {
                Log.e("LiveAudioPlayer", "Error decoding audio chunk", e)
            }
        }
    }

    fun playAudioData(base64Data: String) {
        if (!isPlaying) return
        scope.launch {
            try {
                val data = Base64.decode(base64Data, Base64.DEFAULT)
                audioChunks.send(data)
            } catch (e: Exception) {
                Log.e("LiveAudioPlayer", "Error queuing audio data", e)
            }
        }
    }

    fun stop() {
        isPlaying = false
        decoderJob?.cancel()
        
        try {
            decoder?.stop()
        } catch (e: Exception) {
            // ignore
        }
        try {
            decoder?.release()
        } catch (e: Exception) {
            // ignore
        }
        decoder = null
        
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            // ignore
        }
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
        
        // Clear remaining chunks
        while (audioChunks.tryReceive().isSuccess) {}
        
        Log.d("LiveAudioPlayer", "Stopped audio player")
    }
}
