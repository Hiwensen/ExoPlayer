package com.google.android.exoplayer2.demo

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.StyledPlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.ui.TimeBar
import com.google.android.exoplayer2.demo.ads.ADS_LOADER_DEBUG
import com.google.android.exoplayer2.demo.ads.AdRequestType
import com.google.android.exoplayer2.demo.ads.AdsFetcher
import com.google.android.exoplayer2.demo.ads.ExoAdsLoader
import com.google.android.exoplayer2.demo.ads.ExoAdsLoaderV2
import com.google.android.exoplayer2.demo.ads.FetchAdListener
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.google.common.collect.ImmutableList

class PlaygroundActivity : AppCompatActivity(), AdsLoader.Provider {
    private val TAG = PlaygroundActivity::class.java.simpleName
    private lateinit var playerView: StyledPlayerView
    private lateinit var player: ExoPlayer
    private lateinit var clientSideAdsLoader: ExoAdsLoader
    private var adsFetcher: AdsFetcher = AdsFetcher(lifecycleScope) { player.contentPosition }
    private val timeBarListener = TimeBarListener()
    private var targetCuePointMs = 0L
    private val resumePositionMs = 0L
    private val playerListener = PlayerListener()
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = Runnable {
        onContentProgress(player.contentPosition)
    }
    private val adFetchAdListener = object : FetchAdListener {
        override fun onAdResponse(adUrlList: List<String>, adRequestType: AdRequestType) {
            onReceiveAdResponse(adUrlList, adRequestType)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exo_player)
        playerView = findViewById(R.id.player_view)
        val playerControlView = playerView.findViewById<StyledPlayerControlView>(com.google.android.exoplayer2.ui.R.id
                .exo_controller)
        val timeBar = playerControlView.findViewById<DefaultTimeBar>(com.google.android.exoplayer2.ui.R.id.exo_progress)
        timeBar.addListener(timeBarListener)
        findViewById<Button>(R.id.play_drm_content).setOnClickListener {
            playDRM()
        }

        findViewById<Button>(R.id.play_clear_content).setOnClickListener {
            playClearContent()
        }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer()
            playerView.onResume()
            initAdsFetcher()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT <= 23) {
            initializePlayer()
            playerView.onResume()
            initAdsFetcher()
        }
    }

    override fun onStop() {
        super.onStop()
        player.removeListener(playerListener)
        adsFetcher.removeListener(adFetchAdListener)
        handler.removeCallbacksAndMessages(null)
        player.stop()
        player.release()
    }

    override fun getAdsLoader(adsConfiguration: MediaItem.AdsConfiguration): AdsLoader {
        return clientSideAdsLoader
    }

    private fun initializePlayer() {
        val mediaSourceFactory =
                DefaultMediaSourceFactory(applicationContext)
                        .experimentalUseProgressiveMediaSourceForSubtitles(true)
                        .setLocalAdInsertionComponents(this, playerView)

        player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory)
                .build()
        player.addListener(playerListener)
        playerView.player = player

        clientSideAdsLoader = ExoAdsLoaderV2()
        clientSideAdsLoader.setPlayer(player)
        clientSideAdsLoader.setCuePoints(AD_POSITION_SECONDS, resumePositionMs)
    }

    private fun initAdsFetcher() {
        adsFetcher.addFetchAdListener(adFetchAdListener)
    }

    private fun playDRM() {
        val drmConfiguration = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(URI_DRM_LICENSE_TUBI)
                .setMultiSession(true)
                .setPlayClearContentWithoutKey(true)
//                .setLicenseRequestHeaders(mapOf("customData" to ""))
                .build()
        val mediaItem = MediaItem.Builder().setUri(URI_WIDEVINE_TUBI)
                .setDrmConfiguration(drmConfiguration)
                .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(AD_TAG_URI).build())
                .build()
        player.setMediaItem(mediaItem)
        player.seekTo(resumePositionMs)
        player.prepare()
        player.playWhenReady = true
        targetCuePointMs = resumePositionMs
        adsFetcher.fetchPreRoll(resumePositionMs)
        handler.post(updateProgressRunnable)
    }

    private fun playClearContent() {
        val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(SUBTITLE_URL))
            .setMimeType(MimeTypes.APPLICATION_SUBRIP) // The correct MIME type (required).
            .setLanguage(SUBTITLE_LANGUAGE) // The subtitle language (optional).
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT) // Selection flags for the track (optional).
            .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .build()

        val mediaItem = MediaItem.Builder().setUri(URI_CLEAR_CONTENT_TUBI)
                .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(com.google.android.exoplayer2.demo.ads.AD_TAG_URI).build())
                .setSubtitleConfigurations(ImmutableList.of(subtitle))
                .build()

