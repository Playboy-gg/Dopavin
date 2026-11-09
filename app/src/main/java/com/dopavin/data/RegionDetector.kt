package com.dopavin.data

import android.content.Context
import android.os.LocaleList
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Reads the listener's country and languages from the phone: the SIM and network country and the phone's
 * language list. None of this needs a permission and nothing is asked. Any failure gives an empty profile,
 * which behaves like "unknown".
 */
object RegionDetector {

    fun detect(context: Context): RegionProfile = try {
        val app = context.applicationContext
        val telephony = app.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val locales = LocaleList.getDefault()
        val languages = (0 until locales.size()).map { locales.get(it).language }
        RegionProfiles.build(
            simCountry = telephony?.simCountryIso,
            networkCountry = telephony?.networkCountryIso,
            localeCountry = Locale.getDefault().country,
            phoneLanguages = languages,
        )
    } catch (e: Exception) {
        RegionProfile("", emptyList())
    }
}
