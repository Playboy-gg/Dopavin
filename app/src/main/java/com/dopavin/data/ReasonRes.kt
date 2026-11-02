package com.dopavin.data
import com.dopavin.R
fun ReasonCode.reasonRes(): Int = when (this) {
    ReasonCode.MOOD_MATCH -> R.string.reason_mood
    ReasonCode.TASTE -> R.string.reason_taste
    ReasonCode.FAVORITE_ARTIST -> R.string.reason_artist
    ReasonCode.SESSION -> R.string.reason_session
    ReasonCode.REDISCOVERY -> R.string.reason_rediscovery
    ReasonCode.RELATED -> R.string.reason_related
}
