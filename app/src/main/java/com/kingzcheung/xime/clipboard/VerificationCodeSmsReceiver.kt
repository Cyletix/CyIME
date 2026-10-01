package com.kingzcheung.xime.clipboard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.kingzcheung.xime.settings.SettingsPreferences

/** Receives new SMS only after the user enables and authorizes the feature. */
class VerificationCodeSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION ||
            !SettingsPreferences.isSmsCodeEnabled(context) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
        ) return
        val body = Telephony.Sms.Intents.getMessagesFromIntent(intent).joinToString("") { it.messageBody.orEmpty() }
        val code = VerificationCodeExtractor.extract(body) ?: return
        val clip = ClipData.newPlainText(CLIP_LABEL, code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(clip)
    }

    companion object { const val CLIP_LABEL = "cyime_verification_code" }
}
