<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="utf-8" %>

<html>
<head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <script type="text/javascript" src="<c:url value='/script/mediaelement-7.0.3/mediaelement-and-player.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/speed/speed.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/speed/speed-i18n.js'/>"></script>
    <script src="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/quality/quality.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/quality/quality-i18n.js'/>"></script>
    <script src="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/chromecast/chromecast.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/chromecast/chromecast-i18n.js'/>"></script>
    <link rel="stylesheet" href="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/speed/speed.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/quality/quality.min.css'/>">
    <link rel="stylesheet" href="<c:url value='/script/mediaelement-7.0.3/plugins-4.0.0/chromecast/chromecast.min.css'/>">

    <style type="text/css">
        html, body {
            height: 100%;
        }

        .ui-slider .ui-slider-handle {
            width: 11px;
            height: 11px;
            cursor: pointer;
        }
        .ui-slider a {
            outline:none;
        }
        .ui-slider {
            cursor: pointer;
        }

        .vid-flex-container {
            width:100%;
            height:100%;
            display:flex;
            flex-direction:column;
            justify-content:flex-end;
        }

        .vid-div {
            flex:1 1 auto;
        }

        #videoPlayer {
            height: 100%;
            width: 100%;
        }

        .vid-title {
            padding-top:1em;
            padding-bottom:0.5em;
        }

        #share, #download, #starImage {
            cursor: pointer;
            margin: 0 0.5em 0 0.5em;
        }

        .back {
            padding-right:2em;
            margin-top:1em;
            margin-bottom:5em
        }

    </style>

    <script type="text/javascript" language="javascript">
        function toggleStar(mediaFileId, imageId) {
            if ($(imageId).attr("src").indexOf("<spring:theme code='ratingOnImage'/>") != -1) {
                $(imageId).attr("src", "<spring:theme code="ratingOffImage"/>");
                top.StompClient.send("/app/rate/mediafile/unstar", JSON.stringify([mediaFileId]));
            }
            else if ($(imageId).attr("src").indexOf("<spring:theme code='ratingOffImage'/>") != -1) {
                $(imageId).attr("src", "<spring:theme code='ratingOnImage'/>");
                top.StompClient.send("/app/rate/mediafile/star", JSON.stringify([mediaFileId]));
            }
        }

        var videoModel = {
          duration: ${empty model.video.duration ? -1 : model.video.duration},
          videoTitle: "${model.video.title}",
          streamable: ${model.streamable},
          castable: ${model.castable},
          streamUrls: ${sub:toJson(model.streamUrls)},
          remoteCoverArtUrl: "${model.remoteCoverArtUrl}",
          remoteStreamUrl: "${model.remoteStreamUrl}",
          remoteCaptionsListUrl: "${model.remoteCaptionsUrl}",
          remoteCaptions: [],
          currentUrl: "${model.defaultBitRate}",
          videoId: "${model.video.id}",
          position: ${model.position},
          autoBookmark: ${model.autoBookmark},
          videoBookmarkFrequency: ${model.videoBookmarkFrequency},
          contentType: "${model.contentType}",
          hideShare: ${model.user.shareRole ? 'true': 'false'},
          hideDownload: ${model.user.downloadRole ? 'true': 'false'}
        }

        function setBookmark() {
            if (videoModel.autoBookmark) {
                var position = Math.round(this.videoPlayer.currentTime);
                if ((videoModel.position != (position * 1000)) && (position % videoModel.videoBookmarkFrequency == 0)) {
                    videoModel.position = position * 1000;
                    top.StompClient.send("/app/bookmarks/set", JSON.stringify({positionMillis: videoModel.position, comment: "Played on Web Video Player", mediaFileId: videoModel.videoId}));
                }
            }
        }

        function init() {
            $.get(videoModel.remoteCaptionsListUrl, data => {
                videoModel.remoteCaptions = data;
            });
            var vpr = this;
            this.videoPlayer = new MediaElementPlayer("videoPlayer", {
                iconSprite: "<c:url value='/script/mediaelement-7.0.3/mejs-controls.svg'/>",
                alwaysShowControls: true,
                enableKeyboard: true,
                useDefaultControls: true,
                enableTracks: true,
                castAppID: "4FBFE470",
                features: ["speed", "quality", "chromecast"],
                hls: {
                    path: "<c:url value='/script/mediaelement-7.0.3/renderers/hls-1.5.8/hls.min.js'/>"
                },
                dash: {
                    path: "<c:url value='/script/mediaelement-7.0.3/renderers/dash-4.7.4/dash.all.min.js'/>"
                },
                iconPathQuality: false,
                defaultSpeed: "1.00",
                speeds: ["8.00", "2.00", "1.50", "1.25", "1.00", "0.75", "0.5"],
                defaultQuality: "${model.defaultBitRate}",
                videoWidth: "100%",
                videoHeight: "100%",
                qualityChangeCallback: function(media, node, newQuality, url, event) {
                    videoModel.currentUrl = newQuality;
                },
                success(mediaElement, originalNode, instance) {
                    if (videoModel.streamable) {
                        // "hack" html5 renderer and reinitialize speed
                        instance.media.rendererName = "html5";
                        instance.buildspeed(instance, instance.getElement(instance.controls), instance.getElement(instance.layers), instance.media);
                    }
                    <c:if test="${model.user.shareRole}">
                    $("#share").on('click', () => location.href = "createShare?id=" + videoModel.videoId);
                    </c:if>
                    <c:if test="${model.user.downloadRole}">
                    $("#download").on('click', () => location.href = "download?id=" + videoModel.videoId);
                    </c:if>
                    // add dimensions to playing vid
                    instance.setSrc($('#videoPlayer source[data-quality="${model.defaultBitRate}"]')[0].src);
                    // set position
                    instance.setCurrentTime(videoModel.position/1000);

                    // Once playback reaches the end, go to the next song, if any.
                    $(mediaElement).on("ended", () => vpr.setBookmark());
                    $(mediaElement).on("timeupdate", () => vpr.setBookmark());
                    $(mediaElement).on("seeked", () => vpr.setBookmark());
                    $(mediaElement).on("paused", () => vpr.setBookmark());
                }
            });
            // add dimensions to play at
            $('#videoPlayer source').each((s, d) => d.src = d.src + "&size=" + Math.floor($('.mejs__container').width()) + "x" + Math.floor($('.mejs__container').height()));
        }
    </script>
