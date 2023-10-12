package com.google.android.exoplayer2.demo.ads

import androidx.core.net.toUri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.demo.TimeHelper
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

class MultipleAdTagLoaderV2(private val adTagDataSpec: DataSpec, private val adsId: Any) :
    AdTagLoader, Player.Listener {
    private val TAG = MultipleAdTagLoaderV2::class.java.simpleName
    private val AD_NEXT_CUE_POINT_MAXIMUM_BUFFER_TIME_MS = 10 * C.MICROS_PER_SECOND
    private val FFWD_AD_PRELOAD_TIME_MS: Long = 7 * 1000L
    private val PLAY_POSITION_BEGINNING_MS: Long = 0L

    private var playingAdGroupIndex: Int = C.INDEX_UNSET
    private var playingAdIndexInAdGroup: Int = C.INDEX_UNSET
    private var released: Boolean = false
    private val eventListeners: ArrayList<AdsLoader.EventListener> = ArrayList()
    private var adPlaybackState: AdPlaybackState = AdPlaybackState.NONE
    private var originalAdPlaybackState: AdPlaybackState = AdPlaybackState.NONE

    private var player: Player? = null
    private var timeline = Timeline.EMPTY
    private var period = Timeline.Period()
    private var contentDurationMs = C.TIME_UNSET
    private var playingAd = false

    private var cuePointListMicroOriginal = listOf<Long>()
    private var cuePointListMicro = mutableListOf<Long>()
    private var initCuePointMicro = 0L

    override fun setCuePoints(cuePointListSeconds: List<Long>, initCuePointMs: Long) {
        Log.d(
            TAG,
            "setCuePoints, initCuePoint minute:${TimeHelper.milliToMinute(initCuePointMs)}," +
                "cuePointListSeconds:$cuePointListSeconds"
        )
        cuePointListMicroOriginal = cuePointListSeconds.map { it * C.MICROS_PER_SECOND }
        initCuePointMicro = initCuePointMs * TimeHelper.MILLISECONDS_TO_MICROSECONDS
        cuePointListMicro.addAll(cuePointListMicroOriginal)
        originalAdPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(cuePointListMicroOriginal))
    }

    override fun addAdsLoadEventListener(eventListener: AdsLoader.EventListener) {
        Log.d(TAG, "addAdsLoadEventListener")
        val isStarted: Boolean = eventListeners.isNotEmpty()
        eventListeners.add(eventListener)
        if (isStarted) {
            if (AdPlaybackState.NONE != adPlaybackState) {
                // Pass the existing ad playback state to the new listener.
                eventListener.onAdPlaybackState(adPlaybackState)
            }
            return
        }

        if (AdPlaybackState.NONE == adPlaybackState) {
            createInitAdPlaybackState()
        }
        Log.d(TAG, "addAdsLoadEventListener, updateAdPlaybackState")
        // Pass the ad playback state to the player, and resume ads if necessary.
        updateAdPlaybackState()
    }

    private fun createInitAdPlaybackState() {
        when {
            initCuePointMicro > PLAY_POSITION_BEGINNING_MS -> {
                // Resume play
                Log.d(
                    TAG,
                    "addAdsLoadEventListener, resume position min:${TimeHelper.microToMinute(initCuePointMicro)}"
                )
                when {
                    cuePointListMicroOriginal.contains(initCuePointMicro) -> {
                        // resume position is the original cue point
                        Log.d(TAG, "addAdsLoadEventListener, create AdPlaybackState")
                        adPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(cuePointListMicro)).also {
                            for (i in 0 until adPlaybackState.adGroupCount) {
//                                adPlaybackState = adPlaybackState.withIsServerSideInserted(i, true)
                                adPlaybackState = adPlaybackState
                                        .withAdCount (i, 1)
                                        .withSkippedAdGroup (i)
                            }
                        }

                        cuePointListMicro.indexOf(initCuePointMicro).takeIf { it != -1 }?.let { cuePointIndex ->
                            for (i in 0 until cuePointIndex) {
                                Log.d(TAG, "addAdsLoadEventListener, skip adGroup:$i")
                                adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
                            }
                        }
                    }

                    else -> {
                        // resume position is not the original cue point
                        Log.d(
                            TAG,
                            "addAdsLoadEventListener, create AdPlaybackState resume position not original cuePoint"
                        )
                        var updatedIndex = -1
                        cuePointListMicro.indexOfLast { it < initCuePointMicro }.takeIf { it != -1 }?.let {
                            Log.d(
                                TAG,
                                "addAdsLoadEventListener, replace original cuePoint, index:$it," +
                                    "original value min:${TimeHelper.microToMinute(cuePointListMicro[it])}," +
                                    "new value min:${TimeHelper.microToMinute(initCuePointMicro)}"
                            )
                            updatedIndex = it
                            cuePointListMicro[it] = initCuePointMicro
                        }
                        adPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(cuePointListMicro)).also {
                            for (i in 0 until adPlaybackState.adGroupCount) {
//                                adPlaybackState = adPlaybackState.withIsServerSideInserted(i, true)
                                adPlaybackState = adPlaybackState
                                        .withAdCount (i, 1)
                                        .withSkippedAdGroup (i)
                            }
                        }
                        for (i in 0 until adPlaybackState.adGroupCount) {
                            adPlaybackState.withIsServerSideInserted(i, true)
                        }
                        for (i in 0 until updatedIndex) {
                            Log.d(TAG, "addAdsLoadEventListener, skip adGroup:$i")
                            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
                        }
                    }
                }
            }

            else -> {
                // Play from beginning
                adPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(cuePointListMicroOriginal)).also {
                    for (i in 0 until adPlaybackState.adGroupCount) {
//                        adPlaybackState = adPlaybackState.withIsServerSideInserted(i, true)
                        adPlaybackState = adPlaybackState
                                .withAdCount (i, 1)
                                .withSkippedAdGroup (i)
                    }
                }
            }
        }
    }

    override fun setPlayer(player: Player) {
        Log.d(TAG, "setPlayer")
        this.player = player
        player.addListener(this)
        onTimelineChanged(player.currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
    }

    override fun onReceiveAds(
        vastAdUriList: List<String>,
        vastAdDurationList: List<Long>,
        adRequestType: AdRequestType,
        targetCuePointMicro: Long
    ) {
        Log.d(TAG, "onReceiveAds, adRequestType:$adRequestType")
        val adGroupIndex = getAdGroupIndexForPosition(targetCuePointMicro)
        if (adGroupIndex == C.INDEX_UNSET) {
            Log.d(TAG, "onReceiveAds, adGroupIndex unset, return")
            return
        }

        when {
            vastAdUriList.isNotEmpty() -> {
                val previousAdCount = adPlaybackState.getAdGroup(adGroupIndex).count
                // To prevent exception caused by ad filled number less than the previous filled number
                val adCount = max(previousAdCount, vastAdUriList.size)
                adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, adCount)
                val adGroupDurationUs = mutableListOf<Long>()
                for (adIndex in 0 until adCount) {
                    adGroupDurationUs.add(vastAdDurationList.getOrElse(adIndex) { C.TIME_UNSET })
                    adPlaybackState = if (adIndex < vastAdUriList.size) {
                        Log.d(
                            TAG,
                            "onAdResponse, ad filled, adGroupIndex:$adGroupIndex, adIndexInGroup:$adIndex," +
//                                "adDurationMicro:${vastAdDurationList[adIndex]}," +
                                "adUri:${vastAdUriList[adIndex]}"
                        )
                        adPlaybackState.withAvailableAdUri(adGroupIndex, adIndex, vastAdUriList[adIndex].toUri())
                    } else {
                        adPlaybackState.withSkippedAd(adGroupIndex, adIndex)
                    }
                }
                adPlaybackState = adPlaybackState.withAdDurationsUs(adGroupIndex, *adGroupDurationUs.toLongArray())
            }

            else -> {
                Log.d(TAG, "onAdResponse, ad empty, skip adGroupIndex:$adGroupIndex")
                adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex)
            }
        }

        updateAdPlaybackState()
    }

    override fun skipCurrentAdGroup() {
        player?.let { player ->
            getAdGroupIndexForPosition(Util.msToUs(player.contentPosition)).takeIf { it != -1 }?.let { adGroupIndex ->
                Log.d(TAG, "skipCurrentAdGroup, adGroupIndex:$adGroupIndex")
                adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex)
                updateAdPlaybackState()
            }
        }
    }

    override fun removeListener(eventListener: AdsLoader.EventListener) {
        eventListeners.remove(eventListener)
    }

    override fun handlePrepareComplete(adGroupIndex: Int, adIndexInAdGroup: Int) {
        Log.d(TAG, "handlePrepareComplete, adGroupIndex:$adGroupIndex, adIndexInAdGroup:$adIndexInAdGroup")
    }

    override fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException?) {
        if (player == null) {
            return
        }
        playingAdIndexInAdGroup = adPlaybackState.getAdGroup(adGroupIndex).firstAdIndexToPlay
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    override fun deactivate() {
        player?.let {
            it.removeListener(this)
            player = null
        }
    }

    override fun release() {
        if (released) {
            return
        }
        released = true
        // No more ads will play once the loader is released, so mark all ad groups as skipped.
        for (i in 0 until adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        updateAdPlaybackState()
    }

    override fun markAdPlayed(adGroupIndex: Int, adIndexInAdGroup: Int) {
        Log.d(TAG, "markAdPlayed, adGroupIndex:$adGroupIndex, adIndexInGroup:$adIndexInAdGroup")
        adPlaybackState = adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    override fun skipCurrentAd() = Unit

    override fun updateNextCuePoint(nextCuePointMillis: Long) = Unit

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        val player = player ?: return
        if (timeline.isEmpty) {
            // The player is being reset or contains no media.
            return
        }
        this.timeline = timeline
        val contentDurationUs = timeline.getPeriod(player.currentPeriodIndex, period).durationUs
        contentDurationMs = Util.usToMs(contentDurationUs)
        if (contentDurationUs != adPlaybackState.contentDurationUs) {
            adPlaybackState = adPlaybackState.withContentDurationUs(contentDurationUs)
            updateAdPlaybackState()
        }
        handleTimelineOrPositionChanged()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        Log.d(TAG, "onPositionDiscontinuity:$reason")
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            onSeek(
                TimeHelper.milliToMicro(oldPosition.positionMs),
                TimeHelper.milliToMicro(newPosition.positionMs)
            )
        }
        handleTimelineOrPositionChanged()
    }

    private fun handleTimelineOrPositionChanged() {
        val player = player ?: return
        val wasPlayingAd = playingAd
        val oldPlayingAdGroupIndex = playingAdGroupIndex
        val oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup
        playingAd = player.isPlayingAd
        playingAdGroupIndex = if (playingAd) {
            player.currentAdGroupIndex
        } else if (wasPlayingAd) {
            oldPlayingAdGroupIndex
        } else {
            C.INDEX_UNSET
        }
        playingAdIndexInAdGroup = if (playingAd) player.currentAdIndexInAdGroup else C.INDEX_UNSET
        val adGroup = adPlaybackState.getAdGroup(playingAdGroupIndex)
        if (0 >= adGroup.count || adGroup.durationsUs.isEmpty()) {
            playingAd = false
            return
        }

        val adFinished =
            wasPlayingAd &&
                (playingAdIndexInAdGroup == C.INDEX_UNSET || oldPlayingAdIndexInAdGroup < playingAdIndexInAdGroup)
        if (adFinished &&
            adGroup.firstAdIndexToPlay <= oldPlayingAdIndexInAdGroup
        ) {
            // Ad played
            Log.d(
                TAG,
                "withPlayedAd, playingAdGroupIndex:$playingAdGroupIndex, " +
                    "oldPlayingAdIndexInAdGroup:$oldPlayingAdIndexInAdGroup"
            )
            adPlaybackState = adPlaybackState.withPlayedAd(playingAdGroupIndex, oldPlayingAdIndexInAdGroup)
            if (C.INDEX_UNSET == player.currentAdGroupIndex) {
                playingAdGroupIndex = C.INDEX_UNSET
            }
            updateAdPlaybackState()
        }
    }

    private fun onSeek(oldPositionUs: Long, newPositionUs: Long) {
        Log.d(TAG, "onSeek, newPositionUs:$newPositionUs, oldPositionUs:$oldPositionUs")
        val startPosition = min(oldPositionUs, newPositionUs)
        val endPosition = max(oldPositionUs, newPositionUs)
        var latestSkippedAdGroupIndex = C.INDEX_UNSET

        repeat(adPlaybackState.adGroupCount) { adGroupIndex ->
            val adGroupPositionUs = adPlaybackState.getAdGroup(adGroupIndex).timeUs
            Log.d(
                TAG,
                "onSeek, adGroup position US:$adGroupPositionUs, " +
                    "newPositionUs:$newPositionUs"
            )
            if (adGroupPositionUs in startPosition..endPosition) {
                when {
                    oldPositionUs < newPositionUs -> {
                        // seek forward, skip cue point
                        latestSkippedAdGroupIndex = adGroupIndex
                        Log.d(
                            TAG,
                            "handle seek forward, skip ad group index:$adGroupIndex, " +
                                "ad group position micro:$adGroupPositionUs"
                        )
                        adPlaybackState = adPlaybackState.withSkippedAdGroup(adGroupIndex)
                    }

                    else -> {
                        // seek rewind, revert cue point
                        val originalAdGroup = originalAdPlaybackState.getAdGroup(adGroupIndex)
                        if (originalAdGroup.timeUs != adGroupPositionUs) {
                            Log.d(
                                TAG,
                                "handle seek rewind, revert adGroupTime:$adGroupIndex, " +
                                    "adGroup min:${TimeHelper.microToMinute(adGroupPositionUs)}," +
                                    "originalAdGroup min:${TimeHelper.microToMinute(originalAdGroup.timeUs)}"
                            )
                            adPlaybackState = adPlaybackState.withAdGroupTimeUs(adGroupIndex, originalAdGroup.timeUs)
                        }
                    }
                }
            }
        }

        if (C.INDEX_UNSET != latestSkippedAdGroupIndex &&
            isNewPositionAllowToPlayAd(adPlaybackState, newPositionUs, latestSkippedAdGroupIndex)
        ) {
            // Seek forward and skip one cue point, update the latest cue point before the new position
            Log.d(
                TAG,
                "handle seek forward, update cuePoint position, adGroupIndex:$latestSkippedAdGroupIndex, " +
                    "newPositionMin:${TimeHelper.microToMinute(newPositionUs)}"
            )
            val updatedPosition = newPositionUs + Util.msToUs(FFWD_AD_PRELOAD_TIME_MS)
            adPlaybackState = adPlaybackState
                .withAdGroupTimeUs(latestSkippedAdGroupIndex, updatedPosition)
        }

        updateAdPlaybackState()
    }

    private fun updateAdPlaybackState() {
        for (i in eventListeners.indices) {
            eventListeners[i].onAdPlaybackState(adPlaybackState)
        }
    }

    private fun isNewPositionAllowToPlayAd(
        adPlaybackState: AdPlaybackState,
        newPositionUs: Long,
        skippedAdGroupIndex: Int
    ): Boolean {
        val adGroup = adPlaybackState.getAdGroup(skippedAdGroupIndex)
        val result = if (skippedAdGroupIndex < adPlaybackState.adGroupCount - 1) {
            val nextAdGroupTime =
                adPlaybackState.getAdGroup(skippedAdGroupIndex + 1).timeUs - AD_NEXT_CUE_POINT_MAXIMUM_BUFFER_TIME_MS
            newPositionUs in adGroup.timeUs until nextAdGroupTime
        } else {
            newPositionUs in adGroup.timeUs until Int.MAX_VALUE
        }
        return result
    }

    private fun getAdGroupIndexForPosition(positionUs: Long): Int {
        if (positionUs == C.TIME_END_OF_SOURCE) {
            Log.d(TAG, "getAdGroupIndexForPosition, positionUs end of source")
            return C.INDEX_UNSET
        }
        for (i in 0 until adPlaybackState.adGroupCount) {
            Log.d(
                TAG,
                "getAdGroupIndexForPosition, i:$i, " +
                    "cuePoint time min:${TimeHelper.microToMinute(adPlaybackState.getAdGroup(i).timeUs)}," +
                    "cuePoint time second:${TimeHelper.microToSecond(adPlaybackState.getAdGroup(i).timeUs)}," +
                    "currentPosition second:${TimeHelper.microToSecond(positionUs)}"
            )
            if (positionUs <= adPlaybackState.getAdGroup(i).timeUs) {
                Log.d(TAG, "getAdGroupIndexForPosition, return index:$i")
                return i
            }
        }
        return C.INDEX_UNSET
    }

    private fun getAdGroupTimesUsForCuePoints(cuePointsMicro: List<Long>): LongArray {
        if (cuePointsMicro.isEmpty()) {
            return longArrayOf(0L)
        }
        val count = cuePointsMicro.size
        val adGroupTimesUs = LongArray(count)
        var adGroupIndex = 0
        for (i in 0 until count) {
            adGroupTimesUs[adGroupIndex++] = cuePointsMicro[i]
        }
        // Cue points may be out of order, so sort them.
        Arrays.sort(adGroupTimesUs, 0, adGroupIndex)
        return adGroupTimesUs
    }
}