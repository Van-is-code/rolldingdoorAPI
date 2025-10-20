package api.rollingdoor.service;

import api.rollingdoor.dto.request.*;
import api.rollingdoor.dto.response.DeviceResponse;
import api.rollingdoor.dto.response.InviteResponse;
import api.rollingdoor.entity.*;
import api.rollingdoor.exception.ResourceNotFoundException;
import api.rollingdoor.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j // Bật logger
public class DeviceService {

    private final RemoteDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final UserDeviceAccessRepository accessRepository;
    private final InvitePinRepository pinRepository;
    private final PinGeneratorService pinGeneratorService;
    private final PasswordEncoder passwordEncoder;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===================================
    // HÀM TIỆN ÍCH NỘI BỘ (PRIVATE)
    // ===================================

    private User getAuthenticatedUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    private RemoteDevice getDeviceByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + deviceId));
    }

    private void checkAdminPermission(User user, RemoteDevice device) {
        accessRepository.findByUserAndDeviceAndStatus(user, device, AccessStatus.ACCEPTED)
                .filter(access -> access.getRole() == DeviceRole.ADMIN)
                .orElseThrow(() -> {
                    log.warn("Permission denied: User '{}' is not ADMIN for device '{}'", user.getUsername(), device.getDeviceId());
                    return new AccessDeniedException("User is not an accepted ADMIN for this device");
                });
        log.debug("Admin permission verified for User '{}' on Device '{}'", user.getUsername(), device.getDeviceId());
    }

    private UserDeviceAccess checkAcceptedPermission(User user, RemoteDevice device) {
        return accessRepository.findByUserAndDeviceAndStatus(user, device, AccessStatus.ACCEPTED)
                .orElseThrow(() -> {
                    log.warn("Permission denied: User '{}' does not have accepted access to device '{}'", user.getUsername(), device.getDeviceId());
                    return new AccessDeniedException("User does not have accepted access to this device");
                });
    }

    /**
     * Hàm nội bộ tìm bản ghi UserDeviceAccess bằng ID, đảm bảo nó tồn tại.
     */
    private UserDeviceAccess findAccessRecordById(Long accessId) {
        return accessRepository.findById(accessId)
                .orElseThrow(() -> new ResourceNotFoundException("Access record not found with ID: " + accessId));
    }

    // ===================================
    // CÁC LUỒNG CHÍNH (Đã có)
    // ===================================

    @Transactional
    public void claimDevice(ClaimRequest req, String username) {
        User user = getAuthenticatedUser(username);
        log.info("User '{}' attempting to claim device '{}'", username, req.getDeviceId());
        deviceRepository.findByDeviceId(req.getDeviceId()).ifPresent(existingDevice -> {
            log.warn("Claim failed: Device '{}' already exists.", req.getDeviceId());
            throw new IllegalArgumentException("Device is already registered. Use 'Recover Admin' if you lost access.");
        });
        RemoteDevice device = new RemoteDevice();
        device.setDeviceId(req.getDeviceId());
        device.setDevicePassword(passwordEncoder.encode(req.getDevicePassword()));
        device.setName(req.getDeviceId());
        RemoteDevice savedDevice = deviceRepository.save(device);
        log.info("Device '{}' created with ID: {}", savedDevice.getDeviceId(), savedDevice.getId());
        UserDeviceAccess access = new UserDeviceAccess(user, savedDevice, DeviceRole.ADMIN, AccessStatus.ACCEPTED);
        accessRepository.save(access);
        log.info("ADMIN role granted to user '{}' for device '{}'", username, savedDevice.getDeviceId());
    }

    @Transactional
    public InviteResponse generateInvite(String deviceId, String username) {
        User admin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(deviceId);
        log.info("Admin '{}' generating invite PIN for device '{}'", username, deviceId);
        checkAdminPermission(admin, device);
        String pin = pinGeneratorService.generateUniquePin();
        Instant expiryDate = Instant.now().plus(5, ChronoUnit.MINUTES);
        InvitePin invitePin = new InvitePin();
        invitePin.setPin(pin);
        invitePin.setDevice(device);
        invitePin.setExpiresAt(expiryDate);
        pinRepository.save(invitePin);
        log.info("Invite PIN '{}' created for device '{}', expires at {}", pin, deviceId, expiryDate);
        return new InviteResponse(pin, 300);
    }

    @Transactional
    public void requestAccess(AccessRequest req, String username) {
        User member = getAuthenticatedUser(username);
        log.info("User '{}' requesting access to device '{}' using PIN '{}'", username, req.getDeviceId(), req.getPin());
        InvitePin invitePin = pinRepository.findByPin(req.getPin())
                .orElseThrow(() -> {
                    log.warn("Access request failed: Invalid PIN '{}'", req.getPin());
                    return new ResourceNotFoundException("Invalid or expired PIN");
                });
        if (invitePin.getExpiresAt().isBefore(Instant.now())) {
            pinRepository.delete(invitePin);
            log.warn("Access request failed: PIN '{}' has expired.", req.getPin());
            throw new AccessDeniedException("PIN has expired");
        }
        if (!invitePin.getDevice().getDeviceId().equals(req.getDeviceId())) {
            log.warn("Access request failed: PIN '{}' is not valid for device '{}'", req.getPin(), req.getDeviceId());
            throw new AccessDeniedException("PIN is not valid for this device");
        }
        RemoteDevice device = invitePin.getDevice();
        accessRepository.findByUserAndDevice(member, device)
                .ifPresent(existingAccess -> {
                    if (existingAccess.getStatus() == AccessStatus.PENDING) {
                        log.warn("Access request failed: User '{}' already has a PENDING request for device '{}'", username, req.getDeviceId());
                        throw new IllegalArgumentException("You already have a pending request for this device.");
                    }
                    if (existingAccess.getStatus() == AccessStatus.ACCEPTED) {
                        log.warn("Access request failed: User '{}' already has ACCEPTED access to device '{}'", username, req.getDeviceId());
                        throw new IllegalArgumentException("You already have access to this device.");
                    }
                });
        UserDeviceAccess accessRequest = new UserDeviceAccess(member, device, DeviceRole.MEMBER, AccessStatus.PENDING);
        accessRepository.save(accessRequest);
        log.info("Access request created for user '{}' on device '{}' (ID: {}). Status: PENDING", username, req.getDeviceId(), accessRequest.getId());
        pinRepository.delete(invitePin);
        log.debug("Invite PIN '{}' deleted after use.", req.getPin());
        // TODO: Gửi Push Notification cho Admins
    }

    @Transactional
    public void approveAccess(ApprovalRequest req, String username) {
        User admin = getAuthenticatedUser(username);
        log.info("Admin '{}' attempting to approve access request ID: {}", username, req.getAccessRequestId());
        UserDeviceAccess accessRequest = accessRepository.findById(req.getAccessRequestId())
                .filter(access -> access.getStatus() == AccessStatus.PENDING)
                .orElseThrow(() -> {
                    log.warn("Approval failed: Access request ID '{}' not found or not PENDING.", req.getAccessRequestId());
                    return new ResourceNotFoundException("Access request not found or already processed");
                });
        checkAdminPermission(admin, accessRequest.getDevice());
        accessRequest.setStatus(AccessStatus.ACCEPTED);
        accessRepository.save(accessRequest);
        log.info("Access request ID '{}' for user '{}' on device '{}' approved by Admin '{}'",
                req.getAccessRequestId(), accessRequest.getUser().getUsername(), accessRequest.getDevice().getDeviceId(), username);
        // TODO: Gửi thông báo cho người được duyệt
    }

    @Transactional(readOnly = true)
    public void sendCommand(CommandRequest req, String username) {
        User user = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(req.getDeviceId());
        log.info("User '{}' sending command '{}' to device '{}'", username, req.getAction(), req.getDeviceId());
        UserDeviceAccess access = checkAcceptedPermission(user, device);
        log.debug("Permission verified for command. User role: {}", access.getRole());
        boolean sent = sessionManager.send(device.getDeviceId(), req.getAction());
        if (!sent) {
            log.warn("Command '{}' failed: Device '{}' is offline.", req.getAction(), req.getDeviceId());
            throw new ResourceNotFoundException("Device is offline. Command not sent.");
        }
        log.info("Command '{}' sent successfully to device '{}' via WebSocket.", req.getAction(), req.getDeviceId());
        // TODO: Ghi log lịch sử
    }

    @Transactional
    public void recoverAdmin(RecoverRequest req, String username) {
        User newAdmin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(req.getDeviceId());
        log.info("User '{}' attempting to recover ADMIN rights for device '{}' using master password.", username, req.getDeviceId());
        if (!passwordEncoder.matches(req.getMasterPassword(), device.getDevicePassword())) {
            log.warn("Admin recovery failed: Invalid master password provided for device '{}'", req.getDeviceId());
            throw new AccessDeniedException("Invalid master password");
        }
        log.debug("Master password verified for device '{}'", req.getDeviceId());
        List<UserDeviceAccess> oldAdmins = accessRepository.findByDeviceAndRole(device, DeviceRole.ADMIN);
        log.info("Found {} existing ADMIN(s) for device '{}'. Downgrading them to MEMBER.", oldAdmins.size(), req.getDeviceId());
        oldAdmins.forEach(oldAdminAccess -> {
            if (!oldAdminAccess.getUser().getId().equals(newAdmin.getId())) {
                oldAdminAccess.setRole(DeviceRole.MEMBER);
                accessRepository.save(oldAdminAccess);
                log.debug("Downgraded user '{}' to MEMBER.", oldAdminAccess.getUser().getUsername());
            }
        });
        UserDeviceAccess newAdminAccess = accessRepository.findByUserAndDevice(newAdmin, device)
                .orElse(new UserDeviceAccess(newAdmin, device, DeviceRole.ADMIN, AccessStatus.PENDING));
        newAdminAccess.setRole(DeviceRole.ADMIN);
        newAdminAccess.setStatus(AccessStatus.ACCEPTED);
        newAdminAccess.setCreatedAt(Instant.now());
        accessRepository.save(newAdminAccess);
        log.info("ADMIN rights granted/updated for user '{}' on device '{}'", username, req.getDeviceId());
    }

    @Transactional(readOnly = true)
    public void setOfflinePassword(SetOfflinePasswordRequest req, String username) {
        User admin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(req.getDeviceId());
        log.info("Admin '{}' attempting to set offline password for device '{}'", username, req.getDeviceId());
        checkAdminPermission(admin, device);
        Map<String, String> commandMap = Map.of(
                "type", "SET_OFFLINE_PASS",
                "password", req.getNewPassword()
        );
        String commandJson;
        try {
            commandJson = objectMapper.writeValueAsString(commandMap);
        } catch (JsonProcessingException e) {
            log.error("Error creating JSON for offline password command for device '{}'", req.getDeviceId(), e);
            throw new RuntimeException("Error creating command JSON", e);
        }
        log.debug("Sending offline password command to device '{}': {}", req.getDeviceId(), commandJson);
        boolean sent = sessionManager.send(device.getDeviceId(), commandJson);
        if (!sent) {
            log.warn("Set offline password failed: Device '{}' is offline.", req.getDeviceId());
            throw new ResourceNotFoundException("Device is offline. Cannot set offline password at this time.");
        }
        log.info("Offline password update command sent successfully to device '{}'.", req.getDeviceId());
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getUserDevices(String username) {
        User user = getAuthenticatedUser(username);
        log.debug("Fetching accepted devices for user '{}'", username);
        List<UserDeviceAccess> accesses = accessRepository.findByUserAndStatus(user, AccessStatus.ACCEPTED);
        log.info("User '{}' has access to {} devices.", username, accesses.size());
        return accesses.stream()
                .map(access -> new DeviceResponse(
                        access.getDevice().getDeviceId(),
                        access.getDevice().getName(),
                        access.getRole().name()
                ))
                .collect(Collectors.toList());
    }


    // ===================================
    // CÁC HÀM MỚI
    // ===================================

    /**
     * Xử lý API /reject-access. Admin từ chối yêu cầu PENDING.
     * @param req DTO chứa accessRequestId (ID của bản ghi UserDeviceAccess PENDING).
     * @param username Tên Admin đang thực hiện.
     */
    @Transactional
    public void rejectAccess(ApprovalRequest req, String username) {
        User admin = getAuthenticatedUser(username);
        log.info("Admin '{}' attempting to reject access request ID: {}", username, req.getAccessRequestId());

        // 1. Tìm yêu cầu PENDING
        UserDeviceAccess accessRequest = accessRepository.findById(req.getAccessRequestId())
                .filter(access -> access.getStatus() == AccessStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found or already processed"));

        // 2. Kiểm tra quyền Admin
        checkAdminPermission(admin, accessRequest.getDevice());

        // 3. Xóa yêu cầu PENDING (thay vì lưu trạng thái REJECTED, để user có thể gửi lại sau)
        accessRepository.delete(accessRequest);
        log.info("Access request ID '{}' for user '{}' was rejected and deleted by Admin '{}'",
                req.getAccessRequestId(), accessRequest.getUser().getUsername(), username);

        // TODO: Gửi thông báo cho người bị từ chối
    }

    /**
     * Xử lý API /remove-member. Admin xóa quyền của một user (MEMBER hoặc ADMIN khác).
     * @param req DTO chứa accessId (ID của bản ghi UserDeviceAccess cần xóa).
     * @param username Tên Admin đang thực hiện.
     */
    @Transactional
    public void removeMember(RemoveMemberRequest req, String username) {
        User admin = getAuthenticatedUser(username);
        log.info("Admin '{}' attempting to remove access record ID: {}", username, req.getAccessId());

        // 1. Tìm bản ghi quyền (access record)
        UserDeviceAccess accessToRemove = findAccessRecordById(req.getAccessId());

        // 2. Kiểm tra quyền Admin của người yêu cầu
        checkAdminPermission(admin, accessToRemove.getDevice());

        // 3. Kiểm tra logic an toàn: Admin không thể tự xóa chính mình
        if (accessToRemove.getUser().getId().equals(admin.getId())) {
            log.warn("Admin '{}' attempted to remove themselves. Denied.", username);
            throw new AccessDeniedException("Admin cannot remove themselves. Use 'Delete Device' or transfer Admin role first.");
        }

        // 4. Kiểm tra logic an toàn: Admin không thể xóa Admin cuối cùng
        if (accessToRemove.getRole() == DeviceRole.ADMIN) {
            // Đếm số lượng Admin còn lại cho thiết bị này
            long adminCount = accessRepository.findByDeviceAndRole(accessToRemove.getDevice(), DeviceRole.ADMIN)
                    .stream()
                    .filter(access -> access.getStatus() == AccessStatus.ACCEPTED)
                    .count();
            if (adminCount <= 1) {
                log.warn("Admin '{}' attempted to remove the last Admin for device '{}'. Denied.", username, accessToRemove.getDevice().getDeviceId());
                throw new AccessDeniedException("Cannot remove the last Admin. Transfer Admin role first.");
            }
        }

        // 5. Xóa bản ghi quyền
        log.info("Removing access for user '{}' from device '{}' by Admin '{}'",
                accessToRemove.getUser().getUsername(), accessToRemove.getDevice().getDeviceId(), username);
        accessRepository.delete(accessToRemove);

        // TODO: Gửi thông báo cho người bị xóa
    }

    /**
     * Xử lý API /rename-device. Admin đổi tên thiết bị.
     * @param req DTO chứa deviceId và newName.
     * @param username Tên Admin đang thực hiện.
     */
    @Transactional
    public void renameDevice(RenameDeviceRequest req, String username) {
        User admin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(req.getDeviceId());
        log.info("Admin '{}' attempting to rename device '{}' to '{}'", username, req.getDeviceId(), req.getNewName());

        // 1. Kiểm tra quyền Admin
        checkAdminPermission(admin, device);

        // 2. Cập nhật tên và lưu
        device.setName(req.getNewName());
        deviceRepository.save(device);
        log.info("Device '{}' renamed to '{}'", device.getDeviceId(), req.getNewName());
    }

    /**
     * Xử lý API /delete-device. Admin xóa vĩnh viễn thiết bị.
     * @param deviceId MAC address của thiết bị.
     * @param username Tên Admin đang thực hiện.
     */
    @Transactional
    public void deleteDevice(String deviceId, String username) {
        User admin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(deviceId);
        log.warn("CRITICAL: Admin '{}' attempting to DELETE device '{}' ({})", username, deviceId, device.getName());

        // 1. Kiểm tra quyền Admin
        checkAdminPermission(admin, device);

        // 2. Xóa thiết bị
        // Do có 'cascade = CascadeType.ALL' và 'orphanRemoval = true' trên 'RemoteDevice.accesses'
        // Spring Data JPA sẽ tự động xóa tất cả bản ghi 'UserDeviceAccess' liên quan.
        deviceRepository.delete(device);
        log.warn("Device '{}' and all associated access records have been deleted by Admin '{}'", deviceId, username);

        // TODO: Gửi thông báo cho TẤT CẢ các thành viên rằng thiết bị đã bị xóa.
        // Cần ngắt kết nối WebSocket của ESP32 nếu nó đang online
        sessionManager.unregister(deviceId); // Đóng session nếu đang mở
    }
}