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

import com.sonos.services._1.RefreshAuthTokenResponse;

/**
 * @author Sindre Mehus
 * @version $Id$
 */
public class SonosSoapFault extends RuntimeException {

    private final String faultCode;

    // Must match values in strings.xml
    private final int sonosError;

    protected SonosSoapFault(String message, String faultCode, int sonosError) {
        super(message);
        this.faultCode = faultCode;
        this.sonosError = sonosError;
    }

    public String getFaultCode() {
        return faultCode;
    }

    public int getSonosError() {
        return sonosError;
    }

    public static class LoginInvalid extends SonosSoapFault {

        public LoginInvalid() {
            super("Login invalid", "Client.LoginInvalid", 0);
        }
    }

    public static class LoginUnauthorized extends SonosSoapFault {

        public LoginUnauthorized() {
            super("Login unauthorized","Client.LoginUnauthorized", 1);
        }
    }

    public static class NotLinkedRetry extends SonosSoapFault {

        public NotLinkedRetry() {
            super("Cannot find link code, retry", "Client.NOT_LINKED_RETRY", 5);
        }
    }

    public static class AuthTokenExpired extends SonosSoapFault {

        public AuthTokenExpired() {
            super("Creds expired", "Client.AuthTokenExpired", 6);
        }
    }

    public static class TokenRefreshRequired extends SonosSoapFault {
        RefreshAuthTokenResponse refreshTokens;

        public TokenRefreshRequired(RefreshAuthTokenResponse refreshTokens) {
            super("Refresh tokens", "Client.TokenRefreshRequired", 7);
            this.refreshTokens = refreshTokens;
        }

        public RefreshAuthTokenResponse getRefreshTokens() {
            return refreshTokens;
        }
    }
}
