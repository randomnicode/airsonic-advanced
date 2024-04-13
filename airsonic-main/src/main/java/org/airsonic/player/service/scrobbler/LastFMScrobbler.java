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
package org.airsonic.player.service.scrobbler;

import org.airsonic.player.domain.MediaFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Provides services for "audioscrobbling" at www.last.fm.
 * <br/>
 * See https://www.last.fm/api/submissions
 */
public class LastFMScrobbler {

    private static final Logger LOG = LoggerFactory.getLogger(LastFMScrobbler.class);
    private static final int MAX_PENDING_REGISTRATION = 2000;

    private final LinkedBlockingQueue<RegistrationData> queue = new LinkedBlockingQueue<>(MAX_PENDING_REGISTRATION);

    private Thread thread = Thread.ofVirtual().name("LastFMScrobbler Registration").start(() -> {
        while (true) {
            RegistrationData registrationData = null;
            try {
                registrationData = queue.take();
                scrobble(registrationData);
            } catch (Exception x) {
                LOG.warn("Error in Last.fm registration", x);
            }
        }
    });

    /**
     * Registers the given media file at www.last.fm. This method returns immediately, the actual registration is done
     * by a separate thread.
     *
     * @param mediaFile  The media file to register.
     * @param username   last.fm username.
     * @param password   last.fm password.
     * @param submission Whether this is a submission or a now playing notification.
     * @param time       Event time, or {@code null} to use current time.
     */
    public void register(MediaFile mediaFile, String username, String password, boolean submission, Instant time) {
        RegistrationData registrationData = createRegistrationData(mediaFile, username, password, submission, time);
        if (registrationData == null) {
            return;
        }

        queue.offer(registrationData);
    }

    private RegistrationData createRegistrationData(MediaFile mediaFile, String username, String password, boolean submission, Instant time) {
        RegistrationData reg = new RegistrationData();
        reg.username = username;
        reg.password = password;
        reg.artist = mediaFile.getArtist();
        reg.album = mediaFile.getAlbumName();
        reg.title = mediaFile.getTitle();
        reg.duration = mediaFile.getDuration() == null ? 0 : (int) Math.round(mediaFile.getDuration());
        reg.time = time == null ? Instant.now() : time;
        reg.submission = submission;

        return reg;
    }

    /**
     * Scrobbles the given song data at last.fm, using the protocol defined at http://www.last.fm/api/submissions.
     *
     * @param registrationData Registration data for the song.
     */
    private void scrobble(RegistrationData registrationData) throws URISyntaxException, RestClientException {
        if (registrationData == null) {
            return;
        }

        String[] lines = authenticate(registrationData);
        if (lines == null) {
            return;
        }

        String sessionId = lines[1];
        String nowPlayingUrl = lines[2];
        String submissionUrl = lines[3];

        if (registrationData.submission) {
            lines = registerSubmission(registrationData, sessionId, submissionUrl);
        } else {
            lines = registerNowPlaying(registrationData, sessionId, nowPlayingUrl);
        }

        if (lines[0].startsWith("OK")) {
            LOG.info("Successfully registered {} for song '{}' for user {} at Last.fm: {}", (registrationData.submission ? "submission" : "now playing"), registrationData.title, registrationData.username, registrationData.time);
        } else {
            LOG.warn("Failed to scrobble song '{}' at Last.fm: {}", registrationData.title, lines[0]);
        }
    }

    /**
     * Returns the following lines if authentication succeeds:
     * <p/>
     * Line 0: Always "OK"
     * Line 1: Session ID, e.g., "17E61E13454CDD8B68E8D7DEEEDF6170"
     * Line 2: URL to use for now playing, e.g., "https://post.audioscrobbler.com:80/np_1.2"
     * Line 3: URL to use for submissions, e.g., "https://post2.audioscrobbler.com:80/protocol_1.2"
     * <p/>
     * If authentication fails, <code>null</code> is returned.
     */
    private String[] authenticate(RegistrationData registrationData) throws URISyntaxException, RestClientException {
        String clientId = "sub";
        String clientVersion = "0.1";
        long timestamp = System.currentTimeMillis() / 1000L;
        String authToken = calculateAuthenticationToken(registrationData.password, timestamp);
        URI uri = new URI("http",
                /* userInfo= */ null, "post.audioscrobbler.com", -1,
                "/",
                String.format("hs=true&p=1.2.1&c=%s&v=%s&u=%s&t=%s&a=%s",
                        clientId, clientVersion, registrationData.username,
                        timestamp, authToken),
                /* fragment= */ null);

        String response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        String[] lines = response.split("\\r?\\n");

        if (!lines[0].startsWith("OK")) {
            LOG.warn("Failed to authenticate with Last.fm. Response: {}", registrationData.title, lines[0]);
            return null;
        }

        return lines;
    }

    private String[] registerSubmission(RegistrationData registrationData, String sessionId, String url) throws RestClientException {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("s", sessionId);
        params.add("a[0]", registrationData.artist);
        params.add("t[0]", registrationData.title);
        params.add("i[0]", String.valueOf(registrationData.time.getEpochSecond()));
        params.add("o[0]", "P");
        params.add("r[0]", "");
        params.add("l[0]", String.valueOf(registrationData.duration));
        params.add("b[0]", registrationData.album);
        params.add("n[0]", "");
        params.add("m[0]", "");
        return executePostRequest(url, params);
    }

    private String[] registerNowPlaying(RegistrationData registrationData, String sessionId, String url) throws RestClientException {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("s", sessionId);
        params.add("a", registrationData.artist);
        params.add("t", registrationData.title);
        params.add("b", registrationData.album);
        params.add("l", String.valueOf(registrationData.duration));
        params.add("n", "");
        params.add("m", "");
        return executePostRequest(url, params);
    }

    private String calculateAuthenticationToken(String password, long timestamp) {
        return DigestUtils.md5Hex(DigestUtils.md5Hex(password) + timestamp);
    }

    RestClient restClient = RestClient.create();

    private String[] executePostRequest(String url, MultiValueMap<String, String> parameters) throws RestClientException {
        String response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(new HttpEntity<>(parameters))
                .retrieve()
                .body(String.class);
        return response.split("\\r?\\n");
    }

    private static class RegistrationData {
        private String username;
        private String password;
        private String artist;
        private String album;
        private String title;
        private int duration;
        private Instant time;
        public boolean submission;
    }

}
