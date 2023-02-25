package com.google.android.exoplayer2.demo

import android.net.Uri

val URI_CLEAR_CONTENT = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
val URI_WIDEVINE = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"
val URI_DRM_LICENSE = "https://proxy.uat.widevine.com/proxy?provider=widevine_test"

val AD_POSITION_SECONDS = listOf(3 * 60, 6 * 60, 9 * 60, 45 * 60, 60 * 60)
val AD_PRE_FETCH_TIME_SECOND = 5.0

val AD_URL_0 = "http://paella.adrise.tv/005296/4157521/v1105000139-854x480-HD-1132k.mp4"
val AD_URL_1 = "http://paella.adrise.tv/012243/4116546/v1025163650-854x480-HD-999k.mp4"
val AD_URL_2 = "http://paella.adrise.tv/002781/3860125/v0716204304-640x360-HD-800k.mp4"
val AD_URL_3 = "http://ark.tubi.video/v2/e72e209d1ec13b034178fc2ffca63247/12b27829-6d54-4628-abc8-353991bc38fd/854x480_900k.mp4"
val AD_URL_4 = "http://ark.tubi.video/v2/898d008ee449943df4083ac249971edb/1869f882-c316-48d7-8657-40d44df9b5b7/854x480_900k.mp4"
val AD_URL_5 = "http://ark.tubi.video/v2/7c2efed5603e4667d353fd4498e495fd/57527c13-2056-4db3-9e0b-5a88205ff85d/854x480_900k.mp4"

val AD_TAG_URI = Uri.parse("https://rainmaker.production-public.tubi.io/api/v2/rev/vod/ANDROID")
