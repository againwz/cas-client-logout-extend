/**
 * 
 */
package com.aldb.cas.logout.extend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jasig.cas.client.Protocol;
import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.jasig.cas.client.session.HashMapBackedSessionMappingStorage;
import org.jasig.cas.client.session.SessionMappingStorage;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sunff
 *
 */
public class ClusterSingleSignOutHandler {

    private String clusterNodeUrls;
    private String logoutClusterNodesParameterName = "false";

    public void setClusterNodeUrls(String clusterNodeUrls) {
        this.clusterNodeUrls = clusterNodeUrls;
    }

    private final static int DECOMPRESSION_FACTOR = 10;

    /** Logger instance */
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** Mapping of token IDs and session IDs to HTTP sessions */
    private SessionMappingStorage sessionMappingStorage = new HashMapBackedSessionMappingStorage();

    /**
     * The name of the artifact parameter. This is used to capture the session
     * identifier.
     */
    private String artifactParameterName = Protocol.CAS2.getArtifactParameterName();

    /** Parameter name that stores logout request for back channel SLO */
    private String logoutParameterName = ConfigurationKeys.LOGOUT_PARAMETER_NAME.getDefaultValue();

    /** Parameter name that stores logout request for front channel SLO */
    private String frontLogoutParameterName = ConfigurationKeys.FRONT_LOGOUT_PARAMETER_NAME.getDefaultValue();

    /**
     * Parameter name that stores the state of the CAS server webflow for the
     * callback
     */
    private String relayStateParameterName = ConfigurationKeys.RELAY_STATE_PARAMETER_NAME.getDefaultValue();

    /** The prefix url of the CAS server */
    private String casServerUrlPrefix = "";

    private boolean artifactParameterOverPost = false;

    private boolean eagerlyCreateSessions = true;

    private List<String> safeParameters;

    private LogoutStrategy logoutStrategy = isServlet30() ? new Servlet30LogoutStrategy()
            : new Servlet25LogoutStrategy();

    public void setSessionMappingStorage(final SessionMappingStorage storage) {
        this.sessionMappingStorage = storage;
    }

    public void setArtifactParameterOverPost(final boolean artifactParameterOverPost) {
        this.artifactParameterOverPost = artifactParameterOverPost;
    }

    public SessionMappingStorage getSessionMappingStorage() {
        return this.sessionMappingStorage;
    }

    /**
     * @param name
     *            Name of the authentication token parameter.
     */
    public void setArtifactParameterName(final String name) {
        this.artifactParameterName = name;
    }

    /**
     * @param name
     *            Name of parameter containing CAS logout request message for
     *            back channel SLO.
     */
    public void setLogoutParameterName(final String name) {
        this.logoutParameterName = name;
    }

    /**
     * @param casServerUrlPrefix
     *            The prefix url of the CAS server.
     */
    public void setCasServerUrlPrefix(final String casServerUrlPrefix) {
        this.casServerUrlPrefix = casServerUrlPrefix;
    }

    /**
     * @param name
     *            Name of parameter containing CAS logout request message for
     *            front channel SLO.
     */
    public void setFrontLogoutParameterName(final String name) {
        this.frontLogoutParameterName = name;
    }

    /**
     * @param name
     *            Name of parameter containing the state of the CAS server
     *            webflow.
     */
    public void setRelayStateParameterName(final String name) {
        this.relayStateParameterName = name;
    }

    public void setEagerlyCreateSessions(final boolean eagerlyCreateSessions) {
        this.eagerlyCreateSessions = eagerlyCreateSessions;
    }

    /**
     * Initializes the component for use.
     */
    public synchronized void init() {
        if (this.safeParameters == null) {
            CommonUtils.assertNotNull(this.artifactParameterName, "artifactParameterName cannot be null.");
            CommonUtils.assertNotNull(this.logoutParameterName, "logoutParameterName cannot be null.");
            CommonUtils.assertNotNull(this.frontLogoutParameterName, "frontLogoutParameterName cannot be null.");
            CommonUtils.assertNotNull(this.sessionMappingStorage, "sessionMappingStorage cannot be null.");
            CommonUtils.assertNotNull(this.relayStateParameterName, "relayStateParameterName cannot be null.");
            CommonUtils.assertNotNull(this.casServerUrlPrefix, "casServerUrlPrefix cannot be null.");

            if (CommonUtils.isBlank(this.casServerUrlPrefix)) {
                logger.warn("Front Channel single sign out redirects are disabled when the 'casServerUrlPrefix' value is not set.");
            }

            if (this.artifactParameterOverPost) {
                this.safeParameters = Arrays.asList(this.logoutParameterName, this.artifactParameterName);
            } else {
                this.safeParameters = Arrays.asList(this.logoutParameterName);
            }
        }
    }

    /**
     * Determines whether the given request contains an authentication token.
     *
     * @param request
     *            HTTP reqest.
     *
     * @return True if request contains authentication token, false otherwise.
     */
    private boolean isTokenRequest(final HttpServletRequest request) {
        return CommonUtils.isNotBlank(CommonUtils.safeGetParameter(request, this.artifactParameterName,
                this.safeParameters));
    }

