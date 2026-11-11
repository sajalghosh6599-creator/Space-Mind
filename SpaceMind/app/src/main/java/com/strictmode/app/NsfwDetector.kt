package com.strictmode.app

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/** Model ba threshold bodlate hole shudhu ei object bodlao. */
object NsfwConfig {
    const val ASSET_MODEL = "nsfw.tflite"
    const val ASSET_LABELS = "labels.txt"

    const val HARD = 0.85f   // eta pele sathe sathe block
    const val SOFT = 0.60f   // eta pele porer pore 2 bar hole block
    const val SEXY = 0.90f   // "sexy" label er jonno (2 bar hole block)

    val UNSAFE_LABELS = setOf("porn", "hentai", "nsfw", "explicit", "unsafe")
    val SEXY_LABELS = setOf("sexy")
}

class NsfwDetector(ctx: Context) : AutoCloseable {

    data class Result(val unsafe: Float, val sexy: Float, val top: String)

    private val interp: Interpreter
    private val labels: List<String>
    private val w: Int
    private val h: Int
    private val inType: DataType
    private val outType: DataType
    private val outN: Int
    private val outScale: Float
    private val outZero: Int

    init {
        val bytes = ctx.assets.open(NsfwConfig.ASSET_MODEL).use { it.readBytes() }
        val bb = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        bb.put(bytes)
        bb.rewind()
        interp = Interpreter(bb, Interpreter.Options().setNumThreads(2))

        val inT = interp.getInputTensor(0)
        val shape = inT.shape() // [1, h, w, 3]
        h = shape[1]
        w = shape[2]
        inType = inT.dataType()

        val outT = interp.getOutputTensor(0)
        outN = outT.shape().last()
        outType = outT.dataType()
        outScale = outT.quantizationParams().scale
        outZero = outT.quantizationParams().zeroPoint

        labels = try {
            ctx.assets.open(NsfwConfig.ASSET_LABELS).bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            listOf("sfw", "nsfw")
        }
        Monitor.modelInfo = "in ${w}x$h $inType | out $outN $outType | labels ${labels.joinToString(",")}"
    }

    fun score(src: Bitmap): Result {
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== src) scaled.recycle()

        val input: ByteBuffer
        if (inType == DataType.UINT8) {
            input = ByteBuffer.allocateDirect(w * h * 3).order(ByteOrder.nativeOrder())
            for (p in px) {
                input.put(((p shr 16) and 0xFF).toByte())
                input.put(((p shr 8) and 0xFF).toByte())
                input.put((p and 0xFF).toByte())
            }
        } else {
            input = ByteBuffer.allocateDirect(w * h * 3 * 4).order(ByteOrder.nativeOrder())
            val bgr = Monitor.preprocess == "bgr_mean"
            for (p in px) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (bgr) {
                    // Yahoo OpenNSFW style: BGR order, training mean subtract
                    input.putFloat(b - 104f)
                    input.putFloat(g - 117f)
                    input.putFloat(r - 123f)
                } else {
                    input.putFloat(r / 255f)
                    input.putFloat(g / 255f)
                    input.putFloat(b / 255f)
                }
            }
        }
        input.rewind()

        val probs = FloatArray(outN)
        if (outType == DataType.UINT8) {
            val out = Array(1) { ByteArray(outN) }
            interp.run(input, out)
            for (i in 0 until outN) probs[i] = ((out[0][i].toInt() and 0xFF) - outZero) * outScale
        } else {
            val out = Array(1) { FloatArray(outN) }
            interp.run(input, out)
            out[0].copyInto(probs)
        }

        // Output probability na hole (logit) softmax kore nao
        if (probs.any { it < 0f || it > 1f }) {
            val mx = probs.max()
            val e = FloatArray(outN) { exp(probs[it] - mx) }
            val sum = e.sum()
            for (i in 0 until outN) probs[i] = e[i] / sum
        }

        var unsafe = 0f
        var sexy = 0f
        var top = 0
        for (i in 0 until outN) {
            val lab = labels.getOrNull(i)?.lowercase() ?: ""
            if (lab in NsfwConfig.UNSAFE_LABELS) unsafe += probs[i]
            if (lab in NsfwConfig.SEXY_LABELS) sexy += probs[i]
            if (probs[i] > probs[top]) top = i
        }
        return Result(unsafe, sexy, labels.getOrNull(top) ?: "?")
    }

    override fun close() {
        interp.close()
    }
}