//        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)
//                .experimentalUseProgressiveMediaSourceForSubtitles(true)
//        val contentMediaSource = defaultMediaSourceFactory.createMediaSource(mediaItem)

        // Plays the video with the side loaded subtitle.
//        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
//        val subtitleSource = SingleSampleMediaSource.Factory(httpDataSourceFactory)
//            .createMediaSource(subtitle, -1)
//        val mergingMediaSource = MergingMediaSource(contentMediaSource, subtitleSource)

        player.setMediaItem(mediaItem)
//        player.setMediaSource(mergingMediaSource)
        player.prepare()
        player.playWhenReady = true
        adsFetcher.fetchPreRoll(resumePositionMs)
        handler.post(updateProgressRunnable)
    }

    private fun onContentProgress(contentPositionMs: Long) {
        if (player.playWhenReady) {
            adsFetcher.onProgress(contentPositionMs, targetCuePointMs)
        }
        handler.removeCallbacks(updateProgressRunnable)
        handler.postDelayed(updateProgressRunnable, 1000)
    }

    private fun onReceiveAdResponse(adUrlList: List<String>, adRequestType: AdRequestType) {
        val targetCuePointMilli = if (adRequestType == AdRequestType.REGULAR) {
            targetCuePointMs
        } else {
            player.contentPosition
        }
        clientSideAdsLoader.onReceiveAds(adUrlList,
                vastAdDurationList = emptyList(),
                adRequestType,
                TimeHelper.milliToMicro(targetCuePointMilli))
        if (adUrlList.isEmpty()) {
            updateTargetCuePoint(TimeHelper.milliToSecond(targetCuePointMs))
        }
    }

    inner class PlayerListener : Player.Listener {
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            Log.d("adDebug", "onPositionDiscontinuity, reason:$reason, " +
                    "0-transition, 1-seek, 2-seekAdjust, 3-skip, 4-remove, 5-internal")
            Log.d("adsLoaderDebug_player", "Player callback, onPositionDiscontinuity, reason:$reason, " +
                    "0-transition, 1-seek, 2-seekAdjust, 3-skip, 4-remove, 5-internal")
            when (reason) {
                Player.DISCONTINUITY_REASON_SEEK -> {
                    adsFetcher.resetAdStatus()
                    if (TimeHelper.milliToSecond(newPosition.positionMs) != TimeHelper.milliToSecond(resumePositionMs)) {
                        // Avoid trigger duplicate onSeek callback caused by resume position not 0
                        adsFetcher.onSeek(oldPosition.positionMs, newPosition.positionMs)
                        updateTargetCuePoint(TimeHelper.milliToSecond(player.contentPosition))
                    }
                }

                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                    if (!player.isPlayingAd) {
                        // Switch from ad to video content
                        updateTargetCuePoint(TimeHelper.milliToSecond(targetCuePointMs))
                    }
                }

                else -> {
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d("adDebug", "onPlaybackStateChanged, playbackState:$playbackState, 1-idle, 2-buffer, 3-ready, 4-end")
        }
    }

    private fun updateTargetCuePoint(positionSecond: Long) {
        val cuePointList = AD_POSITION_SECONDS
        val cuePointNewMs = TimeHelper.secondToMillisecs(cuePointList.firstOrNull { it > positionSecond }
                ?: Long.MAX_VALUE)
        if (cuePointNewMs != targetCuePointMs) {
            targetCuePointMs = cuePointNewMs
            adsFetcher.resetAdStatus()
        }
        Log.d(TAG, "updateTargetCuePoint:${this.targetCuePointMs}")
    }

    inner class TimeBarListener : TimeBar.OnScrubListener {
        override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit

        override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit

        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            Log.d(ADS_LOADER_DEBUG, "onScrubStop, position seconds:${position / 1000}")
            clientSideAdsLoader.onSeekTo(position)
        }

    }

}