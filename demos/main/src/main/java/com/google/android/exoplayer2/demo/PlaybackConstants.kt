package com.google.android.exoplayer2.demo

import android.net.Uri

val URI_CLEAR_CONTENT = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
val URI_CLEAR_CONTENT_TUBI = "https://manifest.production-public.tubi.io/f28ec2c0-d869-4b45-b751-ebd4645d7f8d/xg6zpqg5pp.m3u8?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjZG5fcHJlZml4IjoiaHR0cHM6Ly9ha2FtYWkyLnR1YmkudmlkZW8iLCJjb3VudHJ5IjoiVVMiLCJkZXZpY2VfaWQiOiIwMjJjMjBiOS0wNWYzLTRmNjQtYWNjMC0wNjdiNTFiY2U4YTMiLCJleHAiOjE2NzkzNzU3MDAsInBsYXRmb3JtIjoiQU5EUk9JRCIsInVzZXJfaWQiOjIzMjgyMzc2fQ.immBjlj6CvlTIuulfvBcY3MWWzqErDxbQBF8e0ihkbE&manifest=true"
val URI_WIDEVINE = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"
val URI_WIDEVINE_TUBI = "https://manifest.production-public.tubi.io/22318cc0-9db4-4129-955f-cf42a75f3a2e/1jvh0czfye.m3u8?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjZG5fcHJlZml4IjoiaHR0cHM6Ly9ha2FtYWkyLnR1YmkudmlkZW8iLCJjb3VudHJ5IjoiVVMiLCJkZXZpY2VfaWQiOiI5ZmExNTM3OC03MTI0LTQxYzctYmJkMy0xYjEyYmNmMDYxMDEiLCJleHAiOjE2ODU1ODkwMDAsInBsYXRmb3JtIjoiQU5EUk9JRCIsInVzZXJfaWQiOjIzMjgyMzc2fQ._LGPOgDkWgLCOqllLURGKZENuWRjSx9Ky2280OtoVfU&manifest=true"
val URI_DRM_LICENSE = "https://proxy.uat.widevine.com/proxy?provider=widevine_test"
val URI_DRM_LICENSE_TUBI = "https://license.adrise.tv/challenge?platform=android&type=widevine_psshv0&external_id=6EF6F638D7517D6BAA944A1D068B9030&drm_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbmFsb2dfb3V0IjoiZW5hYmxlZCIsImV4cCI6MTY4NTU4OTAwMCwiaGRjcCI6ImRpc2FibGVkIiwicHJfc2VjdXJpdHlfbGV2ZWwiOjIwMDAsInd2X3NlY3VyaXR5X2xldmVsIjoxfQ.xSiz-NGC6hpOk502a-iLtM50D8d7bjsgevIAHzbuCbA"

val AD_POSITION_SECONDS = listOf(3 * 60, 6 * 60, 9 * 60, 45 * 60, 60 * 60)
val AD_PRE_FETCH_TIME_SECOND = 5.0

val AD_URL_0 = "http://paella.adrise.tv/005296/4157521/v1105000139-854x480-HD-1132k.mp4"
val AD_URL_1 = "http://paella.adrise.tv/012243/4116546/v1025163650-854x480-HD-999k.mp4"
val AD_URL_2 = "http://paella.adrise.tv/002781/3860125/v0716204304-640x360-HD-800k.mp4"
val AD_URL_3 = "http://ark.tubi.video/v2/e72e209d1ec13b034178fc2ffca63247/12b27829-6d54-4628-abc8-353991bc38fd/854x480_900k.mp4"
val AD_URL_4 = "http://ark.tubi.video/v2/898d008ee449943df4083ac249971edb/1869f882-c316-48d7-8657-40d44df9b5b7/854x480_900k.mp4"
val AD_URL_5 = "http://ark.tubi.video/v2/7c2efed5603e4667d353fd4498e495fd/57527c13-2056-4db3-9e0b-5a88205ff85d/854x480_900k.mp4"

val AD_TAG_URI = Uri.parse("https://rainmaker.production-public.tubi.io/api/v2/rev/vod/ANDROID")
