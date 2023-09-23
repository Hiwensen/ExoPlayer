package com.google.android.exoplayer2.demo

import android.net.Uri

val URI_CLEAR_CONTENT = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
val URI_CLEAR_CONTENT_TUBI =
        "https://manifest.production-public.tubi.io/bf129953-8795-468d-9e27-a1144d155678/le6hzujacs.m3u8?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjZG5fcHJlZml4IjoiaHR0cHM6Ly9ha2FtYWkyLnR1YmkudmlkZW8iLCJjb3VudHJ5IjoiVVMiLCJkZXZpY2VfaWQiOiI1ZTAzZjcwYi1kOWM4LTQ5MDEtOGNlMi1lNmRiMzA2M2RlNTEiLCJleHAiOjE2OTQ2Nzc4MDAsInBsYXRmb3JtIjoiQU5EUk9JRCIsInVzZXJfaWQiOjIzMjgyMzc2fQ.yFkjdUyBqaIqxManlzP0HJ9UT_Zx4zcerJEkrFSxx0A&manifest=true"
val URI_WIDEVINE = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd"
val URI_WIDEVINE_TUBI =
        "https://manifest.production-public.tubi.io/3ab3810b-e3e5-4c5c-8b05-5418a55921f7/0h25zo9k6f.m3u8?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJjZG5fcHJlZml4IjoiaHR0cHM6Ly9ha2FtYWkyLnR1YmkudmlkZW8iLCJjb3VudHJ5IjoiVVMiLCJkZXZpY2VfaWQiOiI2MzhlNzhiNy1kNmNmLTRmNTQtYTg2Yi1kZWM1NTNmYmY4ODAiLCJleHAiOjE2OTU2ODkxMDAsInBsYXRmb3JtIjoiQU5EUk9JRCIsInVzZXJfaWQiOjIzMjgyMzc2fQ.ntjV_UWfqqu-VdyDcK_gpSpBzP9F50L_ULDKokal8fE&manifest=true"

val URI_DRM_LICENSE = "https://proxy.uat.widevine.com/proxy?provider=widevine_test"
val URI_DRM_LICENSE_TUBI =
        "https://license.adrise.tv/challenge?platform=android&type=widevine_psshv0&external_id=A73E5D305357B07E675DEC452FF9F49E&drm_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhbmFsb2dfb3V0IjoiZW5hYmxlZCIsImV4cCI6MTY5NTY4OTEwMCwiaGRjcCI6ImRpc2FibGVkIiwicHJfc2VjdXJpdHlfbGV2ZWwiOjIwMDAsInd2X3NlY3VyaXR5X2xldmVsIjoxfQ.jQSHNnOko9_XFZFCgAXYEMDSVB5En3sVkkeC9PZAF_4"

val AD_POSITION_SECONDS = listOf(0L, 495L, 723L, 998L, 1267L, 1432L, 1615L, 1940L, 2259L, 2769L, 2971L, 3373L, 3604L, 3809L, 4124L, 4505L, 4747L, 4938L, 5211L, 5711L, 6042L, 6576L, 6877L)
val AD_PRE_FETCH_TIME_SECOND = 5

val AD_URL_0 = "http://paella.adrise.tv/005296/4157521/v1105000139-854x480-HD-1132k.mp4"
val AD_URL_1 = "http://paella.adrise.tv/012243/4116546/v1025163650-854x480-HD-999k.mp4"
val AD_URL_2 = "http://paella.adrise.tv/002781/3860125/v0716204304-640x360-HD-800k.mp4"
val AD_URL_3 = "http://ark.tubi.video/v2/e72e209d1ec13b034178fc2ffca63247/12b27829-6d54-4628-abc8-353991bc38fd/854x480_900k.mp4"
val AD_URL_4 = "http://ark.tubi.video/v2/898d008ee449943df4083ac249971edb/1869f882-c316-48d7-8657-40d44df9b5b7/854x480_900k.mp4"
val AD_URL_5 = "http://ark.tubi.video/v2/7c2efed5603e4667d353fd4498e495fd/57527c13-2056-4db3-9e0b-5a88205ff85d/854x480_900k.mp4"

val AD_TAG_URI = Uri.parse("https://rainmaker.production-public.tubi.io/api/v2/rev/vod/ANDROID")
