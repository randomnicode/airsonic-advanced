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
package org.airsonic.player.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.ModelAndView;

/**
 * A proxy for external HTTP requests.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/proxy")
public class ProxyController {

    RestClient restClient = RestClient.create();

    @GetMapping
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String url = ServletRequestUtils.getRequiredStringParameter(request, "url");

        restClient.get().uri(url).exchange((req, res) -> {
            if (res.getStatusCode().equals(HttpStatus.OK)) {
                IOUtils.copy(res.getBody(), response.getOutputStream());
                return true;
            } else {
                response.sendError(res.getStatusCode().value());
                return false;
            }
        });

        return null;
    }
}
