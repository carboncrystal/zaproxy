/*
 *
 * Paros and its related class files.
 *
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 *
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2011/09/19 Added debugging
// ZAP: 2012/04/23 Removed unnecessary cast.
// ZAP: 2012/05/08 Use custom http client on "Connection: Upgrade" in executeMethod().
//                 Retrieve upgraded socket and save for later use in send() method.
// ZAP: 2012/08/07 Issue 342 Support the HttpSenderListener
// ZAP: 2012/12/27 Do not read request body on Server-Sent Event streams.
// ZAP: 2013/01/03 Resolved Checkstyle issues: removed throws HttpException
//                 declaration where IOException already appears,
//                 introduced two helper methods for notifying listeners.
// ZAP: 2013/01/19 Issue 459: Active scanner locking
// ZAP: 2013/01/23 Clean up of exception handling/logging.
// ZAP: 2013/01/30 Issue 478: Allow to choose to send ZAP's managed cookies on
// a single Cookie request header and set it as the default
// ZAP: 2013/07/10 Issue 720: Cannot send non standard http methods
// ZAP: 2013/07/14 Issue 729: Update NTLM authentication code
// ZAP: 2013/07/25 Added support for sending the message from the perspective of a User
// ZAP: 2013/08/31 Reauthentication when sending a message from the perspective of a User
// ZAP: 2013/09/07 Switched to using HttpState for requesting User for cookie management
// ZAP: 2013/09/26 Issue 716: ZAP flags its own HTTP responses
// ZAP: 2013/09/26 Issue 656: Content-length: 0 in GET requests
// ZAP: 2013/09/29 Deprecating configuring HTTP Authentication through Options
// ZAP: 2013/11/16 Issue 837: Update, always, the HTTP request sent/forward by ZAP's proxy
// ZAP: 2013/12/11 Corrected log.info calls to use debug
// ZAP: 2014/03/04 Issue 1043: Custom active scan dialog
// ZAP: 2014/03/23 Issue 412: Enable unsafe SSL/TLS renegotiation option not saved
// ZAP: 2014/03/23 Issue 416: Normalise how multiple related options are managed throughout ZAP
// and enhance the usability of some options
// ZAP: 2014/03/29 Issue 1132: HttpSender ignores the "Send single cookie request header" option
// ZAP: 2014/08/14 Issue 1291: 407 Proxy Authentication Required while active scanning
// ZAP: 2014/10/25 Issue 1062: Added a getter for the HttpClient.
// ZAP: 2014/10/28 Issue 1390: Force https on cfu call
// ZAP: 2014/11/25 Issue 1411: Changed getUser() visibility
// ZAP: 2014/12/11 Added JavaDoc to constructor and removed the instance variable allowState.
// ZAP: 2015/04/09 Allow to specify the maximum number of retries on I/O error.
// ZAP: 2015/04/09 Allow to specify the maximum number of redirects.
// ZAP: 2015/04/09 Allow to specify if circular redirects are allowed.
// ZAP: 2015/06/12 Issue 1459: Add an HTTP sender listener script
// ZAP: 2016/05/24 Issue 2463: Websocket not proxied when outgoing proxy is set
// ZAP: 2016/05/27 Issue 2484: Circular Redirects
// ZAP: 2016/06/08 Set User-Agent header defined in options as default for (internal) CONNECT
// requests
// ZAP: 2016/06/10 Allow to validate the URI of the redirections before being followed
// ZAP: 2016/08/04 Added removeListener(..)
// ZAP: 2016/12/07 Add initiator constant for AJAX spider requests
// ZAP: 2016/12/12 Add initiator constant for Forced Browse requests
// ZAP: 2017/03/27 Introduce HttpRequestConfig.
// ZAP: 2017/06/12 Allow to ignore listeners.
// ZAP: 2017/06/19 Allow to send a request with custom socket timeout.
// ZAP: 2017/11/20 Add initiator constant for Token Generator requests.
// ZAP: 2017/11/27 Use custom CookieSpec (ZapCookieSpec).
// ZAP: 2017/12/20 Apply socket connect timeout (Issue 4171).
// ZAP: 2018/02/06 Make the lower case changes locale independent (Issue 4327).
// ZAP: 2018/02/19 Added WEB_SOCKET_INITIATOR.
// ZAP: 2018/02/23 Issue 1161: Allow to override the global session tracking setting
//                 Fix Session Tracking button sync
// ZAP: 2018/08/03 Added AUTHENTICATION_HELPER_INITIATOR.
// ZAP: 2018/09/17 Set the user to messages created for redirections (Issue 2531).
// ZAP: 2018/10/12 Deprecate getClient(), it exposes implementation details.
// ZAP: 2019/03/24 Removed commented and unused sendAndReceive method.
// ZAP: 2019/06/01 Normalise line endings.
// ZAP: 2019/06/05 Normalise format/style.
// ZAP: 2019/08/19 Reinstate proxy auth credentials when HTTP state is changed.
// ZAP: 2019/09/17 Use remove() instead of set(null) on IN_LISTENER.
// ZAP: 2019/09/25 Add option to disable cookies
// ZAP: 2020/04/20 Configure if the names should be resolved or not (Issue 29).
// ZAP: 2020/09/04 Added AUTHENTICATION_POLL_INITIATOR
// ZAP: 2020/11/26 Use Log4j 2 classes for logging.
// ZAP: 2020/12/09 Set content encoding to the response body.
// ZAP: 2021/05/14 Remove redundant type arguments and empty statement.
// ZAP: 2022/01/04 Add initiator constant OAST_INITIATOR for OAST requests.
// ZAP: 2022/04/08 Deprecate getSSLConnector() and executeMethod.
// ZAP: 2022/04/10 Add support for unencoded redirects
// ZAP: 2022/04/11 Deprecate set/getUserAgent() and remove userAgent/modifyUserAgent().
// ZAP: 2022/04/11 Prevent null listeners and add JavaDoc to add/removeListener.
// ZAP: 2022/04/23 Use main connection options directly.
// ZAP: 2022/04/24 Notify listeners of all redirects followed.
// ZAP: 2022/04/24 Move network initialisations from ZAP class.
// ZAP: 2022/04/24 Allow to download to file.
// ZAP: 2022/04/27 Expose global HTTP state enabled status.
// ZAP: 2022/04/27 Use latest proxy settings always.
// ZAP: 2022/04/29 Deprecate setAllowCircularRedirects.
// ZAP: 2022/05/04 Always use single cookie request header.
// ZAP: 2022/05/04 Use latest timeout/user-agent always.
// ZAP: 2022/05/20 Address deprecation warnings with ConnectionParam.
package org.parosproxy.paros.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.net.SocketFactory;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpHost;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodDirector;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.InvalidRedirectLocationException;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.model.Model;
import org.zaproxy.zap.ZapGetMethod;
import org.zaproxy.zap.ZapHttpConnectionManager;
import org.zaproxy.zap.network.HttpRedirectionValidator;
import org.zaproxy.zap.network.HttpRequestConfig;
import org.zaproxy.zap.network.HttpSenderListener;
import org.zaproxy.zap.network.ZapCookieSpec;
import org.zaproxy.zap.network.ZapNTLMScheme;
import org.zaproxy.zap.users.User;

public class HttpSender {
    public static final int PROXY_INITIATOR = 1;
    public static final int ACTIVE_SCANNER_INITIATOR = 2;
    public static final int SPIDER_INITIATOR = 3;
    public static final int FUZZER_INITIATOR = 4;
    public static final int AUTHENTICATION_INITIATOR = 5;
    public static final int MANUAL_REQUEST_INITIATOR = 6;
    public static final int CHECK_FOR_UPDATES_INITIATOR = 7;
    public static final int BEAN_SHELL_INITIATOR = 8;
    public static final int ACCESS_CONTROL_SCANNER_INITIATOR = 9;
    public static final int AJAX_SPIDER_INITIATOR = 10;
    public static final int FORCED_BROWSE_INITIATOR = 11;
    public static final int TOKEN_GENERATOR_INITIATOR = 12;
    public static final int WEB_SOCKET_INITIATOR = 13;
    public static final int AUTHENTICATION_HELPER_INITIATOR = 14;
    public static final int AUTHENTICATION_POLL_INITIATOR = 15;
    public static final int OAST_INITIATOR = 16;

    private static Logger log = LogManager.getLogger(HttpSender.class);

    private static ProtocolSocketFactory sslFactory = null;
    private static Protocol protocol = null;

    private static List<HttpSenderListener> listeners = new ArrayList<>();
    private static Comparator<HttpSenderListener> listenersComparator = null;

    private User user = null;

    static {
        try {
            protocol = Protocol.getProtocol("https");
            sslFactory = protocol.getSocketFactory();
        } catch (Exception e) {
        }
        // avoid init again if already initialized
        if (sslFactory == null || !(sslFactory instanceof SSLConnector)) {
            Protocol.registerProtocol(
                    "https",
                    new Protocol("https", (ProtocolSocketFactory) new SSLConnector(true), 443));
        }

        Protocol.registerProtocol(
                "http", new Protocol("http", new ProtocolSocketFactoryImpl(), 80));

        AuthPolicy.registerAuthScheme(AuthPolicy.NTLM, ZapNTLMScheme.class);
        CookiePolicy.registerCookieSpec(CookiePolicy.DEFAULT, ZapCookieSpec.class);
        CookiePolicy.registerCookieSpec(CookiePolicy.BROWSER_COMPATIBILITY, ZapCookieSpec.class);
    }

    private static HttpMethodHelper helper = new HttpMethodHelper();
    private static final ThreadLocal<Boolean> IN_LISTENER = new ThreadLocal<>();
    private static final HttpRequestConfig NO_REDIRECTS = HttpRequestConfig.builder().build();
    private static final HttpRequestConfig FOLLOW_REDIRECTS =
            HttpRequestConfig.builder().setFollowRedirects(true).build();

    private static final ResponseBodyConsumer DEFAULT_BODY_CONSUMER =
            (msg, method) -> {
                if (msg.isEventStream()) {
                    msg.getResponseBody().setCharset(msg.getResponseHeader().getCharset());
                    msg.getResponseBody().setLength(0);
                    return;
                }

                msg.setResponseBody(method.getResponseBody());
            };

    private HttpClient client = null;

    @SuppressWarnings("deprecation")
    private ConnectionParam param = null;

    private MultiThreadedHttpConnectionManager httpConnManager = null;
    private HttpRequestConfig followRedirect = NO_REDIRECTS;
    private boolean useCookies;
    private boolean useGlobalState;
    private int initiator = -1;

    /*
     * public HttpSender(ConnectionParam connectionParam, boolean allowState) { this
     * (connectionParam, allowState, -1); }
     */

    /**
     * Constructs an {@code HttpSender}.
     *
     * <p>The {@code initiator} is used to indicate the component that is sending the messages when
     * the {@code HttpSenderListener}s are notified of messages sent and received.
     *
     * @param connectionParam the parameters used to setup the connections to target hosts
     * @param useGlobalState {@code true} if the messages sent/received should use the global HTTP
     *     state, {@code false} if should use a non shared HTTP state
     * @param initiator the ID of the initiator of the HTTP messages sent
     * @see ConnectionParam#getHttpState()
     * @see HttpSenderListener
     * @see HttpMessage#getRequestingUser()
     * @deprecated (2.12.0) Use {@link #HttpSender(int)} instead, refer also to {@link
     *     #setUseGlobalState(boolean)}.
     */
    @Deprecated
    public HttpSender(ConnectionParam connectionParam, boolean useGlobalState, int initiator) {
        init(useGlobalState, initiator);
    }

    /**
     * Constructs an {@code HttpSender}.
     *
     * <p>Refer to {@link #setUseGlobalState(boolean)} to know how the HTTP state is managed.
     *
     * <p>The {@code initiator} is used to indicate the component that is sending the messages when
     * the {@code HttpSenderListener}s are notified of messages sent and received.
     *
     * @param initiator the ID of the initiator of the HTTP messages sent
     * @since 2.12.0
     * @see HttpSenderListener
     */
    public HttpSender(int initiator) {
        init(true, initiator);
    }

    @SuppressWarnings("deprecation")
    private void init(boolean useGlobalState, int initiator) {
        this.param = Model.getSingleton().getOptionsParam().getConnectionParam();
        this.initiator = initiator;

        client = createHttpClient();
        client.getParams().setBooleanParameter(HttpClientParams.ALLOW_CIRCULAR_REDIRECTS, true);

        // Set how cookie headers are sent no matter of the "allowState", in case a state is forced
        // by other extensions (e.g. Authentication)
        client.getParams().setBooleanParameter(HttpMethodParams.SINGLE_COOKIE_HEADER, true);

        setUseGlobalState(useGlobalState);
        setUseCookies(true);
    }

    private void setClientsCookiePolicy(String policy) {
        client.getParams().setCookiePolicy(policy);
    }

    /**
     * Gets the {@code SSLConnector} of the client.
     *
     * @return the {@code SSLConnector} used by the sender.
     * @deprecated (2.12.0) It will be removed in a following version.
     */
    @Deprecated
    public static SSLConnector getSSLConnector() {
        return (SSLConnector) protocol.getSocketFactory();
    }

    @SuppressWarnings("deprecation")
    private void checkState() {
        if (!useCookies) {
            resetState();
            setClientsCookiePolicy(CookiePolicy.IGNORE_COOKIES);
        } else if (useGlobalState) {
            if (param.isHttpStateEnabled()) {
                client.setState(param.getHttpState());
                setClientsCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
            } else {
                setClientsCookiePolicy(CookiePolicy.IGNORE_COOKIES);
            }
        } else {
            resetState();

            setClientsCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
        }
    }

    private void resetState() {
        HttpState state = new HttpState();
        client.setState(state);
    }

    /**
     * Sets whether or not the global state should be used. Defaults to {@code true}.
     *
     * <p>If {@code enableGlobalState} is {@code true} the {@code HttpSender} will use the HTTP
     * state given by the connections options iff the HTTP state is enabled there otherwise it
     * doesn't have any state (i.e. cookies are disabled). If {@code enableGlobalState} is {@code
     * false} it uses a non shared HTTP state.
     *
     * <p><strong>Note:</strong> The actual state used is overridden when {@link
     * #getUser(HttpMessage)} returns non-{@code null}.
     *
     * @param enableGlobalState {@code true} if the global state should be used, {@code false}
     *     otherwise.
     * @since 2.8.0
     * @see #isGlobalStateEnabled()
     * @see #setUseCookies(boolean)
     */
    public void setUseGlobalState(boolean enableGlobalState) {
        this.useGlobalState = enableGlobalState;

        checkState();
    }

    /**
     * Tells whether or not the global HTTP state is enabled.
     *
     * @return {@code true} if the global HTTP state is enabled, {@code false} otherwise.
     * @since 2.12.0
     * @see #setUseGlobalState(boolean)
     */
    @SuppressWarnings("deprecation")
    public boolean isGlobalStateEnabled() {
        return param.isHttpStateEnabled();
    }

    /**
     * Sets whether or not the requests sent should keep track of cookies.
     *
     * @param shouldUseCookies {@code true} if cookies should be used, {@code false} otherwise.
     * @since 2.9.0
     * @see #setUseGlobalState(boolean)
     */
    public void setUseCookies(boolean shouldUseCookies) {
        this.useCookies = shouldUseCookies;

        checkState();
    }

    private HttpClient createHttpClient() {

        httpConnManager = new MultiThreadedHttpConnectionManager();
        setCommonManagerParams(httpConnManager);
        return new HttpClient(httpConnManager);
    }

    @SuppressWarnings("deprecation")
    private void setProxyAuth(HttpState state) {
        if (param.isUseProxyChain() && param.isUseProxyChainAuth()) {
            String realm = param.getProxyChainRealm();
            state.setProxyCredentials(
                    new AuthScope(
                            param.getProxyChainName(),
                            param.getProxyChainPort(),
                            realm.isEmpty() ? AuthScope.ANY_REALM : realm),
                    new NTCredentials(
                            param.getProxyChainUserName(),
                            param.getProxyChainPassword(),
                            "",
                            realm));
        } else {
            state.clearProxyCredentials();
        }
    }

    /**
     * Executes the given method.
     *
     * @param method the method.
     * @param state the state, might be {@code null}.
     * @return the status code.
     * @throws IOException if an error occurred while executing the method.
     * @deprecated (2.12.0) Use one of the {@code sendAndReceive} methods. It will be removed in a
     *     following version.
     */
    @Deprecated
    public int executeMethod(HttpMethod method, HttpState state) throws IOException {
        return executeMethodImpl(method, state);
    }

    @SuppressWarnings("deprecation")
    private int executeMethodImpl(HttpMethod method, HttpState state) throws IOException {
        int responseCode = -1;

        String hostName;
        hostName = method.getURI().getHost();
        method.setDoAuthentication(true);
        HostConfiguration hc = null;

        HttpClient requestClient;
        if (isConnectionUpgrade(method)) {
            requestClient = new HttpClient(new ZapHttpConnectionManager());
        } else {
            requestClient = client;
        }

        if (this.initiator == CHECK_FOR_UPDATES_INITIATOR) {
            // Use the 'strict' SSLConnector, i.e. one that performs all the usual cert checks
            // The 'standard' one 'trusts' everything
            // This is to ensure that all 'check-for update' calls are made to the expected https
            // urls
            // without this is would be possible to intercept and change the response which could
            // result
            // in the user downloading and installing a malicious add-on
            hc =
                    new HostConfiguration() {
                        @Override
                        public synchronized void setHost(URI uri) {
                            try {
                                setHost(new HttpHost(uri.getHost(), uri.getPort(), getProtocol()));
                            } catch (URIException e) {
                                throw new IllegalArgumentException(e.toString());
                            }
                        }
                    };

            hc.setHost(
                    hostName,
                    method.getURI().getPort(),
                    new Protocol("https", (ProtocolSocketFactory) new SSLConnector(false), 443));
        }

        method.getParams()
                .setBooleanParameter(
                        HttpMethodDirector.PARAM_RESOLVE_HOSTNAME,
                        param.shouldResolveRemoteHostname(hostName));
        method.getParams()
                .setParameter(
                        HttpMethodDirector.PARAM_DEFAULT_USER_AGENT_CONNECT_REQUESTS,
                        param.getDefaultUserAgent());

        int timeout = (int) TimeUnit.SECONDS.toMillis(this.param.getTimeoutInSecs());
        method.getParams().setSoTimeout(timeout);
        httpConnManager.getParams().setConnectionTimeout(timeout);

        // ZAP: Check if a custom state is being used
        if (state != null) {
            // Make sure cookies are enabled
            method.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
            setProxyAuth(state);
        } else {
            setProxyAuth(requestClient.getState());
        }

        if (param.isUseProxy(hostName)) {
            if (hc == null) {
                hc = new HostConfiguration();
                hc.setHost(hostName, method.getURI().getPort(), method.getURI().getScheme());
            }
            hc.setProxy(param.getProxyChainName(), param.getProxyChainPort());
        }

        responseCode = requestClient.executeMethod(hc, method, state);

        return responseCode;
    }

    /**
     * Tells whether or not the given {@code method} has a {@code Connection} request header with
     * {@code Upgrade} value.
     *
     * @param method the method that will be checked
     * @return {@code true} if the {@code method} has a connection upgrade, {@code false} otherwise
     */
    private static boolean isConnectionUpgrade(HttpMethod method) {
        Header connectionHeader = method.getRequestHeader("connection");
        if (connectionHeader == null) {
            return false;
        }
        return connectionHeader.getValue().toLowerCase(Locale.ROOT).contains("upgrade");
    }

    public void shutdown() {
        if (httpConnManager != null) {
            httpConnManager.shutdown();
        }
    }

    /**
     * Downloads the response (body) to the given file.
     *
     * <p>The body in the given {@code message} will be empty.
     *
     * @param message the message containing the request to send.
     * @param file the file where to save the response body.
     * @throws IOException if an error occurred while sending the request or while downloading.
     * @since 2.12.0
     * @see #setFollowRedirect(boolean)
     */
    public void sendAndReceive(HttpMessage message, Path file) throws IOException {
        sendAndReceive(
                message,
                followRedirect,
                (msg, method) -> {
                    if (followRedirect.isFollowRedirects()
                            && isRedirectionNeeded(msg.getResponseHeader().getStatusCode())) {
                        DEFAULT_BODY_CONSUMER.accept(message, method);
                        return;
                    }

                    HttpResponseHeader header = msg.getResponseHeader();
                    try (FileChannel channel =
                                    (FileChannel)
                                            Files.newByteChannel(
                                                    file,
                                                    EnumSet.of(
                                                            StandardOpenOption.WRITE,
                                                            StandardOpenOption.CREATE,
                                                            StandardOpenOption.TRUNCATE_EXISTING));
                            InputStream is = method.getResponseBodyAsStream()) {
                        long totalRead = 0;
                        while ((totalRead +=
                                        channel.transferFrom(
                                                Channels.newChannel(is), totalRead, 1 << 24))
                                < header.getContentLength()) ;
                    }
                });
    }

    public void sendAndReceive(HttpMessage msg) throws IOException {
        sendAndReceive(msg, followRedirect);
    }

    /**
     * Send and receive a HttpMessage.
     *
     * @param msg
     * @param isFollowRedirect
     * @throws HttpException
     * @throws IOException
     * @see #sendAndReceive(HttpMessage, HttpRequestConfig)
     */
    public void sendAndReceive(HttpMessage msg, boolean isFollowRedirect) throws IOException {
        sendAndReceive(msg, isFollowRedirect ? FOLLOW_REDIRECTS : NO_REDIRECTS);
    }

    private void notifyRequestListeners(HttpMessage msg) {
        if (IN_LISTENER.get() != null) {
            // This is a request from one of the listeners - prevent infinite recursion
            return;
        }
        try {
            IN_LISTENER.set(true);
            for (HttpSenderListener listener : listeners) {
                try {
                    listener.onHttpRequestSend(msg, initiator, this);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } finally {
            IN_LISTENER.remove();
        }
    }

    private void notifyResponseListeners(HttpMessage msg) {
        if (IN_LISTENER.get() != null) {
            // This is a request from one of the listeners - prevent infinite recursion
            return;
        }
        try {
            IN_LISTENER.set(true);
            for (HttpSenderListener listener : listeners) {
                try {
                    listener.onHttpResponseReceive(msg, initiator, this);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } finally {
            IN_LISTENER.remove();
        }
    }

    /**
     * Gets the user set in this {@code HttpSender} if any, otherwise the one in the given {@code
     * HttpMessage}.
     *
     * @param msg usually the message being sent, that might have a user.
     * @return the user set in the {@code HttpSender} or in the given {@code HttpMessage}. Might be
     *     {@code null} if no user set.
     * @throws NullPointerException if the given message is {@code null}.
     * @since 2.4.1
     * @see #setUser(User)
     * @see HttpMessage#getRequestingUser()
     */
    public User getUser(HttpMessage msg) {
        if (this.user != null) {
            return user;
        }
        return msg.getRequestingUser();
    }

    private void sendAuthenticated(
            HttpMessage msg, HttpMethodParams params, ResponseBodyConsumer responseBodyConsumer)
            throws IOException {
        // Modify the request message if a 'Requesting User' has been set
        User forceUser = this.getUser(msg);
        if (forceUser != null) {
            if (initiator == AUTHENTICATION_POLL_INITIATOR) {
                forceUser.processMessageToMatchAuthenticatedSession(msg);
            } else if (initiator != AUTHENTICATION_INITIATOR) {
                forceUser.processMessageToMatchUser(msg);
            }
        }

        log.debug("Sending message to: " + msg.getRequestHeader().getURI().toString());
        // Send the message
        send(msg, params, responseBodyConsumer);

        // If there's a 'Requesting User', make sure the response corresponds to an authenticated
        // session and, if not, attempt a reauthentication and try again
        if (initiator != AUTHENTICATION_INITIATOR
                && initiator != AUTHENTICATION_POLL_INITIATOR
                && forceUser != null
                && !msg.getRequestHeader().isImage()
                && !forceUser.isAuthenticated(msg)) {
            log.debug(
                    "First try to send authenticated message failed for "
                            + msg.getRequestHeader().getURI()
                            + ". Authenticating and trying again...");
            forceUser.queueAuthentication(msg);
            forceUser.processMessageToMatchUser(msg);
            send(msg, params, responseBodyConsumer);
        } else log.debug("SUCCESSFUL");
    }

    private void send(
            HttpMessage msg, HttpMethodParams params, ResponseBodyConsumer responseBodyConsumer)
            throws IOException {
        HttpMethod method = null;
        HttpResponseHeader resHeader = null;

        try {
            method = runMethod(msg, params);
            // successfully executed;
            resHeader = HttpMethodHelper.getHttpResponseHeader(method);
            resHeader.setHeader(
                    HttpHeader.TRANSFER_ENCODING,
                    null); // replaceAll("Transfer-Encoding: chunked\r\n",
            // "");
            msg.setResponseHeader(resHeader);

            responseBodyConsumer.accept(msg, method);
            msg.setResponseFromTargetHost(true);

            // ZAP: set method to retrieve upgraded channel later
            if (method instanceof ZapGetMethod) {
                msg.setUserObject(method);
            }
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    private HttpMethod runMethod(HttpMessage msg, HttpMethodParams params) throws IOException {
        HttpMethod method = null;
        // no more retry
        method = helper.createRequestMethod(msg.getRequestHeader(), msg.getRequestBody(), params);
        method.setFollowRedirects(false);

        HttpState state = null;
        User forceUser = this.getUser(msg);
        if (forceUser != null) {
            state = forceUser.getCorrespondingHttpState();
        }
        executeMethodImpl(method, state);

        HttpMethodHelper.updateHttpRequestHeaderSent(msg.getRequestHeader(), method);

        return method;
    }

    public void setFollowRedirect(boolean followRedirect) {
        this.followRedirect = followRedirect ? FOLLOW_REDIRECTS : NO_REDIRECTS;
    }

    /**
     * @return Returns the userAgent.
     * @deprecated (2.12.0) No longer supported, it returns an empty string.
     * @see #setUserAgent(String)
     */
    @Deprecated
    public static String getUserAgent() {
        return "";
    }

    /**
     * @param userAgent The userAgent to set.
     * @deprecated (2.12.0) No longer supported, use a {@link HttpSenderListener} to actually set
     *     the user agent.
     */
    @Deprecated
    public static void setUserAgent(String userAgent) {}

    @SuppressWarnings("deprecation")
    private void setCommonManagerParams(MultiThreadedHttpConnectionManager mgr) {
        mgr.getParams().setStaleCheckingEnabled(true);

        // Set to arbitrary large values to prevent locking
        mgr.getParams().setDefaultMaxConnectionsPerHost(10000);
        mgr.getParams().setMaxTotalConnections(200000);

        // to use for HttpClient 3.0.1
        // mgr.getParams().setDefaultMaxConnectionsPerHost((Constant.MAX_HOST_CONNECTION > 5) ? 15 :
        // 3*Constant.MAX_HOST_CONNECTION);

        // mgr.getParams().setMaxTotalConnections(mgr.getParams().getDefaultMaxConnectionsPerHost()*10);

        // mgr.getParams().setConnectionTimeout(60000); // use default

    }

    /*
     * Send and receive a HttpMessage.
     *
     * @param msg
     *
     * @param isFollowRedirect
     *
     * @throws HttpException
     *
     * @throws IOException
     */
    /*
     * private void sendAndReceive(HttpMessage msg, boolean isFollowRedirect, HttpOutputStream pipe,
     * byte[] buf) throws HttpException, IOException { log.debug("sendAndReceive " +
     * msg.getRequestHeader().getMethod() + " " + msg.getRequestHeader().getURI() + " start");
     * msg.setTimeSentMillis(System.currentTimeMillis());
     *
     * try { if (!isFollowRedirect || !
     * (msg.getRequestHeader().getMethod().equalsIgnoreCase(HttpRequestHeader.POST) ||
     * msg.getRequestHeader().getMethod().equalsIgnoreCase(HttpRequestHeader.PUT)) ) { send(msg,
     * isFollowRedirect, pipe, buf); return; } else { send(msg, false, pipe, buf); }
     *
     * HttpMessage temp = msg.cloneAll(); // POST/PUT method cannot be redirected by library. Need
     * to follow by code
     *
     * // loop 1 time only because httpclient can handle redirect itself after first GET. for (int
     * i=0; i<1 && (HttpStatusCode.isRedirection(temp.getResponseHeader().getStatusCode()) &&
     * temp.getResponseHeader().getStatusCode() != HttpStatusCode.NOT_MODIFIED); i++) { String
     * location = temp.getResponseHeader().getHeader(HttpHeader.LOCATION); URI baseUri =
     * temp.getRequestHeader().getURI(); URI newLocation = new URI(baseUri, location, false);
     * temp.getRequestHeader().setURI(newLocation);
     *
     * temp.getRequestHeader().setMethod(HttpRequestHeader.GET);
     * temp.getRequestHeader().setContentLength(0); send(temp, true, pipe, buf); }
     *
     * msg.setResponseHeader(temp.getResponseHeader()); msg.setResponseBody(temp.getResponseBody());
     *
     * } finally { msg.setTimeElapsedMillis((int)
     * (System.currentTimeMillis()-msg.getTimeSentMillis())); log.debug("sendAndReceive " +
     * msg.getRequestHeader().getMethod() + " " + msg.getRequestHeader().getURI() + " took " +
     * msg.getTimeElapsedMillis()); } }
     */

    /*
     * Do not use this unless sure what is doing. This method works but proxy may skip the pipe
     * without properly handle the filter.
     *
     * @param msg
     *
     * @param isFollowRedirect
     *
     * @param pipe
     *
     * @param buf
     *
     * @throws HttpException
     *
     * @throws IOException
     */
    /*
     * private void send(HttpMessage msg, boolean isFollowRedirect, HttpOutputStream pipe, byte[]
     * buf) throws HttpException, IOException { HttpMethod method = null; HttpResponseHeader
     * resHeader = null;
     *
     * try { method = runMethod(msg, isFollowRedirect); // successfully executed; resHeader =
     * HttpMethodHelper.getHttpResponseHeader(method);
     * resHeader.setHeader(HttpHeader.TRANSFER_ENCODING, null); //
     * replaceAll("Transfer-Encoding: chunked\r\n", ""); msg.setResponseHeader(resHeader);
     * msg.getResponseBody().setCharset(resHeader.getCharset()); msg.getResponseBody().setLength(0);
     *
     * // process response for each listener
     *
     * pipe.write(msg.getResponseHeader()); pipe.flush();
     *
     * if (msg.getResponseHeader().getContentLength() >= 0 &&
     * msg.getResponseHeader().getContentLength() < 20480) { // save time expanding buffer in
     * HttpBody if (msg.getResponseHeader().getContentLength() > 0) {
     * msg.getResponseBody().setBody(method.getResponseBody()); pipe.write(msg.getResponseBody());
     * pipe.flush();
     *
     * } } else { //byte[] buf = new byte[4096]; InputStream in = method.getResponseBodyAsStream();
     *
     * int len = 0; while (in != null && (len = in.read(buf)) > 0) { pipe.write(buf, 0, len);
     * pipe.flush();
     *
     * msg.getResponseBody().append(buf, len); } } } finally { if (method != null) {
     * method.releaseConnection(); } } }
     */

    /**
     * Adds the given listener to be notified of each message sent/received by each {@code
     * HttpSender}.
     *
     * <p>The listener might be notified concurrently.
     *
     * @param listener the listener to add.
     * @since 2.0.0
     * @throws NullPointerException if the given listener is {@code null}.
     */
    public static void addListener(HttpSenderListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
        Collections.sort(listeners, getListenersComparator());
    }

    /**
     * Removes the given listener.
     *
     * @param listener the listener to remove.
     * @since 2.0.0
     * @throws NullPointerException if the given listener is {@code null}.
     */
    public static void removeListener(HttpSenderListener listener) {
        Objects.requireNonNull(listener);
        listeners.remove(listener);
    }

    private static Comparator<HttpSenderListener> getListenersComparator() {
        if (listenersComparator == null) {
            createListenersComparator();
        }

        return listenersComparator;
    }

    private static synchronized void createListenersComparator() {
        if (listenersComparator == null) {
            listenersComparator =
                    new Comparator<HttpSenderListener>() {

                        @Override
                        public int compare(HttpSenderListener o1, HttpSenderListener o2) {
                            int order1 = o1.getListenerOrder();
                            int order2 = o2.getListenerOrder();

                            if (order1 < order2) {
                                return -1;
                            } else if (order1 > order2) {
                                return 1;
                            }

                            return 0;
                        }
                    };
        }
    }

    /**
     * Set the user to scan as. If null then the current session will be used.
     *
     * @param user
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * @return the HTTP client implementation.
     * @deprecated (2.8.0) Do not use, this exposes implementation details which might change
     *     without warning. It will be removed in a following version.
     */
    @Deprecated
    public HttpClient getClient() {
        return this.client;
    }

    /**
     * Sets whether or not the authentication headers ("Authorization" and "Proxy-Authorization")
     * already present in the request should be removed if received an authentication challenge
     * (status codes 401 and 407).
     *
     * <p>If {@code true} new authentication headers will be generated and the old ones removed
     * otherwise the authentication headers already present in the request will be used to
     * authenticate.
     *
     * <p>Default is {@code false}, i.e. use the headers already present in the request header.
     *
     * <p>Processes that reuse messages previously sent should consider setting this to {@code
     * true}, otherwise new authentication challenges might fail.
     *
     * @param removeHeaders {@code true} if the the authentication headers already present should be
     *     removed when challenged, {@code false} otherwise
     */
    public void setRemoveUserDefinedAuthHeaders(boolean removeHeaders) {
        client.getParams()
                .setBooleanParameter(
                        HttpMethodDirector.PARAM_REMOVE_USER_DEFINED_AUTH_HEADERS, removeHeaders);
    }

    /**
     * Sets the maximum number of retries of an unsuccessful request caused by I/O errors.
     *
     * <p>The default number of retries is 3.
     *
     * @param retries the number of retries
     * @throws IllegalArgumentException if {@code retries} is negative.
     * @since 2.4.0
     */
    public void setMaxRetriesOnIOError(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException(
                    "Parameter retries must be greater or equal to zero.");
        }

        HttpMethodRetryHandler retryHandler = new DefaultHttpMethodRetryHandler(retries, false);
        client.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, retryHandler);
    }

    /**
     * Sets the maximum number of redirects that will be followed before failing with an exception.
     *
     * <p>The default maximum number of redirects is 100.
     *
     * @param maxRedirects the maximum number of redirects
     * @throws IllegalArgumentException if {@code maxRedirects} is negative.
     * @since 2.4.0
     */
    public void setMaxRedirects(int maxRedirects) {
        if (maxRedirects < 0) {
            throw new IllegalArgumentException(
                    "Parameter maxRedirects must be greater or equal to zero.");
        }
        client.getParams().setIntParameter(HttpClientParams.MAX_REDIRECTS, maxRedirects);
    }

    /**
     * Sets whether or not circular redirects are allowed.
     *
     * <p>Circular redirects happen when a request redirects to itself, or when a same request was
     * already accessed in a chain of redirects.
     *
     * <p>Since 2.5.0, the default is to allow circular redirects.
     *
     * @param allow {@code true} if circular redirects should be allowed, {@code false} otherwise
     * @since 2.4.0
     * @deprecated (2.12.0) No longer supported, the circular redirects are allowed always. If
     *     needed they can be prevented with a custom {@link HttpRedirectionValidator}.
     */
    @Deprecated
    public void setAllowCircularRedirects(boolean allow) {}

    /**
     * Sends the request of given HTTP {@code message} with the given configurations.
     *
     * @param message the message that will be sent
     * @param requestConfig the request configurations.
     * @throws IllegalArgumentException if any of the parameters is {@code null}
     * @throws IOException if an error occurred while sending the message or following the
     *     redirections
     * @since 2.6.0
     * @see #sendAndReceive(HttpMessage, boolean)
     */
    public void sendAndReceive(HttpMessage message, HttpRequestConfig requestConfig)
            throws IOException {
        sendAndReceive(message, requestConfig, DEFAULT_BODY_CONSUMER);
    }

    private void sendAndReceive(
            HttpMessage message,
            HttpRequestConfig requestConfig,
            ResponseBodyConsumer responseBodyConsumer)
            throws IOException {
        if (message == null) {
            throw new IllegalArgumentException("Parameter message must not be null.");
        }
        if (requestConfig == null) {
            throw new IllegalArgumentException("Parameter requestConfig must not be null.");
        }

        sendAndReceiveImpl(message, requestConfig, responseBodyConsumer);

        if (requestConfig.isFollowRedirects()) {
            followRedirections(message, requestConfig, responseBodyConsumer);
        }
    }

    /**
     * Helper method that sends the request of the given HTTP {@code message} with the given
     * configurations.
     *
     * <p>No redirections are followed (see {@link #followRedirections(HttpMessage,
     * HttpRequestConfig)}).
     *
     * @param message the message that will be sent.
     * @param requestConfig the request configurations.
     * @throws IOException if an error occurred while sending the message or following the
     *     redirections.
     */
    private void sendAndReceiveImpl(
            HttpMessage message,
            HttpRequestConfig requestConfig,
            ResponseBodyConsumer responseBodyConsumer)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(
                    "Sending "
                            + message.getRequestHeader().getMethod()
                            + " "
                            + message.getRequestHeader().getURI());
        }
        message.setTimeSentMillis(System.currentTimeMillis());

        try {
            if (requestConfig.isNotifyListeners()) {
                notifyRequestListeners(message);
            }

            HttpMethodParams params = null;
            if (requestConfig.getSoTimeout() != HttpRequestConfig.NO_VALUE_SET) {
                params = new HttpMethodParams();
                params.setSoTimeout(requestConfig.getSoTimeout());
            }
            sendAuthenticated(message, params, responseBodyConsumer);

        } finally {
            message.setTimeElapsedMillis(
                    (int) (System.currentTimeMillis() - message.getTimeSentMillis()));

            if (log.isDebugEnabled()) {
                log.debug(
                        "Received response after "
                                + message.getTimeElapsedMillis()
                                + "ms for "
                                + message.getRequestHeader().getMethod()
                                + " "
                                + message.getRequestHeader().getURI());
            }

            if (requestConfig.isNotifyListeners()) {
                notifyResponseListeners(message);
            }
        }
    }

    /**
     * Follows redirections using the response of the given {@code message}. The {@code validator}
     * in the given request configuration will be called for each redirection received. After the
     * call to this method the given {@code message} will have the contents of the last response
     * received (possibly the response of a redirection).
     *
     * <p>The validator is notified of each message sent and received (first message and
     * redirections followed, if any).
     *
     * @param message the message that will be sent, must not be {@code null}
     * @param requestConfig the request configuration that contains the validator responsible for
     *     validation of redirections, must not be {@code null}.
     * @throws IOException if an error occurred while sending the message or following the
     *     redirections
     * @see #isRedirectionNeeded(int)
     */
    private void followRedirections(
            HttpMessage message,
            HttpRequestConfig requestConfig,
            ResponseBodyConsumer responseBodyConsumer)
            throws IOException {
        HttpRedirectionValidator validator = requestConfig.getRedirectionValidator();
        validator.notifyMessageReceived(message);

        User requestingUser = getUser(message);
        HttpMessage redirectMessage = message;
        int maxRedirections =
                client.getParams().getIntParameter(HttpClientParams.MAX_REDIRECTS, 100);
        for (int i = 0;
                i < maxRedirections
                        && isRedirectionNeeded(redirectMessage.getResponseHeader().getStatusCode());
                i++) {
            URI newLocation = extractRedirectLocation(redirectMessage);
            if (newLocation == null || !validator.isValid(newLocation)) {
                return;
            }

            redirectMessage = redirectMessage.cloneAll();
            redirectMessage.setRequestingUser(requestingUser);
            redirectMessage.getRequestHeader().setURI(newLocation);

            if (isRequestRewriteNeeded(redirectMessage)) {
                redirectMessage.getRequestHeader().setMethod(HttpRequestHeader.GET);
                redirectMessage.getRequestHeader().setHeader(HttpHeader.CONTENT_TYPE, null);
                redirectMessage.getRequestHeader().setHeader(HttpHeader.CONTENT_LENGTH, null);
                redirectMessage.setRequestBody("");
            }

            sendAndReceiveImpl(redirectMessage, requestConfig, responseBodyConsumer);
            validator.notifyMessageReceived(redirectMessage);

            // Update the response of the (original) message
            message.setResponseHeader(redirectMessage.getResponseHeader());
            message.setResponseBody(redirectMessage.getResponseBody());
        }
    }

    /**
     * Tells whether or not a redirection is needed based on the given status code.
     *
     * <p>A redirection is needed if the status code is 301, 302, 303, 307 or 308.
     *
     * @param statusCode the status code that will be checked
     * @return {@code true} if a redirection is needed, {@code false} otherwise
     * @see #isRequestRewriteNeeded(HttpMessage)
     */
    private static boolean isRedirectionNeeded(int statusCode) {
        switch (statusCode) {
            case 301:
            case 302:
            case 303:
            case 307:
            case 308:
                return true;
            default:
                return false;
        }
    }

    /**
     * Tells whether or not the (original) request of the redirection, should be rewritten.
     *
     * <p>For status codes 301 and 302 the request should be changed from POST to GET when following
     * redirections, for status code 303 it should be changed to GET for all methods except GET/HEAD
     * (mimicking the behaviour of browsers, which per <a
     * href="https://tools.ietf.org/html/rfc7231#section-6.4">RFC 7231, Section 6.4</a> is now OK).
     *
     * @param message the message with the redirection.
     * @return {@code true} if the request should be rewritten, {@code false} otherwise
     * @see #isRedirectionNeeded(int)
     */
    private static boolean isRequestRewriteNeeded(HttpMessage message) {
        int statusCode = message.getResponseHeader().getStatusCode();
        String method = message.getRequestHeader().getMethod();
        if (statusCode == 301 || statusCode == 302) {
            return HttpRequestHeader.POST.equalsIgnoreCase(method);
        }
        return statusCode == 303
                && !(HttpRequestHeader.GET.equalsIgnoreCase(method)
                        || HttpRequestHeader.HEAD.equalsIgnoreCase(method));
    }

    /**
     * Extracts a {@code URI} from the {@code Location} header of the given HTTP {@code message}.
     *
     * <p>If there's no {@code Location} header this method returns {@code null}.
     *
     * @param message the HTTP message that will processed
     * @return the {@code URI} created from the value of the {@code Location} header, might be
     *     {@code null}
     * @throws InvalidRedirectLocationException if the value of {@code Location} header is not a
     *     valid {@code URI}
     */
    private static URI extractRedirectLocation(HttpMessage message)
            throws InvalidRedirectLocationException {
        String location = message.getResponseHeader().getHeader(HttpHeader.LOCATION);
        if (location == null) {
            if (log.isDebugEnabled()) {
                log.debug("No Location header found: " + message.getResponseHeader());
            }
            return null;
        }

        try {
            return new URI(message.getRequestHeader().getURI(), location, true);
        } catch (URIException ex) {
            try {
                // Handle redirect URLs that are unencoded
                return new URI(message.getRequestHeader().getURI(), location, false);
            } catch (URIException e) {
                throw new InvalidRedirectLocationException(
                        "Invalid redirect location: " + location, location, ex);
            }
        }
    }

    private interface ResponseBodyConsumer {

        void accept(HttpMessage message, HttpMethod method) throws IOException;
    }

    /**
     * A {@link ProtocolSocketFactory} for plain sockets.
     *
     * <p>Remote hostnames are not resolved if {@link HttpMethodDirector#PARAM_RESOLVE_HOSTNAME} is
     * {@code false}.
     */
    private static class ProtocolSocketFactoryImpl implements ProtocolSocketFactory {

        @Override
        public Socket createSocket(
                String host,
                int port,
                InetAddress localAddress,
                int localPort,
                HttpConnectionParams params)
                throws IOException {
            if (params == null) {
                throw new IllegalArgumentException("Parameters may not be null");
            }
            Socket socket = SocketFactory.getDefault().createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            SocketAddress remoteAddress;
            if (params.getBooleanParameter(HttpMethodDirector.PARAM_RESOLVE_HOSTNAME, true)) {
                remoteAddress = new InetSocketAddress(host, port);
            } else {
                remoteAddress = InetSocketAddress.createUnresolved(host, port);
            }
            socket.connect(remoteAddress, params.getConnectionTimeout());
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
                throws IOException {
            throw new UnsupportedOperationException(
                    "Method not supported, not required/called by Commons HttpClient library (version >= 3.0).");
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            throw new UnsupportedOperationException(
                    "Method not supported, not required/called by Commons HttpClient library (version >= 3.0).");
        }
    }
}
