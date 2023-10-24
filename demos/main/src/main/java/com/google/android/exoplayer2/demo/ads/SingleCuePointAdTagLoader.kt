package com.tubitv.features.seamlessplay

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.demo.TimeHelper
import com.google.android.exoplayer2.demo.ads.AdRequestType
import com.google.android.exoplayer2.demo.ads.AdTagLoader
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import java.io.IOException

class SingleCuePointAdTagLoader(private val adTagDataSpec: DataSpec, private val adsId: Any) :
    AdTagLoader, Player.Listener {
    private val TAG = SingleCuePointAdTagLoader::class.java.simpleName
    private val AD_PRELOAD_TIMEOUT_MS = 5000L
    private val AD_GROUP_INDEX_FIRST = 0

    // For play ads immediately after fast forward
    private val CUE_POINT_POSITION_DELAY_FF = 500_000
    private val handler = Handler(Looper.getMainLooper())
    private val eventListeners: ArrayList<AdsLoader.EventListener> = ArrayList()
    private val adLoadTimeoutRunnable: Runnable = Runnable {
        handleAdLoadTimeout()
    }

    private var bufferingAd = false
    private var fakeContentProgressOffsetMs: Long = -1
    private var fakeContentProgressElapsedRealtimeMs: Long = -1
    private var sentPendingContentPositionMs: Boolean = false
    private var pendingContentPositionMs: Long = -1
    private var sentContentComplete: Boolean = false
    private var playingAdIndexInAdGroup: Int = -1
    private var released: Boolean = false
    private var cuePointListMicroOriginal = mutableListOf<Float>()
    private var cuePointListMicro = mutableListOf<Float>()
    private var initCuePointMicro = 0F
    private var adPlaybackState: AdPlaybackState = AdPlaybackState.NONE
    private var pendingAdLoadError: AdLoadException? = null
    private var player: Player? = null
    private var timeline = Timeline.EMPTY
    private var period = Timeline.Period()
    private var contentDurationMs = C.TIME_UNSET
    private var playingAd = false

    override fun setCuePoints(cuePointListSeconds: List<Long>, initCuePointMs: Long) {
        this.cuePointListMicroOriginal = cuePointListSeconds.map { it * C.MICROS_PER_SECOND.toFloat() }.toMutableList()
        cuePointListMicro.clear()
        cuePointListMicro.addAll(cuePointListMicroOriginal)
        initCuePointMicro = initCuePointMs * 1000F
        if (!cuePointListMicroOriginal.contains(initCuePointMicro)) {
            cuePointListMicro.add(initCuePointMicro)
            cuePointListMicro.sort()
        }
        Log.d(
            TAG,
            "AdTagLoader, setCuePoints, initCuePointSecond:${initCuePointMs / 1000}, originalCuePoints:" +
                "$cuePointListSeconds, cuePointListMicro:$cuePointListMicro"
        )
    }

    override fun addAdsLoadEventListener(eventListener: AdsLoader.EventListener) {
        val isStarted: Boolean = eventListeners.isNotEmpty()
        eventListeners.add(eventListener)
        if (isStarted) {
            if (AdPlaybackState.NONE != adPlaybackState) {
                // Pass the existing ad playback state to the new listener.
                eventListener.onAdPlaybackState(adPlaybackState)
            }
            return
        }
        maybeNotifyPendingAdLoadError()
        if (AdPlaybackState.NONE != adPlaybackState) {
            // Pass the ad playback state to the player, and resume ads if necessary.
            eventListener.onAdPlaybackState(adPlaybackState)
        } else {
            adPlaybackState = AdPlaybackState(adsId, initCuePointMicro.toLong())
            updateAdPlaybackState()
        }
    }

    override fun setPlayer(player: Player) {
        Log.d(TAG, "AdTagLoader, activate")
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
        // Do nothing if vastAdUriList empty, the cuePoint will be update at updateNextCuePoint()
        if (vastAdUriList.isEmpty()) return

        if (adRequestType == AdRequestType.FFWD) {
            Log.d(
                TAG,
                "AdTagLoader, onReceiveAds, fastForward, update AdPlaybackState, " +
                    "nextCuePoint minute:${TimeHelper.microToMinute(targetCuePointMicro)}"
            )
            adPlaybackState = AdPlaybackState(adsId, targetCuePointMicro + CUE_POINT_POSITION_DELAY_FF)
        } else {
            Log.d(TAG, "AdTagLoader, onReceiveAds, regular playback, don't update AdPlaybackState")
        }
        adPlaybackState = adPlaybackState.withAdCount(AD_GROUP_INDEX_FIRST, vastAdUriList.size)
        for (i in vastAdUriList.indices) {
            adPlaybackState =
                adPlaybackState.withAvailableAdUri(AD_GROUP_INDEX_FIRST, i, Uri.parse(vastAdUriList[i]))
        }
        updateAdPlaybackState()
    }

    override fun skipCurrentAdGroup() {
        Log.d(TAG, "skipCurrentAdGroup")
        adPlaybackState = adPlaybackState.withSkippedAdGroup(AD_GROUP_INDEX_FIRST)
        updateAdPlaybackState()
    }

    /** Skips the current skippable ad, if there is one.  */
    override fun skipCurrentAd() {
    }

    /** Stops passing of events from this instance and unregisters obstructions.  */
    override fun removeListener(eventListener: AdsLoader.EventListener) {
        eventListeners.remove(eventListener)
    }

    override fun handlePrepareComplete(adGroupIndex: Int, adIndexInAdGroup: Int) {
        if (getLoadingAdGroupIndex() == adGroupIndex) {
            handler.removeCallbacks(adLoadTimeoutRunnable)
        }
    }

    override fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException?) {
        if (player == null) {
            return
        }
        try {
            handleAdPrepareError(adGroupIndex, adIndexInAdGroup)
        } catch (e: RuntimeException) {
            Log.e(TAG, "handlePrepareError", e)
        }
    }

    override fun deactivate() {
        Log.d(TAG, "deactivate")
        player?.let {
            if (AdPlaybackState.NONE != adPlaybackState) {
                adPlaybackState = adPlaybackState.withAdResumePositionUs(
                    if (playingAd) Util.msToUs(it.currentPosition) else 0
                )
            }
            it.removeListener(this)
        }
        player = null
        handler.removeCallbacksAndMessages(null)
    }

    /** Releases all resources used by the ad tag loader.  */
    override fun release() {
        if (released) {
            return
        }
        released = true
        pendingAdLoadError = null
        // No more ads will play once the loader is released, so mark all ad groups as skipped.
        for (i in 0 until adPlaybackState.adGroupCount) {
            adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
        }
        updateAdPlaybackState()
    }

    override fun updateNextCuePoint(nextCuePointMillis: Long) {
        Log.d(TAG, "updateNextCuePoint, newPosition minutes:${nextCuePointMillis / 60000}")
//        adPlaybackState = AdPlaybackState(adsId, nextCuePointMillis * 1000)
        adPlaybackState =
            adPlaybackState.withAdGroupTimeUs(AD_GROUP_INDEX_FIRST, nextCuePointMillis * 1000)
        updateAdPlaybackState()
    }

    override fun markAdPlayed(adGroupIndex: Int, adIndexInAdGroup: Int) {
        adPlaybackState = adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (timeline.isEmpty) {
            // The player is being reset or contains no media.
            return
        }
        this.timeline = timeline
        val player = Assertions.checkNotNull(player)
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
        Log.d(
            TAG,
            "onPositionDiscontinuity, reason:$reason, " +
                "oldPosition: adGroupIndex${oldPosition.adGroupIndex},adIndexInGroup:${oldPosition.adIndexInAdGroup} " +
                "newPosition: adGroupIndex${newPosition.adGroupIndex},adIndexInGroup:${newPosition.adIndexInAdGroup} " +
                "isPlayingAd:${player?.isPlayingAd}"
        )
        handleTimelineOrPositionChanged()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        Log.d(TAG,"onPlaybackStateChanged:$playbackState")
        val player = player ?: return
        handlePlayerStateChanged(player.playWhenReady, playbackState)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: @PlayWhenReadyChangeReason Int) {
        player?.playbackState?.let {
            handlePlayerStateChanged(playWhenReady, it)
        }
    }

    private fun handleAdPrepareError(adGroupIndex: Int, adIndexInAdGroup: Int) {
        playingAdIndexInAdGroup = adPlaybackState.getAdGroup(adGroupIndex).firstAdIndexToPlay
        adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, adIndexInAdGroup)
        updateAdPlaybackState()
    }

    private fun updateAdPlaybackState() {
        for (i in eventListeners.indices) {
            eventListeners[i].onAdPlaybackState(adPlaybackState)
        }
    }

    private fun maybeNotifyPendingAdLoadError() {
        pendingAdLoadError?.let {
            for (i in eventListeners.indices) {
                eventListeners[i].onAdLoadError(it, adTagDataSpec)
            }
            pendingAdLoadError = null
        }
    }

    private fun handlePlayerStateChanged(playWhenReady: Boolean, playbackState: @Player.State Int) {
        if (playingAd) {
            if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
                bufferingAd = true
            } else if (bufferingAd && playbackState == Player.STATE_READY) {
                bufferingAd = false
            }
        }
        if (playbackState == Player.STATE_BUFFERING && playWhenReady) {
            ensureSentContentCompleteIfAtEndOfStream()
        }
    }

    private fun handleTimelineOrPositionChanged() {
        val player = player ?: return
        Log.d(TAG, "handleTimelineOrPositionChanged")
        if (!playingAd && !player.isPlayingAd) {
            ensureSentContentCompleteIfAtEndOfStream()
            if (!sentContentComplete && !timeline.isEmpty) {
                val positionMs = getContentPeriodPositionMs(player, timeline, period)
                timeline.getPeriod(player.currentPeriodIndex, period)
                val newAdGroupIndex = period.getAdGroupIndexForPositionUs(Util.msToUs(positionMs))
                if (newAdGroupIndex != C.INDEX_UNSET) {
                    sentPendingContentPositionMs = false
                    pendingContentPositionMs = positionMs
                }
            }
        }

        val wasPlayingAd = playingAd
        playingAd = player.isPlayingAd
        playingAdIndexInAdGroup = if (playingAd) player.currentAdIndexInAdGroup else C.INDEX_UNSET
        if (!sentContentComplete && !wasPlayingAd && playingAd) {
            val adGroup = adPlaybackState.getAdGroup(player.currentAdGroupIndex)
            if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
                sendContentComplete()
            } else {
                // Hasn't called playAd yet, so fake the content position.
                fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
                fakeContentProgressOffsetMs = Util.usToMs(adGroup.timeUs)
                if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                    fakeContentProgressOffsetMs = contentDurationMs
                }
            }
        }
        if (isWaitingForCurrentAdToLoad()) {
            Log.d(TAG, "handleTimelineOrPositionChanged, waitingForCurrAdToLoad, post timeout")
            handler.removeCallbacks(adLoadTimeoutRunnable)
            handler.postDelayed(adLoadTimeoutRunnable, AD_PRELOAD_TIMEOUT_MS)
        }
    }

    private fun handleAdLoadTimeout() {
        Log.d(TAG, "handle ad load timeout")
        // Got stuck and didn't load an ad in time, so skip the entire group.
        handleAdGroupLoadError(IOException("Ad loading timed out"))
        maybeNotifyPendingAdLoadError()
    }

    private fun handleAdGroupLoadError(error: Exception) {
        val adGroupIndex: Int = getLoadingAdGroupIndex()
        Log.e(TAG, "handleAdGroupLoadError, adGroupIndex:$adGroupIndex, exception:$error")
        if (adGroupIndex == C.INDEX_UNSET) {
            return
        }
        markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex)
        if (pendingAdLoadError == null) {
            pendingAdLoadError = AdLoadException.createForAdGroup(error, adGroupIndex)
        }
    }

    private fun markAdGroupInErrorStateAndClearPendingContentPosition(adGroupIndex: Int) {
        // Update the ad playback state so all ads in the ad group are in the error state.
        var adGroup = adPlaybackState.getAdGroup(adGroupIndex)
        if (adGroup.count == C.LENGTH_UNSET) {
            adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, Math.max(1, adGroup.states.size))
            adGroup = adPlaybackState.getAdGroup(adGroupIndex)
        }
        for (i in 0 until adGroup.count) {
            if (adGroup.states[i] == AdPlaybackState.AD_STATE_UNAVAILABLE) {
                Log.d(TAG, "Removing ad $i in ad group $adGroupIndex")
            }
            adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i)
        }
        updateAdPlaybackState()
        adPlaybackState.withSkippedAdGroup(adGroupIndex)
        updateAdPlaybackState()
        // Clear any pending content position that triggered attempting to load the ad group.
        pendingContentPositionMs = C.TIME_UNSET
        fakeContentProgressElapsedRealtimeMs = C.TIME_UNSET
    }

    /**
     * Returns the index of the ad group that will preload next, or [C.INDEX_UNSET] if there is
     * no such ad group.
     */
    private fun getLoadingAdGroupIndex(): Int {
        player?.let {
            val playerPositionMs = getContentPeriodPositionMs(it, timeline, period)
            var adGroupIndex = getFirstAdIndexAtPosition(playerPositionMs)
            if (adGroupIndex == C.INDEX_UNSET) {
                adGroupIndex = adPlaybackState.getAdGroupIndexAfterPositionUs(
                    Util.msToUs(playerPositionMs),
                    Util.msToUs(contentDurationMs)
                )
            }
            return adGroupIndex
        }

        return C.INDEX_UNSET
    }

    private fun isWaitingForCurrentAdToLoad(): Boolean {
        val player = player ?: return false
        val adGroupIndex = player.currentAdGroupIndex
        if (adGroupIndex == C.INDEX_UNSET) {
            return false
        }
        val adGroup = adPlaybackState.getAdGroup(adGroupIndex)
        val adIndexInAdGroup = player.currentAdIndexInAdGroup
        return if (adGroup.count == C.LENGTH_UNSET || adGroup.count <= adIndexInAdGroup) {
            true
        } else adGroup.states[adIndexInAdGroup] == AdPlaybackState.AD_STATE_UNAVAILABLE
    }

    private fun ensureSentContentCompleteIfAtEndOfStream() {
        if (sentContentComplete || contentDurationMs == C.TIME_UNSET || pendingContentPositionMs != C.TIME_UNSET) {
            return
        }

        val contentPeriodPositionMs = getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period)
        val pendingAdGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(
            Util.msToUs(contentPeriodPositionMs),
            Util.msToUs(contentDurationMs)
        )
        if (pendingAdGroupIndex != C.INDEX_UNSET &&
            adPlaybackState.getAdGroup(pendingAdGroupIndex).timeUs != C.TIME_END_OF_SOURCE &&
            adPlaybackState.getAdGroup(pendingAdGroupIndex).shouldPlayAdGroup()
        ) {
            // Pending mid-roll ad that needs to be played before marking the content complete.
            return
        }
        sendContentComplete()
    }

    private fun sendContentComplete() {
        sentContentComplete = true
        for (i in 0 until adPlaybackState.adGroupCount) {
            if (adPlaybackState.getAdGroup(i).timeUs != C.TIME_END_OF_SOURCE) {
                adPlaybackState = adPlaybackState.withSkippedAdGroup(i)
            }
        }
        updateAdPlaybackState()
    }

    private fun getContentPeriodPositionMs(player: Player, timeline: Timeline, period: Timeline.Period): Long {
        val contentWindowPositionMs = player.contentPosition
        return if (timeline.isEmpty) {
            contentWindowPositionMs
        } else {
            contentWindowPositionMs - timeline.getPeriod(player.currentPeriodIndex, period).positionInWindowMs
        }
    }

    private fun getFirstAdIndexAtPosition(currentPositionMilli: Long): Int {
        return if (initCuePointMicro >= currentPositionMilli) {
            AD_GROUP_INDEX_FIRST
        } else {
            C.INDEX_UNSET
        }
    }
}