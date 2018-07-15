import YouTubePlayerRemoteBridge from "./io/YouTubePlayerRemoteBridge.js"

function YouTubePlayer(communicationConstants, communicationChannel) {
    const UNSTARTED = "UNSTARTED"
    const ENDED = "ENDED"
    const PLAYING = "PLAYING"
    const PAUSED = "PAUSED"
    const BUFFERING = "BUFFERING"
    const CUED = "CUED"

    const YouTubePlayerBridge = new YouTubePlayerRemoteBridge(communicationConstants, communicationChannel)

    let player
    let player2
    let ready = false
    let ready2 = false
    let lastState
    let lastState2
    let lastVideoId
    let lastVideoId2
    let offset

    function initialize() {        
        YouTubePlayerBridge.sendYouTubeIframeAPIReady()
        
        player = new YT.Player('youTubePlayerDOM', {

            height: '50%',
            width: '100%',

            events: {
                onReady: () => YouTubePlayerBridge.sendReady(),
                onStateChange: event  => sendPlayerStateChange(event.data),
                onPlaybackQualityChange: event => YouTubePlayerBridge.sendPlaybackQualityChange(event.data),
                onPlaybackRateChange: event => YouTubePlayerBridge.sendPlaybackRateChange(event.data),
                onError: error => YouTubePlayerBridge.sendError(error.data),
                onApiChange: () => YouTubePlayerBridge.sendApiChange()
            },
            playerVars: {
                autoplay: 0,
                autohide: 1,
                controls: 0,
                enablejsapi: 1,
                fs: 0,
                origin: 'https://www.youtube.com',
                rel: 0,
                showinfo: 0,
                iv_load_policy: 3
            }
        })

        player2 = new YT.Player('youTubePlayerDOM2', {

                    height: '50%',
                    width: '100%',

                    events: {
                        onReady: () => YouTubePlayerBridge.sendReady(),
                        onStateChange: event  => sendPlayerStateChange2(event.data),
                        onPlaybackQualityChange: event => YouTubePlayerBridge.sendPlaybackQualityChange(event.data),
                        onPlaybackRateChange: event => YouTubePlayerBridge.sendPlaybackRateChange(event.data),
                        onError: error => YouTubePlayerBridge.sendError(error.data),
                        onApiChange: () => YouTubePlayerBridge.sendApiChange()
                    },
                    playerVars: {
                        autoplay: 0,
                        autohide: 1,
                        controls: 0,
                        enablejsapi: 1,
                        fs: 0,
                        origin: 'https://www.youtube.com',
                        rel: 0,
                        showinfo: 0,
                        iv_load_policy: 3
                    }
                })
    }

    function restoreCommunication() {
        YouTubePlayerBridge.sendYouTubeIframeAPIReady()
        sendPlayerStateChange(lastState)
        YouTubePlayerBridge.sendVideoId(lastVideoId)
    }

    function sendPlayerStateChange2(playerState) {
        if (lastState2 == YT.PlayerState.BUFFERING && playerState == YT.PlayerState.PLAYING) {
            ready2 = true
             if (ready) {
                 player.playVideo()
             } else {
               player2.pauseVideo()
               player2.seekTo(offset)
             }
        }
//        if (lastState2 != playerState && lastState == YT.PlayerState.PLAYING && playerState == YT.PlayerState.PLAYING) {
//             player2.seekTo(player.getDuration() + offset)
//        }
        lastState2 = playerState
    }

    function sendPlayerStateChange(playerState) {
        if (lastState == YT.PlayerState.BUFFERING && playerState == YT.PlayerState.PLAYING) {
            ready = true
             if (ready2) {
                 player2.playVideo()
             } else {
                player.pauseVideo()
                player.seekTo(0)
             }
        }
//        if (lastState != playerState && playerState == YT.PlayerState.PLAYING && lastState2 == YT.PlayerState.PLAYING) {
//             player.seekTo(player2.getDuration() - offset)
//        }
        lastState = playerState

        let timerTaskId
        clearInterval(timerTaskId)

        switch (playerState) {
            case YT.PlayerState.UNSTARTED:
                sendStateChange(UNSTARTED)
                return

            case YT.PlayerState.ENDED:
                sendStateChange(ENDED)
                return

            case YT.PlayerState.PLAYING:
                sendStateChange(PLAYING)
                timerTaskId = setInterval( () => YouTubePlayerBridge.sendVideoCurrentTime( player.getCurrentTime() ), 100 )
                sendVideoData(player)
                return

            case YT.PlayerState.PAUSED:
                sendStateChange(PAUSED)
                return

            case YT.PlayerState.BUFFERING:
                sendStateChange(BUFFERING)
                return

            case YT.PlayerState.CUED:
                sendStateChange(CUED)
                return
        }

        function sendVideoData(player) {
            const videoDuration = player.getDuration()
            YouTubePlayerBridge.sendVideoDuration(videoDuration)
        }

        function sendStateChange(newState) {
            YouTubePlayerBridge.sendStateChange(newState)
        }
    }

    // JAVA to WEB functions
    function seekTo(startSeconds) {        	
        player.seekTo(startSeconds, true)
        player2.seekTo(startSeconds + offset, true)
    }

    function pauseVideo() {
        player.pauseVideo()
        player2.pauseVideo()
    }

    function playVideo() {
        player.playVideo()
        player2.playVideo()
    }

    function loadVideo(videoId, startSeconds) {
        lastVideoId = videoId

        player.loadVideoById(videoId, startSeconds)
        YouTubePlayerBridge.sendVideoId(videoId)
    }

    function loadVideos(videoId, startSeconds, videoId2, startSeconds2) {
            lastVideoId = videoId
            lastVideoId2 = videoId2

            offset = startSeconds2

            player.cueVideoById(videoId, startSeconds)
            player2.cueVideoById(videoId2, startSeconds2)
            YouTubePlayerBridge.sendVideoId(videoId)
    }

    function cueVideo(videoId, startSeconds) {
        lastVideoId = videoId

        player.cueVideoById(videoId, startSeconds)
        YouTubePlayerBridge.sendVideoId(videoId)
    }

    function mute() {
        player.mute()
        player2.mute()
    }

    function unMute() {
        player.unMute()
        player2.unMute()
    }

    function setVolume(volumePercent) {
        player.setVolume(volumePercent)
        player2.setVolume(volumePercent)
    }

    function getActions() {
        return actions
    }

    const actions = { seekTo, pauseVideo, playVideo, loadVideo, cueVideo, mute, unMute, setVolume }
    
    return {
        initialize,
        restoreCommunication,
        getActions
    }
}

export default YouTubePlayer