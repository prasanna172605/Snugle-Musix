package com.snuggle.music.personalization

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.snuggle.music.constants.RegionalPreferenceProfileKey
import com.snuggle.music.utils.dataStore
import com.snuggle.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegionalPreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val selectedRegionFlow: Flow<RegionProfile> = context.dataStore.data
        .map { prefs ->
            val savedProfileCode = prefs[RegionalPreferenceProfileKey]
            if (!savedProfileCode.isNullOrBlank()) {
                RegionProfile.fromCode(savedProfileCode)
            } else {
                detectDeviceRegion()
            }
        }.distinctUntilChanged()

    suspend fun getActiveRegion(): RegionProfile {
        val saved = context.dataStore.get(RegionalPreferenceProfileKey, "")
        if (!saved.isNullOrBlank()) {
            return RegionProfile.fromCode(saved)
        }
        return detectDeviceRegion()
    }

    /**
     * Non-intrusively detects initial region prior using telephony, locale, and timezone cues.
     * Defaults to TAMIL_NADU as default Indian prior if within IST timezone, or GLOBAL if outside India.
     */
    fun detectDeviceRegion(): RegionProfile {
        val locale = Locale.getDefault()
        val country = locale.country.uppercase()
        val language = locale.language.lowercase()
        val tz = TimeZone.getDefault().id.lowercase()

        // 1. Check language-specific system locale
        when (language) {
            "ta" -> return RegionProfile.TAMIL_NADU
            "ml" -> return RegionProfile.KERALA
            "te" -> return RegionProfile.ANDHRA_PRADESH
            "kn" -> return RegionProfile.KARNATAKA
            "mr" -> return RegionProfile.MAHARASHTRA
            "bn" -> return RegionProfile.WEST_BENGAL
            "pa" -> return RegionProfile.PUNJAB
            "gu" -> return RegionProfile.GUJARAT
            "hi" -> return RegionProfile.NORTH_INDIA
        }

        // 2. Check Telephony SIM / Network country
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simCountry = telephonyManager?.simCountryIso?.uppercase().orEmpty()
        val networkCountry = telephonyManager?.networkCountryIso?.uppercase().orEmpty()

        val isIndia = country == "IN" || simCountry == "IN" || networkCountry == "IN" || tz.contains("kolkata") || tz.contains("calcutta") || tz.contains("ist")

        if (isIndia) {
            // Default initial South-Indian / Tamil prior for Indian region when unspecified
            return RegionProfile.TAMIL_NADU
        }

        return RegionProfile.GLOBAL
    }
}
