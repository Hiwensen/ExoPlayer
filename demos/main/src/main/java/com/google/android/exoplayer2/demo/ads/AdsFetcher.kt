package com.google.android.exoplayer2.demo.ads

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.exoplayer2.demo.AD_POSITION_SECONDS
import com.google.android.exoplayer2.demo.AD_PRE_FETCH_TIME_SECOND
import com.google.android.exoplayer2.demo.AD_URL_0
import com.google.android.exoplayer2.demo.AD_URL_1
import com.google.android.exoplayer2.demo.AD_URL_2
import com.google.android.exoplayer2.demo.AD_URL_3
import com.google.android.exoplayer2.demo.AD_URL_4
import com.google.android.exoplayer2.demo.TimeHelper
import com.google.android.exoplayer2.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdsFetcher(val lifecycleScope: LifecycleCoroutineScope, getContentPositionMs: () -> Long) {
    private val TAG = AdsFetcher::class.java.simpleName
    private val REQUEST_AD_FF_DELAY_MILLIS = 5000L
    private val cuePointListMs = AD_POSITION_SECONDS.map { it * TimeHelper.SECONDS_TO_MILLISECONDS }
    private var isFetchingAd = false
    private var hasAdBeenFetched = false
    private val fetchAdListenerList = mutableListOf<FetchAdListener>()
    private var havePendingAdRequestRunnable = false
    private var requestAdSeekStartPositionMilli = -1L
    private val handler = Handler(Looper.getMainLooper())

    private val requestAdRunnable = Runnable {
        havePendingAdRequestRunnable = false
        val currentProgressMs = getContentPositionMs()
        if (currentProgressMs > requestAdSeekStartPositionMilli) {
            for (i in cuePointListMs) {
                if (i in requestAdSeekStartPositionMilli..currentProgressMs) {
                    fetchAd(2, AdRequestType.FFWD)
                    break
                }
            }
        }
    }

    fun addFetchAdListener(fetchAdListener: FetchAdListener) {
        fetchAdListenerList.add(fetchAdListener)
    }

    fun removeListener(fetchAdListener: FetchAdListener) {
        fetchAdListenerList.remove(fetchAdListener)
    }

    fun fetchPreRoll(startPositionMs: Long) {
        val adGroupIndex = cuePointListMs.indexOfFirst { it >= startPositionMs }
        fetchAd(adGroupIndex, AdRequestType.REGULAR)
    }

    fun onSeek(oldPositionMilli: Long, newPositionMilli: Long) {
        Log.d(TAG,
                "onSeek, newPositionMin:${TimeHelper.milliToMinute(newPositionMilli)}, " +
                        "oldPositionMin:${TimeHelper.milliToMinute(oldPositionMilli)}")
        if (havePendingAdRequestRunnable) {
            // have pending ad request
            handler.removeCallbacks(requestAdRunnable)
            handler.postDelayed(requestAdRunnable, REQUEST_AD_FF_DELAY_MILLIS)
        } else {
            // no pending ad request
            requestAdSeekStartPositionMilli = oldPositionMilli
            handler.postDelayed(requestAdRunnable, REQUEST_AD_FF_DELAY_MILLIS)
            havePendingAdRequestRunnable = true
        }
    }

    fun resetAdStatus() {
        Log.d(TAG, "resetAdStatus")
        isFetchingAd = false
        hasAdBeenFetched = false
    }

    fun onProgress(currentProgressMs: Long, targetCuePointMs: Long) {
        if (isFetchingAd || hasAdBeenFetched) {
            Log.d(TAG,"onProgress, return, isFetchingAd:$isFetchingAd, hasAdBeenFetched:$hasAdBeenFetched")
            return
        }
        val timeLeftSecond = TimeHelper.milliToSecond(targetCuePointMs - currentProgressMs)
        if (timeLeftSecond in 0..AD_PRE_FETCH_TIME_SECOND) {
            Log.d(TAG,
                    "onProgress, going to fetch ad," +
                            "targetCuePoint min:${targetCuePointMs / 60000}, targetCuePointMs:$targetCuePointMs," +
                            "currentProgressMs:$currentProgressMs")
            fetchAd(cuePointListMs.indexOf(targetCuePointMs), AdRequestType.REGULAR)
        } else {
            Log.d(TAG,
                    "onProgress, timeLeftSecond:$timeLeftSecond, " +
                            "targetCuePoint min:${targetCuePointMs / 60000}, targetCuePointMs:$targetCuePointMs" +
                            "currentProgressMs:$currentProgressMs")
        }
    }

    private fun fetchAd(adGroupIndex: Int, adRequestType: AdRequestType) {
        Log.d(TAG, "fetchAd, adRequestType:$adRequestType")
        lifecycleScope.launch(Dispatchers.IO) {
            isFetchingAd = true
            delay(1500)

            withContext(Dispatchers.Main) {
                isFetchingAd = false
                hasAdBeenFetched = true
                when (adGroupIndex) {
                    0 -> {
                        fetchAdListenerList.forEach {
//                            it.onAdResponse(emptyList(), adRequestType)
                            it.onAdResponse(listOf(AD_URL_0, AD_URL_1), adRequestType)
                        }
                    }

                    1 -> {
                        fetchAdListenerList.forEach {
                            it.onAdResponse(emptyList(), adRequestType)
//                            it.onAdResponse(listOf(AD_URL_2), adRequestType)
                        }
                    }

                    2 -> {
                        fetchAdListenerList.forEach {
                            it.onAdResponse(emptyList(), adRequestType)
//                            it.onAdResponse(listOf(AD_URL_3, AD_URL_4), adRequestType)
                        }
                    }

                    else -> {
                        fetchAdListenerList.forEach {
                            it.onAdResponse(emptyList(), adRequestType)
//                            it.onAdResponse(listOf(AD_URL_3), adRequestType)
                        }
                    }
                }
            }
        }
    }
}

interface FetchAdListener {
    fun onAdResponse(adUrlList: List<String>, adRequestType: AdRequestType)
}