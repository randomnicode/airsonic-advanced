package org.airsonic.player.service.upnp.transport.jakarta;

import java.net.InetAddress;

import jakarta.servlet.Servlet;

import org.airsonic.player.service.upnp.transport.jakarta.async.AsyncServlet;
import org.airsonic.player.service.upnp.transport.jakarta.async.AsyncUtil;
import org.airsonic.player.service.upnp.transport.jakarta.blocking.BlockingServlet;
import org.jupnp.transport.Router;
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet stream server implementation. Copied from jupnp to work with jakarta packages
 *
 * @author Christian Bauer - Initial contribution to work with Servlet 3.0
 * @author Ivan Iliev - Added support for runtime switch to Servlet 2.4
 */
public class ServletStreamServerImpl implements StreamServer<ServletStreamServerConfigurationImpl> {

    private final Logger logger = LoggerFactory.getLogger(org.jupnp.transport.impl.ServletStreamServerImpl.class);

    protected final ServletStreamServerConfigurationImpl configuration;
    protected int localPort;

    public ServletStreamServerImpl(ServletStreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
    }

    @Override
    public ServletStreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    public synchronized void init(InetAddress bindAddress, final Router router) throws InitializationException {
        try {
            logger.debug("Setting executor service on servlet container adapter");
            getConfiguration().getServletContainerAdapter()
                    .setExecutorService(router.getConfiguration().getStreamServerExecutorService());

            logger.debug("Adding connector: {}:{}", bindAddress, getConfiguration().getListenPort());
            localPort = getConfiguration().getServletContainerAdapter().addConnector(bindAddress.getHostAddress(),
                    getConfiguration().getListenPort());

            String contextPath = router.getConfiguration().getNamespace().getBasePath().getPath();

            // Instantiate async or blocking servlet depending on javax.servlet runtime version
            Servlet servlet;
            if (AsyncUtil.SERVLET3_SUPPORT) {
                servlet = createAsyncServlet(router);
            } else {
                servlet = createBlockingServlet(router);
            }

            getConfiguration().getServletContainerAdapter().registerServlet(contextPath, servlet);
        } catch (Exception e) {
            throw new InitializationException("Could not initialize " + getClass().getSimpleName(), e);
        }
    }

    @Override
    public synchronized int getPort() {
        return this.localPort;
    }

    @Override
    public synchronized void stop() {
        getConfiguration().getServletContainerAdapter().stopIfRunning();
    }

    @Override
    public void run() {
        getConfiguration().getServletContainerAdapter().startIfNotRunning();
    }

    protected Servlet createAsyncServlet(final Router router) {
        return new AsyncServlet(router, getConfiguration());
    }

    protected Servlet createBlockingServlet(final Router router) {
        return new BlockingServlet(router, getConfiguration());
    }
}

