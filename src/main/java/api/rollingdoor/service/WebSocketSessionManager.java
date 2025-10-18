package api.rollingdoor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String deviceId, WebSocketSession session) {
        WebSocketSession oldSession = sessions.remove(deviceId);
        if (oldSession != null && oldSession.isOpen()) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.warn("Error closing old session for device: {}", deviceId, e);
            }
        }
        sessions.put(deviceId, session);
        log.info("Device connected: {}. Total devices online: {}", deviceId, sessions.size());
    }

    public void unregister(String deviceId) {
        if (deviceId != null) {
            sessions.remove(deviceId);
            log.info("Device disconnected: {}. Total devices online: {}", deviceId, sessions.size());
        }
    }

    public boolean send(String deviceId, String payload) {
        WebSocketSession session = sessions.get(deviceId);

        if (session != null && session.isOpen()) {
            try {
                log.info("Sending command '{}' to device {}", payload, deviceId);
                session.sendMessage(new TextMessage(payload));
                return true;
            } catch (IOException e) {
                log.error("Failed to send message to device: {}", deviceId, e);
                return false;
            }
        } else {
            log.warn("Device {} is offline. Command '{}' not sent.", deviceId, payload);
            return false;
        }
    }
}