package api.rollingdoor.websocket;

import api.rollingdoor.repository.RemoteDeviceRepository;
import api.rollingdoor.service.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final RemoteDeviceRepository deviceRepository;

    private static final String DEVICE_ID_KEY = "deviceId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Lấy deviceId (MAC Address) từ URL
        // ESP32 sẽ kết nối đến: ws://your-server.com/ws/device?deviceId=AA:BB:CC...
        String deviceId = getDeviceIdFromSession(session);

        if (deviceId == null) {
            log.warn("Connection attempt without deviceId. Closing session.");
            session.close(CloseStatus.BAD_DATA.withReason("Missing deviceId query parameter"));
            return;
        }

        // Check 1: Thiết bị này có tồn tại trong DB không?
        if (!deviceRepository.findByDeviceId(deviceId).isPresent()) {
            log.warn("Unknown device tried to connect: {}. Closing session.", deviceId);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Device not registered"));
            return;
        }

        // Check 2: Đăng ký session
        sessionManager.register(deviceId, session);
        session.getAttributes().put(DEVICE_ID_KEY, deviceId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String deviceId = (String) session.getAttributes().get(DEVICE_ID_KEY);
        log.info("Received message from {}: {}", deviceId, message.getPayload());
        // (Xử lý tin nhắn từ ESP32 nếu cần)
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = (String) session.getAttributes().get(DEVICE_ID_KEY);
        if (deviceId != null) {
            sessionManager.unregister(deviceId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    private String getDeviceIdFromSession(WebSocketSession session) {
        try {
            UriComponents uriComponents = UriComponentsBuilder.fromUri(session.getUri()).build();
            List<String> params = uriComponents.getQueryParams().get(DEVICE_ID_KEY);
            if (params != null && !params.isEmpty()) {
                return params.get(0); // Lấy giá trị đầu tiên của ?deviceId=...
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to parse deviceId from URI: {}", session.getUri(), e);
            return null;
        }
    }
}