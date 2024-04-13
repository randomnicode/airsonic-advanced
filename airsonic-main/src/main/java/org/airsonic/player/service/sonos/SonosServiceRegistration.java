/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2015 (C) Sindre Mehus
 */

package org.airsonic.player.service.sonos;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * <p>Registration with Sonos controller. They are 2 types of registration they are still supported by Sonos.
 * The ANONYMOUS and APPLICATION_LINK. The third USER_ID, must still work but will be not supported.</p>
 * <p></p>
 * @author Sindre Mehus
 * @author Nacrylic
 * @version $Id$
 */
@Component
public class SonosServiceRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(SonosServiceRegistration.class);

    /**
     * The type of Authentication fo Sonos, the old want USER_ID, is will be not supported. We must use the
     * Anonymous or AppLink. The USER_ID
     */
    public enum AuthenticationType {
        //@Deprecated use ANONYMOUS or APPLICATION_LINK
        @Deprecated
        DEVICE_LINK("DeviceLink"),
        @Deprecated
        USER_ID("UserId"),

        ANONYMOUS("Anonymous"),
        APPLICATION_LINK("AppLink");

        private String fieldValue;

        AuthenticationType(String fieldValue) {
            this.fieldValue = fieldValue;
        }

        public String getFieldValue() {
            return fieldValue;
        }
    }

    /**
     * Enable or disable Sonos registration
     *
     * @param airsonicBaseUrl must be the ip address, not the name
     * @param sonosControllerIp must be a ip too
     * @param enabled true for enable or false to disable
     * @param sonosServiceName the name of service you will see on Sonos service list
     * @param sonosServiceId the ID, the free id is : 240-253 or 255
     * @throws IOException if some io problem
     */
    public boolean setEnabled(String airsonicBaseUrl, String sonosControllerIp, boolean enabled, String sonosServiceName,
                           int sonosServiceId, AuthenticationType authenticationType) throws IOException {
        String localUrl = airsonicBaseUrl + "ws/Sonos";
        String controllerUrl = String.format("http://%s:1400/customsd", sonosControllerIp);

        LOG.info("Setting Sonos music service enabled={}, using Sonos controller IP={}, SID={}, Airsonic url={}", enabled, sonosControllerIp, sonosServiceId, localUrl);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("sid", String.valueOf(sonosServiceId));

        // Need the csrf token on each request
        String csrfToken = retrieveCsrfToken(controllerUrl);
        if (csrfToken != null) {
            params.add("csrfToken", csrfToken);
        }

        if (enabled) {
            params.add("name", sonosServiceName);
            params.add("uri", localUrl);
            params.add("secureUri", localUrl);
            params.add("pollInterval", "1200");
            params.add("containerType", "MService");
            params.add("caps", "search");
            params.add("caps", "trFavorites");
            params.add("caps", "alFavorites");
            params.add("caps", "ucPlaylists");
            params.add("caps", "extendedMD");

            // If you change airsonic/airsonic-main/src/main/webapp/sonos/presentationMap.xml
            // Change the presentationMapVersion @see https://musicpartners.sonos.com/node/134
            params.add("presentationMapVersion", "1");
            params.add("presentationMapUri", airsonicBaseUrl + "sonos/presentationMap.xml");

            // Don't forget to change `stringsVersion` if you change the text in airsonic/airsonic-main/src/main/webapp/sonos/strings.xml
            // Change the stringsVersion @see https://musicpartners.sonos.com/node/134
            params.add("stringsVersion", "11");
            params.add("stringsUri", airsonicBaseUrl + "sonos/strings.xml");
            params.add("authType", authenticationType.getFieldValue());

        } else {

            // To disable a Sonos device, just name it with an empty value.
            params.add("name", null);
        }

        return execute(controllerUrl, params);
    }

    RestClient restClient = RestClient.create();

    private boolean execute(String url, MultiValueMap<String, String> parameters) throws IOException {
        String result = restClient.post()
                .uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(new HttpEntity<>(parameters))
                .retrieve()
                .body(String.class);
        LOG.info("Sonos controller returned: {}", result);

        return result.contains("Success");
    }

    private String retrieveCsrfToken(String controllerUrl) throws IOException {
        Document doc = Jsoup.connect(controllerUrl).get();
        Element element = doc.selectFirst("input[name='csrfToken']");

        if (element != null) {
            return element.attributes().get("value");
        }

        return null;
    }
}
