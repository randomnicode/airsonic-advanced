package org.airsonic.player.spring;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.airsonic.player.service.NetworkService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfiguration implements WebSocketMessageBrokerConfigurer {
    public static final String BASE_URL = "baseUri";
    public static final String CLIENT_IP = "clientIp";
    public static final String REQUEST_PARAMETERS = "requestParameters";
    public static final String REQUEST_SESSION_ATTRIBUTES = "requestSessionAttributes";
    public static final String REQUEST_COOKIES = "requestCookies";

    private TaskScheduler messageBrokerTaskScheduler;

    @Autowired
    public void setMessageBrokerTaskScheduler(TaskScheduler taskScheduler) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(messageBrokerTaskScheduler)
                .setHeartbeatValue(new long[] { 20000, 20000 });
        config.setApplicationDestinationPrefixes("/app");

        // this ensures publish order is serial at the cost of no parallelization and
        // performance - if performance is bad, this should be turned off
        config.setPreservePublishOrder(true);
    }

    public static String STOMP_ENDPOINT = "websocket";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/" + STOMP_ENDPOINT)
                .setAllowedOriginPatterns("*")
                .addInterceptors(new ServletRequestCaptureHandshakeInterceptor());
    }

    public static class ServletRequestCaptureHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                WebSocketHandler wsHandler, Map<String, Object> attributes) {

            // Set servlet request attribute to WebSocket session
            if (request instanceof ServletServerHttpRequest) {
                HttpServletRequest req = ((ServletServerHttpRequest) request).getServletRequest();
                attributes.put(BASE_URL, NetworkService.getBaseUrl(req));
                attributes.put(CLIENT_IP, req.getRemoteAddr());
                attributes.put(REQUEST_PARAMETERS, req.getParameterMap());
                Map<String, Object> sessionAttributes = new HashMap<>();
                if (req.getSession() != null) {
                    req.getSession().getAttributeNames().asIterator().forEachRemaining(a -> sessionAttributes.put(a, req.getSession().getAttribute(a)));
                }
                attributes.put(REQUEST_SESSION_ATTRIBUTES, sessionAttributes);
                attributes.put(REQUEST_COOKIES, req.getCookies());
            }

            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                Exception exception) {
        }
    }
}
