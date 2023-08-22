package com.google.android.exoplayer2.demo.ads

import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ads.AdsLoader
import java.io.IOException

interface AdTagLoader {
    fun setCuePoints(cuePointListSeconds: List<Long>, initCuePointMs: Long)

    fun addAdsLoadEventListener(eventListener: AdsLoader.EventListener)

    fun setPlayer(player: Player)

    fun onReceiveAds(
            vastAdUriList: List<String>,
            vastAdDurationList: List<Long>,
            adRequestType: AdRequestType,
            targetCuePointMicro: Long
    )

    fun skipCurrentAdGroup()

    /** Skips the current ad, if there is one.  */
    fun skipCurrentAd()

    /** Stops passing of events from this instance and unregisters obstructions.  */
    fun removeListener(eventListener: AdsLoader.EventListener)

    fun handlePrepareComplete(adGroupIndex: Int, adIndexInAdGroup: Int)

    fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException?)

    fun deactivate()

    /** Releases all resources used by the ad tag loader.  */
    fun release()
    fun updateNextCuePoint(nextCuePointMillis: Long)
    fun markAdPlayed(adGroupIndex: Int, adIndexInAdGroup: Int)
}