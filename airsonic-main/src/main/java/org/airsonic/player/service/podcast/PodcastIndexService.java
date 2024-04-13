package org.airsonic.player.service.podcast;

import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class PodcastIndexService {
    private static final Logger LOG = LoggerFactory.getLogger(PodcastIndexService.class);

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private VersionService versionService;

    private static final String DEFAULT_URL = "https://api.podcastindex.org/api/1.0/search/byterm";

    RestClient restClient = RestClient.create();

    public List<PodcastIndexResponse.PodcastIndexResult> search(String username, String search) throws Exception {
        UserSettings userSettings = settingsService.getUserSettings(username);

        if (!userSettings.getPodcastIndexEnabled() || StringUtils.isBlank(search)) {
            return Collections.emptyList();
        }

        Map<App, UserCredential> creds = securityService.getDecodableCredsForApps(username, App.PODCASTINDEX);

        UserCredential cred = creds.get(App.PODCASTINDEX);
        if (cred != null) {
            String decoded = SecurityService.decodeCredentials(cred);
            if (decoded != null) {
                String now = String.valueOf(Instant.now().getEpochSecond());
                PodcastIndexResponse resp = null;
                try {
                    resp = restClient.post()
                            .uri(
                                UriComponentsBuilder
                                        .fromHttpUrl(StringUtils.isBlank(userSettings.getPodcastIndexUrl()) ? DEFAULT_URL : userSettings.getPodcastIndexUrl())
                                        .queryParam("q", search)
                                        .build().toUri())
                            .header("User-Agent", "Airsonic/" + versionService.getLocalVersion())
                            .header("X-Auth-Key", cred.getAppUsername())
                            .header("X-Auth-Date", now)
                            .header("Authorization", DigestUtils.sha1Hex(cred.getAppUsername() + decoded + now))
                            .contentType(MediaType.APPLICATION_JSON)
                            .retrieve().body(PodcastIndexResponse.class);
                } catch (Exception e) {
                    LOG.warn("Failed to execute PodcastIndex request: {}", search, e);
                }

                if (resp != null) {
                    return resp.getFeeds();
                }
            }
        }

        return Collections.emptyList();
    }

    // from https://podcastindex-org.github.io/docs-api/#get-/search/byterm
    public static class PodcastIndexResponse {
        private String status;
        private Integer count;
        private List<PodcastIndexResult> feeds;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public List<PodcastIndexResult> getFeeds() {
            return feeds;
        }

        public void setFeeds(List<PodcastIndexResult> feeds) {
            this.feeds = feeds;
        }

        public static class PodcastIndexResult {
            private Integer id;
            private String title;
            private String url;
            private String link;
            private String description;
            private String author;
            private String artwork;
            private Instant lastUpdateTime;
            private Integer type;
            private Integer dead;
            private Integer locked;
            private String language;
            private Map<Integer, String> categories;
            private String imageUrlHash;

            public Integer getId() {
                return id;
            }

            public void setId(Integer id) {
                this.id = id;
            }

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public String getLink() {
                return link;
            }

            public void setLink(String link) {
                this.link = link;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public String getAuthor() {
                return author;
            }

            public void setAuthor(String author) {
                this.author = author;
            }

            public String getArtwork() {
                return artwork;
            }

            public void setArtwork(String artwork) {
                this.artwork = artwork;
            }

            public Instant getLastUpdateTime() {
                return lastUpdateTime;
            }

            public void setLastUpdateTime(Instant lastUpdateTime) {
                this.lastUpdateTime = lastUpdateTime;
            }

            public Integer getType() {
                return type;
            }

            public void setType(Integer type) {
                this.type = type;
            }

            public Integer getDead() {
                return dead;
            }

            public void setDead(Integer dead) {
                this.dead = dead;
            }

            public Integer getLocked() {
                return locked;
            }

            public void setLocked(Integer locked) {
                this.locked = locked;
            }

            public String getLanguage() {
                return language;
            }

            public void setLanguage(String language) {
                this.language = language;
            }

            public Map<Integer, String> getCategories() {
                return categories;
            }

            public void setCategories(Map<Integer, String> categories) {
                this.categories = categories;
            }

            public String getImageUrlHash() {
                return imageUrlHash;
            }

            public void setImageUrlHash(String imageUrlHash) {
                this.imageUrlHash = imageUrlHash;
            }

        }
    }
}
