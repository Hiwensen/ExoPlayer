package com.google.android.exoplayer2.demo.ads

import com.google.android.exoplayer2.source.ads.AdsLoader

const val ADS_LOADER_DEBUG = "adsLoaderDebug"
interface ExoAdsLoader : AdsLoader {
    fun setCuePoints(cuePointListSeconds: List<Long>, initCuePointMs: Long)

    fun onReceiveAds(
            vastAdUriList: List<String>,
            vastAdDurationList: List<Long>,
            adRequestType: AdRequestType,
            targetCuePointMicro: Long
    )

    fun skipCurrentAdGroup()

    fun skipCurrentAd()
    fun updateNextCuePoint(nextCuePointMillis: Long)
    fun markAdPlayed(adGroupIndex: Int, adIndexInAdGroup: Int)
    fun onSeekTo(positionMs: Long)
}

enum class AdRequestType{
    REGULAR,
    FFWD
}