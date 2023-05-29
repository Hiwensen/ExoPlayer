package com.google.android.exoplayer2.demo

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.demo.ads.ADS_LOADER_DEBUG
import com.google.android.exoplayer2.demo.ads.ExoAdsLoader
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ads.AdsLoader
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.StyledPlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.ui.TimeBar

class PlaygroundActivity : AppCompatActivity(), AdsLoader.Provider {
    private lateinit var playerView: StyledPlayerView
    private lateinit var player: ExoPlayer
    private lateinit var clientSideAdsLoader: ExoAdsLoader
    private val timeBarListener = TimeBarListener()

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
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT <= 23) {
            initializePlayer()
            playerView.onResume()
        }
    }

    override fun onStop() {
        super.onStop()
        player.stop()
        player.release()
    }

    override fun getAdsLoader(adsConfiguration: MediaItem.AdsConfiguration): AdsLoader? {
        return clientSideAdsLoader
    }

    private fun initializePlayer() {
        val mediaSourceFactory =
                DefaultMediaSourceFactory(applicationContext).setLocalAdInsertionComponents(this, playerView)

        player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory)
                .build()
        player.playWhenReady = true
        playerView.player = player

        clientSideAdsLoader = ExoAdsLoader(applicationContext)
        clientSideAdsLoader.setPlayer(player)
    }

    private fun playDRM() {
        val drmConfiguration = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(URI_DRM_LICENSE_TUBI)
                .setMultiSession(true)
                .setPlayClearContentWithoutKey(true)
                .setLicenseRequestHeaders(mapOf("customData" to ""))
                .build()
        val mediaItem = MediaItem.Builder().setUri(URI_WIDEVINE_TUBI)
                .setDrmConfiguration(drmConfiguration)
                .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(AD_TAG_URI).build())
                .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    private fun playClearContent() {
        val mediaItem = MediaItem.Builder().setUri(URI_CLEAR_CONTENT)
                .setAdsConfiguration(MediaItem.AdsConfiguration.Builder(AD_TAG_URI).build())
                .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
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