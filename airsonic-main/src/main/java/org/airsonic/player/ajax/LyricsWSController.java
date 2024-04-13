/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.ajax;

import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestClient;

import java.io.StringReader;

import static org.airsonic.player.util.XMLUtil.createSAXBuilder;

/**
 * Provides services for retrieving song lyrics from chartlyrics.com.
 * <p/>
 * See http://www.chartlyrics.com/api.aspx for details.
 *
 */
@Controller
@MessageMapping("/lyrics")
public class LyricsWSController {

    private static final Logger LOG = LoggerFactory.getLogger(LyricsWSController.class);

    /**
     * Returns lyrics for the given song and artist.
     *
     * @return The lyrics, never <code>null</code> .
     */
    @MessageMapping("/get")
    @SendToUser(broadcast = false)
    public LyricsInfo getLyrics(LyricsGetRequest req) {
        return getLyrics(req.getArtist(), req.getSong());
    }

    public LyricsInfo getLyrics(String artist, String song) {
        LyricsInfo lyrics = new LyricsInfo();
        try {

            artist = StringUtil.urlEncode(artist);
            song = StringUtil.urlEncode(song);

            String url = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?artist=" + artist + "&song=" + song;
            String xml = executeGetRequest(url);
            lyrics = parseSearchResult(xml);

//        }
//        catch (RestClientException x) {
//            LOG.warn("Failed to get lyrics for song '{}'. Request failed: {}", song, x.toString());
//            if (x.getStatusCode() == 503) {
//                lyrics.setTryLater(true);
//            }
//        } catch (SocketException | ConnectTimeoutException x) {
//            LOG.warn("Failed to get lyrics for song '{}': {}", song, x.toString());
//            lyrics.setTryLater(true);
        } catch (Exception x) {
            LOG.warn("Failed to get lyrics for song '{}'.", song, x);
        }
        return lyrics;
    }

    private LyricsInfo parseSearchResult(String xml) throws Exception {
        SAXBuilder builder = createSAXBuilder();
        Document document = builder.build(new StringReader(xml));

        Element root = document.getRootElement();
        Namespace ns = root.getNamespace();

        String lyric = StringUtils.trimToNull(root.getChildText("Lyric", ns));
        String song = root.getChildText("LyricSong", ns);
        String artist = root.getChildText("LyricArtist", ns);

        return new LyricsInfo(lyric, artist, song);
    }

    RestClient restClient = RestClient.create();

    private String executeGetRequest(String url) {
        return restClient.get().uri(url).retrieve().body(String.class);
    }

    public static class LyricsGetRequest {
        private String artist;
        private String song;

        public LyricsGetRequest() {
        }

        public LyricsGetRequest(String artist, String song) {
            this.artist = artist;
            this.song = song;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getSong() {
            return song;
        }

        public void setSong(String song) {
            this.song = song;
        }
    }
}
