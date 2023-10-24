package com.google.android.exoplayer2.demo.ads

import android.net.Uri
import android.os.Looper
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.DiscontinuityReason
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.Player.PositionInfo
import com.google.android.exoplayer2.Player.RepeatMode
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.tubitv.features.seamlessplay.SingleCuePointAdTagLoader
import java.io.IOException
import java.util.Collections

val AD_TAG_URI = Uri.parse("")

class DefaultExoAdsLoader : ExoAdsLoader, Listener {
    private val TAG = DefaultExoAdsLoader::class.java.simpleName
    private var nextPlayer: Player? = null
    private var player: Player? = null
    private var wasSetPlayerCalled = false
    private var supportedMimeTypes: List<String> = emptyList()
    private val adTagLoaderByAdsId: HashMap<Any, AdTagLoader> = HashMap()
    private val adTagLoaderByAdsMediaSource: HashMap<AdsMediaSource, AdTagLoader> = HashMap()
    private var currentAdTagLoader: AdTagLoader? = null
    private val period = Timeline.Period()
    private val window = Timeline.Window()
    private val playerListener = PlayerListenerImpl()
    private var cuePointListSeconds: List<Long> = listOf()
    private var initCuePointMs = 0L
    private val useMultipleCuePoint = false

    override fun setCuePoints(cuePointListSeconds: List<Long>, initCuePointMs: Long) {
        Log.d(
            TAG,
            "ExoAdsLoader, setCuePoints, cuePointListSecond:$cuePointListSeconds, initCuePointMs:$initCuePointMs"
        )
        this.cuePointListSeconds = cuePointListSeconds
        this.initCuePointMs = initCuePointMs
    }

    override fun onReceiveAds(
            vastAdUriList: List<String>,
            vastAdDurationList: List<Long>,
            adRequestType: AdRequestType,
            targetCuePointMicro: Long
    ) {
        currentAdTagLoader?.onReceiveAds(vastAdUriList, vastAdDurationList, adRequestType, targetCuePointMicro)
    }

    override fun skipCurrentAdGroup() {
        currentAdTagLoader?.skipCurrentAdGroup()
    }

    override fun skipCurrentAd() {
    }

    override fun updateNextCuePoint(nextCuePointMillis: Long) {
        currentAdTagLoader?.updateNextCuePoint(nextCuePointMillis)
    }

    override fun markAdPlayed(adGroupIndex: Int, adIndexInAdGroup: Int) {
        currentAdTagLoader?.markAdPlayed(adGroupIndex, adIndexInAdGroup)
    }

    override fun onSeekTo(positionMs: Long) {

    }

    override fun setPlayer(player: Player?) {
        checkState(Looper.myLooper() == Looper.getMainLooper())
        nextPlayer = player
        wasSetPlayerCalled = true
    }

    override fun setSupportedContentTypes(vararg contentTypes: Int) {
        val supportedMimeTypes: MutableList<String> = ArrayList()
        for (contentType in contentTypes) {
            // IMA does not support Smooth Streaming ad media.
            when (contentType) {
                C.CONTENT_TYPE_DASH -> {
                    supportedMimeTypes.add(MimeTypes.APPLICATION_MPD)
                }

                C.CONTENT_TYPE_HLS -> {
                    supportedMimeTypes.add(MimeTypes.APPLICATION_M3U8)
                }

                C.CONTENT_TYPE_OTHER -> {
                    supportedMimeTypes.addAll(
                        listOf(
                            MimeTypes.VIDEO_MP4,
                            MimeTypes.VIDEO_WEBM,
                            MimeTypes.VIDEO_H263,
                            MimeTypes.AUDIO_MP4,
                            MimeTypes.AUDIO_MPEG
                        )
                    )
                }
            }
        }
        this.supportedMimeTypes = Collections.unmodifiableList(supportedMimeTypes)
    }

    override fun start(
        adsMediaSource: AdsMediaSource,
        adTagDataSpec: DataSpec,
        adsId: Any,
        adViewProvider: AdViewProvider,
        eventListener: AdsLoader.EventListener
    ) {
        Log.d(TAG, "start, adsId:$adsId")
        checkState(
            wasSetPlayerCalled,
            "Set player using adsLoader.setPlayer before preparing the player."
        )

        if (adTagLoaderByAdsMediaSource.isEmpty()) {
            player = nextPlayer
            val player: Player = this.player ?: return
            player.addListener(this)
        }

        var adTagLoader: AdTagLoader? = adTagLoaderByAdsId[adsId]
        if (adTagLoader == null) {
            requestAds(adTagDataSpec, adsId)
            adTagLoader = adTagLoaderByAdsId[adsId]
        }
        adTagLoader?.let {
            adTagLoaderByAdsMediaSource[adsMediaSource] = adTagLoader
        }
        adTagLoader?.setCuePoints(cuePointListSeconds, initCuePointMs)
        adTagLoader?.addAdsLoadEventListener(eventListener)
        player?.let {
            adTagLoader?.setPlayer(it)
        }
        currentAdTagLoader = adTagLoader
    }