    /**
     * Determines whether the given request is a CAS back channel logout
     * request.
     *
     * @param request
     *            HTTP request.
     *
     * @return True if request is logout request, false otherwise.
     */
    private boolean isBackChannelLogoutRequest(final HttpServletRequest request) {
        System.out.println("isBackChannelLogoutRequest begin----");
        System.out.println(request.getRequestURL());
        System.out.println("request.getMethod()=" + request.getMethod());
        System.out
                .println("CommonUtils.isNotBlank(CommonUtils.safeGetParameter(request, this.logoutParameterName,this.safeParameters))="
                        + CommonUtils.safeGetParameter(request, this.logoutParameterName, this.safeParameters));
        System.out.println("isBackChannelLogoutRequest end----");
        return "POST".equals(request.getMethod())
                && !isMultipartRequest(request)
                && CommonUtils.isNotBlank(CommonUtils.safeGetParameter(request, this.logoutParameterName,
                        this.safeParameters));
    }

    /**
     * Determines whether the given request is a CAS front channel logout
     * request. Front Channel log out requests are only supported when the
     * 'casServerUrlPrefix' value is set.
     *
     * @param request
     *            HTTP request.
     *
     * @return True if request is logout request, false otherwise.
     */
    private boolean isFrontChannelLogoutRequest(final HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && CommonUtils.isNotBlank(this.casServerUrlPrefix)
                && CommonUtils.isNotBlank(CommonUtils.safeGetParameter(request, this.frontLogoutParameterName));
    }

    /**
     * Process a request regarding the SLO process: record the session or
     * destroy it.
     *
     * @param request
     *            the incoming HTTP request.
     * @param response
     *            the HTTP response.
     * @return if the request should continue to be processed.
     */
    public boolean process(final HttpServletRequest request, final HttpServletResponse response) {
        if (isTokenRequest(request)) {
            logger.trace("Received a token request");
            recordSession(request);
            return true;

        } else if (isBackChannelLogoutRequest(request)) {
            logger.trace("Received a back channel logout request");
            destroySession(request);

            return false;

        } else if (isFrontChannelLogoutRequest(request)) {
            logger.trace("Received a front channel logout request");
            destroySession(request);
            // redirection url to the CAS server
            final String redirectionUrl = computeRedirectionToServer(request);
            if (redirectionUrl != null) {
                CommonUtils.sendRedirect(response, redirectionUrl);
            }
            return false;

        } else if (isLogoutRequestFromClusterNode(request)) {
            destorySessionFromClusterNode(request);
            // redirection url to the CAS server
            final String redirectionUrl = computeRedirectionToServer(request);
            if (redirectionUrl != null) {
                CommonUtils.sendRedirect(response, redirectionUrl);
            }
            return false;
        } else {
            logger.trace("Ignoring URI for logout: {}", request.getRequestURI());
            return true;
        }
    }

    private void destoryAllSessionOfClusterNodes(String token) {
        // 广播到所有节点
        System.out.println("destorySessionOfClusterNodes--begin");
        if (clusterNodeUrls != null) {
            String[] clusterNodeUrlArray = clusterNodeUrls.split(",");
            for (String url : clusterNodeUrlArray) {
                HttpClient httpClient = new DefaultHttpClient();
                HttpPost httpPostReq = new HttpPost(url);
                ArrayList<NameValuePair> paramList = new ArrayList<NameValuePair>();
                paramList.add(new BasicNameValuePair(this.logoutClusterNodesParameterName, "true"));
                paramList.add(new BasicNameValuePair(this.artifactParameterName, token));
                try {
                    httpPostReq.setEntity(new UrlEncodedFormEntity(paramList));
                    httpClient.execute(httpPostReq);
                } catch (Exception e) {
                    logger.debug("Error destorySessionOfClusterNodes", e);
                } finally {
                    HttpClientUtils.closeQuietly(httpClient);
                }
            }
        }
        System.out.println("destorySessionOfClusterNodes--end");
    }

    private boolean isLogoutRequestFromClusterNode(HttpServletRequest request) {
        String clusterNodes = request.getParameter(logoutClusterNodesParameterName);
        System.out.println("isLogoutRequestFromClusterNode begin---");
        System.out.println("clusterNodes=" + clusterNodes);
        System.out.println("isLogoutRequestFromClusterNode end---");
        if (clusterNodes != null && "true".equals(clusterNodes)) {
            return true;
        }
        return false;
        // CommonUtils.safeGetParameter(request,
        // this.logoutClusterNodesParameterName)
    }

