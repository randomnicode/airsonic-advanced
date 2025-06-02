package org.airsonic.player.service.upnp.transport;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
//import org.apache.catalina.connector.Connector;
import org.jupnp.transport.spi.ServletContainerAdapter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class TomcatServletContainer implements ServletContainerAdapter {

    ServletContext servletContext;



    @Override
    public void setExecutorService(ExecutorService executorService) {

    }

    @Override
    public int addConnector(String host, int port) throws IOException {
//        Connector connector = new Connector();
//        connector.setPort(port);
        //connector.set
        return port;
    }

    @Override
    public void registerServlet(String contextPath, Servlet servlet) {
        ServletRegistration.Dynamic reg = servletContext.addServlet(servlet.getServletConfig().getServletName(), servlet);
        reg.addMapping(contextPath);
        reg.setLoadOnStartup(2);
        reg.setAsyncSupported(true);
    }

    @Override
    public void startIfNotRunning() {

    }

    @Override
    public void stopIfRunning() {

    }
}
