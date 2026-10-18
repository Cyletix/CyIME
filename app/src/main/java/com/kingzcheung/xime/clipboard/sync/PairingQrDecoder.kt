package com.kingzcheung.xime.clipboard.sync

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader

/** Decode QR first, then validate the pairing protocol. Never silently treat a foreign QR as no QR. */
object PairingQrDecoder {
    sealed interface Result {
        data class PairingCode(val text: String) : Result
        data object OtherCode : Result
        data object InvalidPairingCode : Result
    }

    fun decode(source: LuminanceSource): String? = (scan(source) as? Result.PairingCode)?.text

    fun scan(source: LuminanceSource): Result? {
        // ZXing's local threshold handles shadows; global histogram is useful for clean screen codes.
        // Do not lower contrast or impose a single brightness threshold on every camera.
        val reader = QRCodeReader()
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        for (inverted in listOf(false, true)) for (local in listOf(true, false)) {
            val image = if (inverted) source.invert() else source
            val binarizer = if (local) HybridBinarizer(image) else GlobalHistogramBinarizer(image)
            val text = try { reader.decode(BinaryBitmap(binarizer), hints).text }
                catch (_: com.google.zxing.ReaderException) { continue }
                finally { reader.reset() }
            if (!text.startsWith("cyime://pair")) return Result.OtherCode
            return if (runCatching { WindowsPairingProtocol.offer(text) }.isSuccess) Result.PairingCode(text)
                else Result.InvalidPairingCode
        }
        return null
    }
}
