package com.google.android.exoplayer2.demo.ads

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.demo.*
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.source.ads.AdsMediaSource
import com.google.android.exoplayer2.source.ads.AdsMediaSource.AdLoadException
import com.google.android.exoplayer2.ui.AdViewProvider
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import java.io.IOException
import java.util.*
import kotlin.math.roundToLong
const val SKIP_DEBUG = "skipDebug"
class ExoAdTagLoader(
        context: Context, supportedMimeTypes: List<String>, private val adTagDataSpec: DataSpec,
        private val adsId: Any, adViewGroup: ViewGroup?
) : Player.Listener {
    private var bufferingAd = false
    private var waitingForPreloadElapsedRealtimeMs: Long = -1
    private val adPreloadTimeoutMs = 3000L
    private val adLoadTimeoutRunnable: Runnable = Runnable {
        handleAdLoadTimeout()
    }
    private var fakeContentProgressOffsetMs: Long = -1
    private var fakeContentProgressElapsedRealtimeMs: Long = -1
    private var sentPendingContentPositionMs: Boolean = false
    private var pendingContentPositionMs: Long = -1
    private var sentContentComplete: Boolean = false
    private var playingAdIndexInAdGroup: Int = -1
    private var released: Boolean = false
    private var adPlayedTime: Int = 0
    private var currentPosition: Long = -1
    private var adGroupIndexPendingReset: Int = C.INDEX_UNSET
    private var pendingResetAdGroup: Boolean = false
    private val TAG = ExoAdTagLoader::class.java.simpleName

    private val cuePointList = listOf<Float>(
            0F,
            3 * 60 * C.MICROS_PER_SECOND.toFloat(),
            6 * 60 * C.MICROS_PER_SECOND.toFloat(),
            9 * 60 * C.MICROS_PER_SECOND.toFloat(),
            12 * 60 * C.MICROS_PER_SECOND.toFloat()
    )
    private val eventListeners: ArrayList<AdsLoader.EventListener> = ArrayList()
    private var adPlaybackState: AdPlaybackState = AdPlaybackState.NONE
    private var pendingAdLoadError: AdsMediaSource.AdLoadException? = null
    private var player: Player? = null
    private var timeline = Timeline.EMPTY
    private var period = Timeline.Period()
    private var contentDurationMs = C.TIME_UNSET
    private var playingAd = false
    private val handler = Handler(Looper.getMainLooper())
    private var adIndex = 0
    private val adInsertStateMap = mutableMapOf<Int, Boolean>()
    private val shouldResetAdGroupMap = mutableMapOf<Int, Boolean>()
    private val adPlayedGroupMap = mutableMapOf<Int, Boolean>()
    private val checkPositionRunnable: Runnable = Runnable {
        if (player?.playWhenReady == true) {
            onProgress(player!!.currentPosition)
        }
        updateProgress()
    }

    companion object {
        fun getContentPeriodPositionMs(
                player: Player, timeline: Timeline, period: Timeline.Period
        ): Long {
            val contentWindowPositionMs = player.contentPosition
            return if (timeline.isEmpty) {
                contentWindowPositionMs
            } else {
                (contentWindowPositionMs
                        - timeline.getPeriod(player.currentPeriodIndex, period).positionInWindowMs)
            }
        }
    }

    fun addListenerWithAdView(eventListener: AdsLoader.EventListener, adViewProvider: AdViewProvider) {
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
            adPlaybackState = AdPlaybackState(adsId, *getAdGroupTimesUsForCuePoints(cuePointList))

//            adPlaybackState = AdPlaybackState(
//                    adsId, 3 * 60 * C.MICROS_PER_SECOND,
//                    6 * 60 * C.MICROS_PER_SECOND,
//                    9 * 60 * C.MICROS_PER_SECOND,
//                    12 * 60 * C.MICROS_PER_SECOND
//            )
            Log.d(ADS_LOADER_DEBUG, "addListenerWithAdView, update cuePoint info")
            updateAdPlaybackState()
        }
    }

    fun activate(player: Player) {
        Log.d(ADS_LOADER_DEBUG, "activate")
        Log.d("activeDebug", "activate")
        this.player = player
        player.addListener(this)
        handler.post(checkPositionRunnable)

        onTimelineChanged(player.currentTimeline, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        if (AdPlaybackState.NONE != adPlaybackState) {
            // Check whether the current ad break matches the expected ad break based on the current
            // position. If not, discard the current ad break so that the correct ad break can load.
            val contentPositionMs: Long = getContentPeriodPositionMs(player, timeline, period)
            val adGroupForPositionIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                    Util.msToUs(contentPositionMs), Util.msToUs(contentDurationMs)
            )
            if (adGroupForPositionIndex != C.INDEX_UNSET) {
                //todo discard preload ad
            }
        }
    }

    fun deactivate() {
        Log.d("activeDebug", "deactivate")
        val player = Assertions.checkNotNull<Player>(this.player)
        if (AdPlaybackState.NONE != adPlaybackState) {
            adPlaybackState = adPlaybackState.withAdResumePositionUs(
                    if (playingAd) Util.msToUs(player.currentPosition) else 0
            )
        }
        player.removeListener(this)
        this.player = null
        handler.removeCallbacksAndMessages(null)
    }

    fun onSeekTo(positionMilli: Long) {
        //        val contentPosition = player?.currentPosition ?: -1
        Log.d(
                ADS_LOADER_DEBUG,
                "onSeekTo: currentPosition:${currentPosition / 1000}, newPosition:${positionMilli / 1000}"
        )
        if (positionMilli > currentPosition) {
            cuePointList.indexOfLast { positionMilli * 1000 > it }.takeIf { it != -1 }?.let { adSkippedIndex ->
                Log.d(ADS_LOADER_DEBUG, "going to skip ad index:$adSkippedIndex")
                adPlaybackState = adPlaybackState.withSkippedAdGroup(adSkippedIndex)
                updateAdPlaybackState()
                shouldResetAdGroupMap[adSkippedIndex] = true
                //                adPlaybackState = adPlaybackState.withResetAdGroup(adSkippedIndex)
                //                updateAdPlaybackState()
            }
        }

//        if (positionMilli < currentPosition) {
//            Log.d(ADS_LOADER_DEBUG, "going to recreate AdPlaybackState after rewind")
//            adPlaybackState = AdPlaybackState(
//                adsId, 3 * 60 * C.MICROS_PER_SECOND,
//                6 * 60 * C.MICROS_PER_SECOND,
//                9 * 60 * C.MICROS_PER_SECOND,
//                12 * 60 * C.MICROS_PER_SECOND
//            )
//            updateAdPlaybackState()

//         reset the next cuePoint if necessary
        getFirstAdIndexAfterPosition(positionMilli).takeIf { it != -1 }?.let { nextCuePointIndex ->
            if (adPlayedGroupMap[nextCuePointIndex] == true) {
//                    Log.d(ADS_LOADER_DEBUG, "going to reset ad group after rewind:$nextCuePointIndex")
//                    adPlaybackState = adPlaybackState.withResetAdGroup(nextCuePointIndex)

                Log.d(ADS_LOADER_DEBUG, "going to recreate AdPlaybackState after rewind")
                adPlaybackState = AdPlaybackState(
                        adsId, *getAdGroupTimesUsForCuePoints(cuePointList)
                )
                updateAdPlaybackState()
                adPlayedGroupMap[nextCuePointIndex] = false
            }
        }
//        }
    }

    /**
     * Populates the ad playback state with loaded cue points, if available. Any preroll will be
     * paused immediately while waiting for this instance to be [activated][.activate].
     */
    fun maybePreloadAds(contentPositionMs: Long, contentDurationMs: Long) {
        Log.d(ADS_LOADER_DEBUG, "maybePreloadAds, empty implementation")
        //        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
    }

    /** Stops passing of events from this instance and unregisters obstructions.  */
    fun removeListener(eventListener: AdsLoader.EventListener?) {
        eventListeners.remove(eventListener)
    }

    /** Releases all resources used by the ad tag loader.  */
    fun release() {
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

    /** Notifies the IMA SDK that the specified ad has failed to prepare for playback.  */
    fun handlePrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException?) {
        if (player == null) {
            return
        }
        try {
            handleAdPrepareError(adGroupIndex, adIndexInAdGroup, exception)
        } catch (e: RuntimeException) {
            Log.e(TAG, "handlePrepareError", e)
        }
    }

    private fun handleAdPrepareError(adGroupIndex: Int, adIndexInAdGroup: Int, exception: IOException?) {
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
        val contentPositionMs = getContentPeriodPositionMs(player, timeline, period)
        //        maybeInitializeAdsManager(contentPositionMs, contentDurationMs)
        handleTimelineOrPositionChanged()
    }

    override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
    ) {
        Log.d(
                ADS_LOADER_DEBUG,
                "onPositionDiscontinuity, reason:$reason, " +
                        "oldPosition: adGroupIndex${oldPosition.adGroupIndex},adIndexInGroup:${oldPosition.adIndexInAdGroup} " +
                        "newPosition: adGroupIndex${newPosition.adGroupIndex},adIndexInGroup:${newPosition.adIndexInAdGroup} " +
                        "isPlayingAd:${player?.isPlayingAd}"
        )

        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            val adGroupIndex = oldPosition.adGroupIndex
            val adIndexInAdGroup = oldPosition.adIndexInAdGroup
            if (adGroupIndex != C.INDEX_UNSET && adIndexInAdGroup != C.INDEX_UNSET) {
                adPlaybackState = adPlaybackState.withPlayedAd(adGroupIndex, adIndexInAdGroup)
                updateAdPlaybackState()
                Log.d(ADS_LOADER_DEBUG, "withPlayedAd:adGroupIndex:$adGroupIndex, adIndexInAdGroup:$adIndexInAdGroup")
            }

            if (player?.isPlayingAd == false) {
                adInsertStateMap[adGroupIndex] = false
                shouldResetAdGroupMap[adGroupIndex] = true
                adPlayedGroupMap[adGroupIndex] = true
                adPlayedTime = 1
                Log.d(ADS_LOADER_DEBUG, "adGroup play complete, increase adPlayedTime")
            }
        }

        handleTimelineOrPositionChanged()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        Log.d(ADS_LOADER_DEBUG, "onPlaybackStateChanged, state:$playbackState, buffer state 2")
        val player = player ?: return

        if (playbackState == Player.STATE_BUFFERING && !player.isPlayingAd
                && isWaitingForFirstAdToPreload()
        ) {
            waitingForPreloadElapsedRealtimeMs = SystemClock.elapsedRealtime()
        } else if (playbackState == Player.STATE_READY) {
            waitingForPreloadElapsedRealtimeMs = C.TIME_UNSET
        }
        handlePlayerStateChanged(player.playWhenReady, playbackState)
    }

    override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean, reason: @PlayWhenReadyChangeReason Int
    ) {
        player?.playbackState?.let {
            handlePlayerStateChanged(playWhenReady, it)
        }
    }

    override fun onPlayerError(error: PlaybackException) {

    }

    private fun handlePlayerStateChanged(playWhenReady: Boolean, playbackState: @Player.State Int) {
        if (playingAd) {
            if (!bufferingAd && playbackState == Player.STATE_BUFFERING) {
                bufferingAd = true
                stopUpdatingAdProgress()
            } else if (bufferingAd && playbackState == Player.STATE_READY) {
                bufferingAd = false
                updateAdProgress()
            }
        }
        if (playbackState == Player.STATE_BUFFERING && playWhenReady) {
            ensureSentContentCompleteIfAtEndOfStream()
        } else if (playbackState == Player.STATE_ENDED) {

        }
    }

    private fun stopUpdatingAdProgress() {
    }

    private fun updateAdProgress() {
    }

    /** Notifies the IMA SDK that the specified ad has been prepared for playback.  */
    fun handlePrepareComplete(adGroupIndex: Int, adIndexInAdGroup: Int) {
    }

    /** Skips the current skippable ad, if there is one.  */
    fun skipAd() {
    }

    private fun handleTimelineOrPositionChanged() {
        val player = player ?: return
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
        val oldPlayingAdIndexInAdGroup = playingAdIndexInAdGroup
        playingAd = player.isPlayingAd
        playingAdIndexInAdGroup = if (playingAd) player.currentAdIndexInAdGroup else C.INDEX_UNSET
        val adFinished = wasPlayingAd && playingAdIndexInAdGroup != oldPlayingAdIndexInAdGroup
        if (adFinished) {
            // IMA is waiting for the ad playback to finish so invoke the callback now.
            // Either CONTENT_RESUME_REQUESTED will be passed next, or playAd will be called again.
        }
        if (!sentContentComplete && !wasPlayingAd && playingAd) {
            val adGroup = adPlaybackState.getAdGroup(player.currentAdGroupIndex)
            if (adGroup.timeUs == C.TIME_END_OF_SOURCE) {
                sendContentComplete()
            } else {
                // IMA hasn't called playAd yet, so fake the content position.
                fakeContentProgressElapsedRealtimeMs = SystemClock.elapsedRealtime()
                fakeContentProgressOffsetMs = Util.usToMs(adGroup.timeUs)
                if (fakeContentProgressOffsetMs == C.TIME_END_OF_SOURCE) {
                    fakeContentProgressOffsetMs = contentDurationMs
                }
            }
        }
        val adPreloadTimeoutMs = 3000L
        if (isWaitingForCurrentAdToLoad()) {
            handler.removeCallbacks(adLoadTimeoutRunnable)
            handler.postDelayed(adLoadTimeoutRunnable, adPreloadTimeoutMs)
        }
    }

    private fun handleAdLoadTimeout() {
        Log.d(ADS_LOADER_DEBUG, "handle ad load timeout")
        // IMA got stuck and didn't load an ad in time, so skip the entire group.
        handleAdGroupLoadError(IOException("Ad loading timed out"))
        maybeNotifyPendingAdLoadError()
    }

    private fun handleAdGroupLoadError(error: Exception) {
        val adGroupIndex: Int = getLoadingAdGroupIndex()
        Log.e(ADS_LOADER_DEBUG, "handleAdGroupLoadError, adGroupIndex:$adGroupIndex, exception:$error")
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
                Log.d(
                        TAG,
                        "Removing ad $i in ad group $adGroupIndex"
                )
            }
            adPlaybackState = adPlaybackState.withAdLoadError(adGroupIndex, i)
        }
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
        if (player == null) {
            return C.INDEX_UNSET
        }
        val playerPositionUs = Util.msToUs(
                getContentPeriodPositionMs(
                        player!!, timeline, period
                )
        )
        var adGroupIndex =
                adPlaybackState.getAdGroupIndexForPositionUs(playerPositionUs, Util.msToUs(contentDurationMs))
        if (adGroupIndex == C.INDEX_UNSET) {
            adGroupIndex = adPlaybackState.getAdGroupIndexAfterPositionUs(
                    playerPositionUs, Util.msToUs(contentDurationMs)
            )
        }
        return adGroupIndex
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
        if (sentContentComplete
                || contentDurationMs == C.TIME_UNSET || pendingContentPositionMs != C.TIME_UNSET
        ) {
            return
        }
        val contentPeriodPositionMs = getContentPeriodPositionMs(Assertions.checkNotNull(player), timeline, period)
        val pendingAdGroupIndex = adPlaybackState.getAdGroupIndexForPositionUs(
                Util.msToUs(contentPeriodPositionMs), Util.msToUs(contentDurationMs)
        )
        if (pendingAdGroupIndex != C.INDEX_UNSET && adPlaybackState.getAdGroup(pendingAdGroupIndex).timeUs != C.TIME_END_OF_SOURCE && adPlaybackState.getAdGroup(
                        pendingAdGroupIndex
                ).shouldPlayAdGroup()
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
                adPlaybackState = adPlaybackState.withSkippedAdGroup( /* adGroupIndex= */i)
            }
        }
        updateAdPlaybackState()
    }

    /**
     * Returns whether this instance is expecting the first ad in an the upcoming ad group to load
     * within the [preload timeout]
     */
    private fun isWaitingForFirstAdToPreload(): Boolean {
        val player = player ?: return false
        val adGroupIndex = getLoadingAdGroupIndex()
        if (adGroupIndex == C.INDEX_UNSET) {
            return false
        }
        val adGroup = adPlaybackState.getAdGroup(adGroupIndex)
        if (adGroup.count != C.LENGTH_UNSET && adGroup.count != 0 && adGroup.states[0] != AdPlaybackState.AD_STATE_UNAVAILABLE) {
            // An ad is available already.
            return false
        }
        val adGroupTimeMs = Util.usToMs(adGroup.timeUs)
        val contentPositionMs = getContentPeriodPositionMs(player, timeline, period)
        val timeUntilAdMs = adGroupTimeMs - contentPositionMs
        return timeUntilAdMs < adPreloadTimeoutMs
    }

    private fun updateProgress() {
        handler.removeCallbacks(checkPositionRunnable)
        handler.postDelayed(checkPositionRunnable, 1000)
    }

    private fun onProgress(currentPositionMilli: Long) {
        //        Log.d(ADS_LOADER_DEBUG, "onProgress, position:${currentPositionMilli / 1000}")
        currentPosition = currentPositionMilli
        if (player?.isPlayingAd == false) {
            insertAdsIfNecessary(currentPositionMilli)
        }
        //        resetAdGroupIfNecessary(currentPositionMilli)
    }

    private fun resetAdGroupIfNecessary(currentPositionMilli: Long) {
        if (player?.isPlayingAd == false && pendingResetAdGroup && adGroupIndexPendingReset != C.INDEX_UNSET) {
            val cuePointPosition = cuePointList[adGroupIndexPendingReset]
            if (currentPositionMilli - 60000 > cuePointPosition / 1000) {
                adPlaybackState = adPlaybackState.withResetAdGroup(adGroupIndexPendingReset)
                updateAdPlaybackState()
                Log.d(ADS_LOADER_DEBUG, "withResetAdGroup:adGroupIndex:$adGroupIndexPendingReset")
            }
        }
    }

    private fun insertAdsIfNecessary(currentPositionMilli: Long) {
        val loadingAdGroupIndex = getFirstAdIndexAfterPosition(currentPositionMilli)
        if (loadingAdGroupIndex == -1 || adInsertStateMap[loadingAdGroupIndex] == true) return

        player?.let {
            //            Log.d(TAG, "progress:${it.contentPosition / 1000}")
            val timeLeft = (cuePointList[loadingAdGroupIndex] / 1000000) - (currentPositionMilli / 1000).toInt()
            Log.d(
                    ADS_LOADER_DEBUG,
                    "insertAdsIfNecessary, loadingAdGroupIndex:$loadingAdGroupIndex, " +
                            "currentPosition:${currentPositionMilli / 1000},timeLeft:$timeLeft"
            )
            if (timeLeft in 0.0..AD_PRE_FETCH_TIME_SECOND) {
//                resetAdPlaybackStateIfPossible(loadingAdGroupIndex)
                insertAds(loadingAdGroupIndex)
                adInsertStateMap[loadingAdGroupIndex] = true
            }
        }
    }

    private fun resetAdPlaybackStateIfPossible(loadingAdGroupIndex: Int) {
        if (shouldResetAdGroupMap[loadingAdGroupIndex] == true) {
            adPlaybackState = adPlaybackState.withResetAdGroup(loadingAdGroupIndex)
            updateAdPlaybackState()
            Log.d(ADS_LOADER_DEBUG, "reset ad group:$loadingAdGroupIndex")
        }
    }

    private fun getFirstAdIndexAfterPosition(currentPositionMilli: Long): Int {
        return cuePointList.indexOfFirst { it >= currentPositionMilli * 1000 }
    }

    private fun insertAds(adGroupIndex: Int) {
        when (adPlayedTime) {
            0 -> {
                adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, 1)
                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 0, Uri.parse(AD_URL_4))
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 1, Uri.parse(AD_URL_5))
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 2, Uri.parse(AD_URL_1))
            }
            1 -> {
                adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, 1)
                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 0, Uri.parse(AD_URL_5))
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 1, Uri.parse(AD_URL_4))
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 2, Uri.parse(AD_URL_5))
            }