</head>

<body class="mainframe bgcolor1" style="padding-bottom:0.5em" onload="init();">

<div class="vid-flex-container">
    <div class="vid-div">
		<video id="videoPlayer">
		  <c:forEach items="${model.streamUrls}" var="streamUrl">
		    <source src="${streamUrl.value}" data-quality="${streamUrl.key}" type="${model.streamType}">
		  </c:forEach>
		  <c:forEach items="${model.captions}" var="caption">
		    <c:url value="/captions" var="suburl">
		        <c:param name="id" value="${model.video.id}" />
		        <c:param name="captionId" value="${caption.identifier}" />
		    </c:url>
		    <track src="${caption.url}" label="${caption.identifier} (${caption.language})" srclang="${caption.language}" kind="subtitles">
		  </c:forEach>
		</video>
	</div>

	<h1 class="vid-title">
	    <span style="vertical-align:middle">${fn:escapeXml(model.video.title)}</span>
	</h1>

	<div>
	  <c:if test="${model.user.shareRole}">
	    <img id="share" src="<spring:theme code='shareImage'/>" title="<fmt:message key='main.more.share'/>" alt="<fmt:message key='main.more.share'/>">
	  </c:if>
	  <c:if test="${model.user.downloadRole}">
	    <img id="download" src="<spring:theme code='downloadImage'/>" title="<fmt:message key='common.download'/>" alt="<fmt:message key='common.download'/>">
	  </c:if>
	  <img id="starImage" src="<spring:theme code='${not empty model.video.starredDate ? \'ratingOnImage\' : \'ratingOffImage\'}'/>"
	         onclick="toggleStar(${model.video.id}, '#starImage'); return false;" title="<fmt:message key='playlist.more.star'/> / <fmt:message key='playlist.more.unstar'/>" alt="<fmt:message key='playlist.more.star'/> / <fmt:message key='playlist.more.unstar'/>">
	</div>

	<sub:url value="main" var="backUrl"><sub:param name="id" value="${model.video.id}"/></sub:url>
	<div class="back"><a href="${backUrl}"><fmt:message key="common.back"/></a></div>
</div>

</body>
</html>
