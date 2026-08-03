package com.company.dss.mq;

import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.company.dss.authentication.TokenHolder;
import com.company.dss.common.DssApiPaths;
import com.company.dss.config.DssProperties;
import com.company.dss.dto.mq.MqConfigData;
import com.company.dss.exception.DssClientException;
import com.company.dss.util.DssCryptoUtils;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Connexion ActiveMQ DSS — implémentation conforme à la doc officielle
 * {@code 6.3.2 MQ Connection Examples} (activemq-client 5.15.12 + CustomerMqFactory).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqConnectionService {

    // verifyHostName=false : requis (IP privée, cert auto-signé DSS)
    private static final String MQ_URL_PREFIX = "ssl://%s?socket.verifyHostName=false";
    private static final String TOPIC = "topic";

    private final DssProperties dssProperties;
    private final TokenHolder tokenHolder;
    private final MqConfigClient mqConfigClient;
    private final MqMessageHandler messageHandler;
    private final MqEventBuffer eventBuffer;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<String> brokerUrl = new AtomicReference<>();
    private final List<String> subscribedTopics = new ArrayList<>();

    private Connection connection;
    private Session session;

    public synchronized void start() {
        if (!dssProperties.isMqEnabled()) {
            log.info(">>> [MQ] désactivé (dss.mq-enabled=false)");
            return;
        }
        stop();

        if (!tokenHolder.hasValidToken()) {
            throw new DssClientException("Impossible de démarrer MQ : pas de token DSS");
        }
        if (!tokenHolder.hasMqCrypto()) {
            throw new DssClientException(
                    "Impossible de démarrer MQ : clés AES absentes (login sans publickey plateforme)");
        }

        String userId = tokenHolder.getUserId()
                .orElseThrow(() -> new DssClientException("userId DSS manquant pour les topics MQ"));
        String userGroupId = tokenHolder.getUserGroupId().filter(StringUtils::hasText).orElse(null);

        try {
            // Doc 3.2.1 GetMqConfig + 6.3.2 : password AES à déchiffrer avec secretKey/secretVector du login
            MqConfigData config = mqConfigClient.fetchConfig();
            if (!StringUtils.hasText(config.password())) {
                throw new DssClientException(
                        "Password MQ null — vérifier secretKey/secretVector au second login");
            }
            String userPassword = DssCryptoUtils.decryptAesCbcHex(
                    config.password(),
                    tokenHolder.getAesSecretKey().orElseThrow(),
                    tokenHolder.getAesSecretVector().orElseThrow()
            );
            String mqUrl = String.format(MQ_URL_PREFIX, config.addr());
            log.info(">>> [MQ] Config reçue — addr={}, enableTls={}, user={}, passwordLen={}",
                    config.addr(), config.enableTls(), config.userName(), userPassword.length());

            listenMqMessages(mqUrl, config.userName(), userPassword, userId, userGroupId);
            connected.set(true);
            lastError.set(null);
            brokerUrl.set(mqUrl);
            log.info(">>> [MQ] CONNECTÉ — {}", mqUrl);
        } catch (DssClientException ex) {
            connected.set(false);
            lastError.set(ex.getMessage());
            stopQuietly();
            throw ex;
        } catch (Exception ex) {
            connected.set(false);
            Throwable root = rootCause(ex);
            String detail = ex.getMessage() + " | cause=" + root.getClass().getSimpleName() + ": " + root.getMessage();
            lastError.set(detail);
            log.error(">>> [MQ] ÉCHEC : {}", detail, ex);
            stopQuietly();
            throw new DssClientException("Échec connexion MQ DSS : " + detail, ex);
        }
    }

    /**
     * Équivalent de {@code listenMqMessage(...)} dans la doc Dahua 6.3.2.
     */
    private void listenMqMessages(
            String mqUrl,
            String userName,
            String passWord,
            String userId,
            String userGroupId
    ) throws Exception {
        // Doc : CustomerMqFactory extends ActiveMQSslConnectionFactory
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");
        CustomerMqFactory factory = new CustomerMqFactory();
        factory.setUserName(userName);
        factory.setPassword(passWord);
        factory.setBrokerURL(mqUrl);
        // Ceinture + bretelles : certains runtimes n'appellent pas createTrustManager()
        factory.setKeyAndTrustManagers(null, factory.createTrustManager(), new java.security.SecureRandom());

        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        List<String> topics = new ArrayList<>();
        // Doc 2.2 : topics OpenWire (points, pas slashes)
        topics.add(DssApiPaths.MQ_ALARM_TOPIC_PREFIX + userId);
        if (StringUtils.hasText(userGroupId)) {
            topics.add(DssApiPaths.MQ_ALARM_GROUP_TOPIC_PREFIX + userGroupId);
        }
        topics.add(DssApiPaths.MQ_EVENT_TOPIC_PREFIX + userId);
        topics.add(DssApiPaths.MQ_COMMON_TOPIC);

        subscribedTopics.clear();
        for (String topicName : topics) {
            MessageConsumer consumer = session.createConsumer(new ActiveMQTopic(topicName));
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage textMessage) {
                        messageHandler.handle(topicName, textMessage.getText());
                    }
                } catch (JMSException ex) {
                    log.warn(">>> [MQ] Erreur lecture sur {} : {}", topicName, ex.getMessage());
                }
            });
            subscribedTopics.add(topicName);
            log.info(">>> [MQ] Abonné au topic {}", topicName);
        }
        log.info(">>> [MQ] Écoute active — en attente d'événements People Counting...");
    }

    public synchronized void stop() {
        stopQuietly();
        connected.set(false);
        subscribedTopics.clear();
    }

    @PreDestroy
    public void onShutdown() {
        stop();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getBrokerUrl() {
        return brokerUrl.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    public List<String> getSubscribedTopics() {
        return List.copyOf(subscribedTopics);
    }

    public int capturedEventCount() {
        return eventBuffer.size();
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private void stopQuietly() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception ignored) {
            // ignore
        } finally {
            session = null;
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
            // ignore
        } finally {
            connection = null;
        }
    }

    /**
     * Comme la doc Dahua 6.3.2 — CustomerMqFactory (activemq-client 5.15+/5.17).
     * createTransport() invoque createTrustManager() pour le SSL OpenWire.
     */
    static final class CustomerMqFactory extends ActiveMQSslConnectionFactory {

        @Override
        public TrustManager[] createTrustManager() {
            return new TrustManager[]{
                    new X509ExtendedTrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };
        }
    }
}
