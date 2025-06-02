package org.airsonic.player.service.upnp.transport;

import org.airsonic.player.service.VersionService;
import org.jupnp.model.message.*;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Callable;

public class SpringRestClientStreamClientImpl extends AbstractStreamClient<StreamClientConfigurationImpl, RestClient.RequestBodySpec> {
    private final static Logger LOG = LoggerFactory.getLogger(SpringRestClientStreamClientImpl.class);

    private StreamClientConfigurationImpl configuration;
    private RestClient restClient;
    private VersionService versionService;

    public SpringRestClientStreamClientImpl(StreamClientConfigurationImpl configuration, VersionService versionService) {
        this.configuration = configuration;
        this.versionService = versionService;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent", "Airsonic/" + this.versionService.getLocalVersion())
                //.defaultHeader("Host", NetworkService.)
                .build();
    }

    @Override
    public void stop() {

    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected RestClient.RequestBodySpec createRequest(StreamRequestMessage requestMessage) {
        final UpnpRequest upnpRequest = requestMessage.getOperation();

        LOG.trace("Creating HTTP request. URI: '{}' method: '{}'", upnpRequest.getURI(), upnpRequest.getMethod());
        RestClient.RequestBodySpec request;
        switch (upnpRequest.getMethod()) {
            case GET:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case POST:
            case NOTIFY:
                try {
                    request = restClient.method(HttpMethod.valueOf(upnpRequest.getHttpMethodName())).uri(upnpRequest.getURI());
                } catch (IllegalArgumentException e) {
                    LOG.debug("Cannot create request because URI '{}' is invalid", upnpRequest.getURI(), e);
                    return null;
                }
                break;
            default:
                throw new RuntimeException("Unknown HTTP method: " + upnpRequest.getHttpMethodName());
        }

        switch (upnpRequest.getMethod()) {
            case POST:
            case NOTIFY:
                createContentProvider(requestMessage, request);
                break;
            default:
        }

        // Add the default user agent if not already set on the message
        if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)) {
            request.header("User-Agent", getConfiguration().getUserAgentValue(requestMessage.getUdaMajorVersion(), requestMessage.getUdaMinorVersion()));
        }

        // Headers
        requestMessage.getHeaders().forEach( (h, v) -> request.header(h, v.toArray(new String[0])));

        return request;
    }

    protected <O extends UpnpOperation> void createContentProvider(final UpnpMessage<O> upnpMessage, RestClient.RequestBodySpec request) {
        if (upnpMessage.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
            LOG.trace("Preparing HTTP request entity as String");
            request.body(upnpMessage.getBodyString());
        } else {
            LOG.trace("Preparing HTTP request entity as byte[]");
            request.body(new HttpEntity<>(upnpMessage.getBodyBytes()));
        }
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(StreamRequestMessage requestMessage, RestClient.RequestBodySpec request) {
        return () -> {
            LOG.trace("Sending HTTP request: {}", requestMessage);
            try {
                return request.exchange( (req, res) -> {
                    LOG.trace("Received HTTP response: {}", res.getStatusText());

                    // Status
                    final UpnpResponse responseOperation = new UpnpResponse(res.getStatusCode().value(), res.getStatusText());

                    // Message
                    final StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

                    // Headers
                    responseMessage.setHeaders(new UpnpHeaders(res.getHeaders()));

                    // Body
                    final byte[] bytes = res.getBody().readAllBytes();
                    if (bytes != null && bytes.length != 0) {
                        responseMessage.setBodyCharacters(bytes);
                    }

                    return responseMessage;
                });
            } catch (final RuntimeException e) {
                LOG.error("Request: {} failed", request, e);
                throw e;
            }
        };
    }

    @Override
    protected void abort(RestClient.RequestBodySpec request) {

    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        LOG.debug("Exception while executing UPNP request", t);
        return false;
    }
}