//            else -> {
//                adPlaybackState = adPlaybackState.withAdCount(adGroupIndex, 3)
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 0, Uri.parse(AD_URL_4))
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 1, Uri.parse(AD_URL_5))
//                adPlaybackState = adPlaybackState.withAvailableAdUri(adGroupIndex, 2, Uri.parse(AD_URL_1))
//            }
        }
        updateAdPlaybackState()
        Log.d(ADS_LOADER_DEBUG, "insert ads, adPlayedTime:$adPlayedTime")
    }

    private fun getAdGroupTimesUsForCuePoints(cuePoints: List<Float>): LongArray {
        if (cuePoints.isEmpty()) {
            return longArrayOf(0L)
        }
        val count = cuePoints.size
        val adGroupTimesUs = LongArray(count)
        var adGroupIndex = 0
        for (i in 0 until count) {
            val cuePoint = cuePoints[i].toDouble()
            if (cuePoint == -1.0) {
                adGroupTimesUs[count - 1] = C.TIME_END_OF_SOURCE
            } else {
                adGroupTimesUs[adGroupIndex++] = cuePoint.roundToLong()
            }
        }
        // Cue points may be out of order, so sort them.
        Arrays.sort(adGroupTimesUs, 0, adGroupIndex)
        return adGroupTimesUs
    }

}