    private void destorySessionFromClusterNode(HttpServletRequest request) {
        System.out.println("destorySessionFromClusterNode begin---");

        String token = request.getParameter(this.artifactParameterName);
        System.out.println("token=" + token);
       

        if (CommonUtils.isNotBlank(token)) {
            final HttpSession session = sessionMappingStorage.removeSessionByMappingId(token);
            if (session != null) {
                String sessionId = session.getId();
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Invalidating sesssion[%s]for token [%s]", sessionId, token));
                }
                try {
                    session.invalidate();

                } catch (final IllegalStateException e) {
                    logger.debug("Error invalidating session", e);
                }
                this.logoutStrategy.logout(request);
            }
        }
        System.out.println("destorySessionFromClusterNode end---");
    }

    /**
     * Associates a token request with the current HTTP session by recording the
     * mapping in the the configured {@link SessionMappingStorage} container.
     * 
     * @param request
     *            HTTP request containing an authentication token.
     */
    private void recordSession(final HttpServletRequest request) {
        final HttpSession session = request.getSession(this.eagerlyCreateSessions);

        if (session == null) {
            logger.debug("No session currently exists (and none created).  Cannot record session information for single sign out.");
            return;
        }

        final String token = CommonUtils.safeGetParameter(request, this.artifactParameterName, this.safeParameters);
        logger.debug("Recording session for token {}", token);

        try {
            this.sessionMappingStorage.removeBySessionById(session.getId());
        } catch (final Exception e) {
            // ignore if the session is already marked as invalid. Nothing we
            // can do!
        }
        System.out.println("recordSesson,token=" + token);
        sessionMappingStorage.addSessionById(token, session);
    }

    /**
     * Uncompress a logout message (base64 + deflate).
     * 
     * @param originalMessage
     *            the original logout message.
     * @return the uncompressed logout message.
     */
    private String uncompressLogoutMessage(final String originalMessage) {
        final byte[] binaryMessage = Base64.decodeBase64(originalMessage);

        Inflater decompresser = null;
        try {
            // decompress the bytes
            decompresser = new Inflater();
            decompresser.setInput(binaryMessage);
            final byte[] result = new byte[binaryMessage.length * DECOMPRESSION_FACTOR];

            final int resultLength = decompresser.inflate(result);

            // decode the bytes into a String
            return new String(result, 0, resultLength, "UTF-8");
        } catch (final Exception e) {
            logger.error("Unable to decompress logout message", e);
            throw new RuntimeException(e);
        } finally {
            if (decompresser != null) {
                decompresser.end();
            }
        }
    }

    /**
     * Destroys the current HTTP session for the given CAS logout request.
     *
     * @param request
     *            HTTP request containing a CAS logout message.
     */
    private void destroySession(final HttpServletRequest request) {
        final String logoutMessage;
        // front channel logout -> the message needs to be base64 decoded +
        // decompressed
        if (isFrontChannelLogoutRequest(request)) {
            logoutMessage = uncompressLogoutMessage(CommonUtils
                    .safeGetParameter(request, this.frontLogoutParameterName));
        } else {
            logoutMessage = CommonUtils.safeGetParameter(request, this.logoutParameterName, this.safeParameters);
        }
        logger.trace("Logout request:\n{}", logoutMessage);

        final String token = XmlUtils.getTextForElement(logoutMessage, "SessionIndex");
        if (CommonUtils.isNotBlank(token)) {
            final HttpSession session = this.sessionMappingStorage.removeSessionByMappingId(token);

            if (session != null) {
                final String sessionID = session.getId();
                logger.debug("Invalidating session [{}] for token [{}]", sessionID, token);

                try {
                    session.invalidate();
                } catch (final IllegalStateException e) {
                    logger.debug("Error invalidating session.", e);
                }
                this.logoutStrategy.logout(request);
            } else {
                destoryAllSessionOfClusterNodes(token);
            }
        }
    }

    /**
     * Compute the redirection url to the CAS server when it's a front channel
     * SLO (depending on the relay state parameter).
     *
     * @param request
     *            The HTTP request.
     * @return the redirection url to the CAS server.
     */
    private String computeRedirectionToServer(final HttpServletRequest request) {
        final String relayStateValue = CommonUtils.safeGetParameter(request, this.relayStateParameterName);
        // if we have a state value -> redirect to the CAS server to continue
        // the logout process
        if (CommonUtils.isNotBlank(relayStateValue)) {
            final StringBuilder buffer = new StringBuilder();
            buffer.append(casServerUrlPrefix);
            if (!this.casServerUrlPrefix.endsWith("/")) {
                buffer.append("/");
            }
            buffer.append("logout?_eventId=next&");
            buffer.append(this.relayStateParameterName);
            buffer.append("=");
            buffer.append(CommonUtils.urlEncode(relayStateValue));
            final String redirectUrl = buffer.toString();
            logger.debug("Redirection url to the CAS server: {}", redirectUrl);
            return redirectUrl;
        }
        return null;
    }

    private boolean isMultipartRequest(final HttpServletRequest request) {
        return request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart");
    }

    private static boolean isServlet30() {
        try {
            return HttpServletRequest.class.getMethod("logout") != null;
        } catch (final NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Abstracts the ways we can force logout with the Servlet spec.
     */
    private interface LogoutStrategy {

        void logout(HttpServletRequest request);
    }

    private class Servlet25LogoutStrategy implements LogoutStrategy {

        public void logout(final HttpServletRequest request) {
            // nothing additional to do here
        }
    }

    private class Servlet30LogoutStrategy implements LogoutStrategy {

        public void logout(final HttpServletRequest request) {
            try {
                request.logout();
            } catch (final ServletException e) {
                logger.debug("Error performing request.logout.");
            }
        }
    }
}
