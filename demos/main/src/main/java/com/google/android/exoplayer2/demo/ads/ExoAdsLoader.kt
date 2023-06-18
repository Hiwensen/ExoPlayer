package com.google.android.exoplayer2.demo.ads

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.*
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.*

val ADS_LOADER_DEBUG = "adsLoaderDebug"
val ACTIVE_DEBUG = "activeDebug"
val SEAMLESS_DEBUG = "SeamlessDebug"

class ExoAdsLoader(val context: Context) : AdsLoader, Player.Listener {
    private val TAG = ExoAdsLoader::class.java.simpleName
    private var nextPlayer: Player? = null
    private var player: Player? = null
    private var wasSetPlayerCalled = false
    private var supportedMimeTypes: List<String> = emptyList()
    private val adTagLoaderByAdsId: HashMap<Any, ExoAdTagLoader> = HashMap()
    private val adTagLoaderByAdsMediaSource: HashMap<AdsMediaSource, ExoAdTagLoader> = HashMap()
    private var currentAdTagLoader: ExoAdTagLoader? = null
    private val period = Timeline.Period()
    private val window = Timeline.Window()
    private val playerListener = PlayerListenerImpl()

    override fun setPlayer(player: Player?) {
        Log.d(ADS_LOADER_DEBUG,"setPlayer")
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
        Log.d(ADS_LOADER_DEBUG, "start, adsId:$adsId")
        checkState(
            wasSetPlayerCalled, "Set player using adsLoader.setPlayer before preparing the player."
        )

        if (adTagLoaderByAdsMediaSource.isEmpty()) {
            player = nextPlayer
            val player: Player = this.player ?: return
            player.addListener(this)
        }

        var adTagLoader: ExoAdTagLoader? = adTagLoaderByAdsId[adsId]
        if (adTagLoader == null) {
            requestAds(adTagDataSpec, adsId, adViewProvider.adViewGroup)
            adTagLoader = adTagLoaderByAdsId[adsId]
        }
        adTagLoaderByAdsMediaSource[adsMediaSource] = adTagLoader!!
        adTagLoader.addListenerWithAdView(eventListener, adViewProvider)
        //        maybeUpdateCurrentAdTagLoader()

        //         todo remove this line
        player?.let {
            adTagLoader.activate(it)
        }
        currentAdTagLoader = adTagLoader
    }

    private fun requestAds(adTagDataSpec: DataSpec, adsId: Any, adViewGroup: ViewGroup?) {
        if (!adTagLoaderByAdsId.containsKey(adsId)) {
            val adTagLoader = ExoAdTagLoader(
                context,
                supportedMimeTypes,
                adTagDataSpec,
                adsId,
                adViewGroup
            )
            adTagLoaderByAdsId[adsId] = adTagLoader
        }
    }

    override fun stop(adsMediaSource: AdsMediaSource, eventListener: AdsLoader.EventListener) {
        currentAdTagLoader?.deactivate()

        //        val removedAdTagLoader = adTagLoaderByAdsMediaSource.remove(adsMediaSource)
        //        maybeUpdateCurrentAdTagLoader()
        //        removedAdTagLoader?.removeListener(eventListener)
        //
        //        if (player != null && adTagLoaderByAdsMediaSource.isEmpty()) {
        //            player!!.removeListener(playerListener)
        //            player = null
        //        }
    }

    override fun release() {
        if (player != null) {
            player!!.removeListener(playerListener)
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
        Log.d(ADS_LOADER_DEBUG, "handlePrepareComplete, adGroupIndex:$adGroupIndex, adIndexInGroup:$adIndexInAdGroup")

    }

    override fun handlePrepareError(
        adsMediaSource: AdsMediaSource,
        adGroupIndex: Int,
        adIndexInAdGroup: Int,
        exception: IOException
    ) {
        Log.d(
            ADS_LOADER_DEBUG, "handlePrepareError, adGroupIndex:$adGroupIndex, " +
                    "adIndexInAdGroup:$adIndexInAdGroup, exception:$exception"
        )
        Assertions.checkNotNull<ExoAdTagLoader>(adTagLoaderByAdsMediaSource[adsMediaSource])
            .handlePrepareError(adGroupIndex, adIndexInAdGroup, exception)
    }

    // Internal methods.

    private fun maybeUpdateCurrentAdTagLoader() {
        val oldAdTagLoader = currentAdTagLoader
        val newAdTagLoader = getCurrentAdTagLoader()
        if (!Util.areEqual(oldAdTagLoader, newAdTagLoader)) {
            oldAdTagLoader?.deactivate()
            currentAdTagLoader = newAdTagLoader
            newAdTagLoader?.activate(Assertions.checkNotNull(player))
            Log.d("activeDebug", "set new adTagLoader and active")
        }
    }

    private fun getCurrentAdTagLoader(): ExoAdTagLoader? {
        val player = player ?: return null
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            Log.d(ACTIVE_DEBUG, "getCurrentAdTagLoader, timeline is empty")
            return null
        }
        val periodIndex = player.currentPeriodIndex
        //        val adsId = timeline.getPeriod(periodIndex, period).adsId ?: return null
        val adsId = timeline.getPeriod(periodIndex, period).adsId
        if (adsId == null) {
            Log.d(ACTIVE_DEBUG, "getCurrentAdTagLoader, adsId is null")
        }
        val adTagLoader = adTagLoaderByAdsId[adsId]
        return if (adTagLoader == null || !adTagLoaderByAdsMediaSource.containsValue(adTagLoader)) {
            Log.d(
                ACTIVE_DEBUG,
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
        val periodPositionUs = timeline.getPeriodPositionUs(
            window, period, period.windowIndex,  /* windowPositionUs= */C.TIME_UNSET
        ).second
        nextAdTagLoader.maybePreloadAds(Util.usToMs(periodPositionUs), Util.usToMs(period.durationUs))
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

    fun onSeekTo(positionMilli: Long) {
        currentAdTagLoader?.onSeekTo(positionMilli)
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