    override fun stop(adsMediaSource: AdsMediaSource, eventListener: AdsLoader.EventListener) {
        currentAdTagLoader?.deactivate()
    }

    override fun release() {
        player?.let {
            it.removeListener(playerListener)
            player = null
            maybeUpdateCurrentAdTagLoader()
        }
        nextPlayer = null

        for (adTagLoader in adTagLoaderByAdsMediaSource.values) {
            adTagLoader.release()
        }
        adTagLoaderByAdsMediaSource.clear()

        for (adTagLoader in adTagLoaderByAdsId.values) {
            adTagLoader.release()
        }
        adTagLoaderByAdsId.clear()
    }

    override fun handlePrepareComplete(adsMediaSource: AdsMediaSource, adGroupIndex: Int, adIndexInAdGroup: Int) {
        Log.d(TAG, "handlePrepareComplete, adGroupIndex:$adGroupIndex, adIndexInGroup:$adIndexInAdGroup")
        Assertions.checkNotNull<AdTagLoader>(adTagLoaderByAdsMediaSource[adsMediaSource])
            .handlePrepareComplete(adGroupIndex, adIndexInAdGroup)
    }

    override fun handlePrepareError(
        adsMediaSource: AdsMediaSource,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: IOException
    ) {
        Log.d(
            TAG,
            "handlePrepareError, adGroupIndex:$adGroupIndex," +
                "adIndexInAdGroup:$adIndexInAdGroup, exception:$exception"
        )

        Assertions.checkNotNull<AdTagLoader>(adTagLoaderByAdsMediaSource[adsMediaSource])
            .handlePrepareError(adGroupIndex, adIndexInAdGroup, exception)
    }

    // Internal methods.
    private fun requestAds(adTagDataSpec: DataSpec, adsId: Any) {
        if (!adTagLoaderByAdsId.containsKey(adsId)) {
            val adTagLoader = if (useMultipleCuePoint) {
//                MultipleCuePointAdTagLoader(adTagDataSpec, adsId)
                MultipleAdTagLoaderV2(adTagDataSpec, adsId)
            } else {
                SingleCuePointAdTagLoader(adTagDataSpec, adsId)
            }
            adTagLoaderByAdsId[adsId] = adTagLoader
        }
    }

    private fun maybeUpdateCurrentAdTagLoader() {
        val oldAdTagLoader = currentAdTagLoader
        val newAdTagLoader = getCurrentAdTagLoader()
        if (!Util.areEqual(oldAdTagLoader, newAdTagLoader)) {
            oldAdTagLoader?.deactivate()
            currentAdTagLoader = newAdTagLoader
            newAdTagLoader?.setPlayer(Assertions.checkNotNull(player))
            Log.d(TAG, "set new adTagLoader and active")
        }
    }

    private fun getCurrentAdTagLoader(): AdTagLoader? {
        val player = player ?: return null
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            Log.d(TAG, "getCurrentAdTagLoader, timeline is empty")
            return null
        }
        val periodIndex = player.currentPeriodIndex
        val adsId = timeline.getPeriod(periodIndex, period).adsId
        if (adsId == null) {
            Log.d(TAG, "getCurrentAdTagLoader, adsId is null")
        }
        val adTagLoader = adTagLoaderByAdsId[adsId]
        return if (adTagLoader == null || !adTagLoaderByAdsMediaSource.containsValue(adTagLoader)) {
            Log.d(
                TAG,
                "adTagLoader:$adTagLoader, containsValue:${adTagLoaderByAdsMediaSource.containsValue(adTagLoader)}"
            )
            null
        } else adTagLoader
    }

    private fun maybePreloadNextPeriodAds() {
        val player: Player = this.player ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            return
        }
        val nextPeriodIndex = timeline.getNextPeriodIndex(
            player.currentPeriodIndex,
            period,
            window,
            player.repeatMode,
            player.shuffleModeEnabled
        )
        if (nextPeriodIndex == C.INDEX_UNSET) {
            return
        }
        timeline.getPeriod(nextPeriodIndex, period)
        val nextAdsId = period.adsId ?: return
        val nextAdTagLoader = adTagLoaderByAdsId[nextAdsId]
        if (nextAdTagLoader == null || nextAdTagLoader === currentAdTagLoader) {
            return
        }
    }

    private fun checkState(expression: Boolean) {
        if (!expression) {
            throw IllegalStateException()
        }
    }

    private fun checkState(expression: Boolean, errorMessage: Any) {
        if (!expression) {
            throw java.lang.IllegalStateException(errorMessage.toString())
        }
    }

    private inner class PlayerListenerImpl : Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: @TimelineChangeReason Int) {
            if (timeline.isEmpty) {
                // The player is being reset or contains no media.
                return
            }
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onPositionDiscontinuity(
            oldPosition: PositionInfo,
            newPosition: PositionInfo,
            reason: @DiscontinuityReason Int
        ) {
            maybeUpdateCurrentAdTagLoader()
            maybePreloadNextPeriodAds()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            maybePreloadNextPeriodAds()
        }

        override fun onRepeatModeChanged(repeatMode: @RepeatMode Int) {
            maybePreloadNextPeriodAds()
        }
    }
}