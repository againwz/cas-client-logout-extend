/**
 * 
 */
package com.aldb.cas.logout.extend;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.jasig.cas.client.session.SessionMappingStorage;
import org.jasig.cas.client.util.AbstractConfigurationFilter;

/**
 * @author sunff
 * 
 */
public class ClusterSigleSignOutFilter extends AbstractConfigurationFilter {

    // private String clusterNodeUrls;

    public void setClusterNodeUrls(String clusterNodeUrls) {
        // this.clusterNodeUrls = clusterNodeUrls;
        if (HANDLER != null) {
            HANDLER.setClusterNodeUrls(clusterNodeUrls);
        }
    }

    private static final ClusterSingleSignOutHandler HANDLER = new ClusterSingleSignOutHandler();

    private AtomicBoolean handlerInitialized = new AtomicBoolean(false);

    public void init(final FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);
        if (!isIgnoreInitConfiguration()) {
            setArtifactParameterName(getString(ConfigurationKeys.ARTIFACT_PARAMETER_NAME));
            setLogoutParameterName(getString(ConfigurationKeys.LOGOUT_PARAMETER_NAME));
            setFrontLogoutParameterName(getString(ConfigurationKeys.FRONT_LOGOUT_PARAMETER_NAME));
            setRelayStateParameterName(getString(ConfigurationKeys.RELAY_STATE_PARAMETER_NAME));
            setCasServerUrlPrefix(getString(ConfigurationKeys.CAS_SERVER_URL_PREFIX));
            HANDLER.setArtifactParameterOverPost(getBoolean(ConfigurationKeys.ARTIFACT_PARAMETER_OVER_POST));
            HANDLER.setEagerlyCreateSessions(getBoolean(ConfigurationKeys.EAGERLY_CREATE_SESSIONS));
        }
        HANDLER.init();
        handlerInitialized.set(true);
    }

    public void setArtifactParameterName(final String name) {
        HANDLER.setArtifactParameterName(name);
    }

    public void setLogoutParameterName(final String name) {
        HANDLER.setLogoutParameterName(name);
    }

    public void setFrontLogoutParameterName(final String name) {
        HANDLER.setFrontLogoutParameterName(name);
    }

    public void setRelayStateParameterName(final String name) {
        HANDLER.setRelayStateParameterName(name);
    }

    public void setCasServerUrlPrefix(final String casServerUrlPrefix) {
        HANDLER.setCasServerUrlPrefix(casServerUrlPrefix);
    }

    public void setSessionMappingStorage(final SessionMappingStorage storage) {
        HANDLER.setSessionMappingStorage(storage);
    }

    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;

        /**
         * <p>Workaround for now for the fact that Spring Security will fail since it doesn't call {@link #init(javax.servlet.FilterConfig)}.</p>
         * <p>Ultimately we need to allow deployers to actually inject their fully-initialized {@link org.jasig.cas.client.session.SingleSignOutHandler}.</p>
         */
        if (!this.handlerInitialized.getAndSet(true)) {
            HANDLER.init();
        }

        if (HANDLER.process(request, response)) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    public void destroy() {
        // nothing to do
    }

    protected static ClusterSingleSignOutHandler getSingleSignOutHandler() {
        return HANDLER;
    }



/*    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        final HttpServletRequest request = (HttpServletRequest) servletRequest;

        if (handler.isTokenRequest(request)) {
            handler.recordSession(request);
        } else if (handler.isLogoutRequest(request)) {
            handler.destroySession(request);
            // Do not continue up filter chain
            return;
        } else if (handler.isLogoutRequestFromClusterNode(request)) {
            handler.destorySessionFromClusterNode(request);
            return;
        } else {
            logger.trace("Ignoring URI " + request.getRequestURI());
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }*/

   
}
