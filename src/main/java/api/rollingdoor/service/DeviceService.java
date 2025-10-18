package api.rollingdoor.service;

import api.rollingdoor.dto.request.*;
import api.rollingdoor.dto.response.DeviceResponse;
import api.rollingdoor.dto.response.InviteResponse;
import api.rollingdoor.entity.*;
import api.rollingdoor.exception.ResourceNotFoundException;
import api.rollingdoor.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final RemoteDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final UserDeviceAccessRepository accessRepository;
    private final InvitePinRepository pinRepository;
    private final PinGeneratorService pinGeneratorService;
    private final PasswordEncoder passwordEncoder;
    private final WebSocketSessionManager sessionManager; // Thay thế MQTT

    // ----- Tiện ích -----
    private User getAuthenticatedUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private RemoteDevice getDeviceByDeviceId(String deviceId) {
        return deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + deviceId));
    }

    private void checkAdminPermission(User user, RemoteDevice device) {
        accessRepository.findByUserAndDevice(user, device)
                .filter(access -> access.getRole() == DeviceRole.ADMIN && access.getStatus() == AccessStatus.ACCEPTED)
                .orElseThrow(() -> new AccessDeniedException("User is not ADMIN for this device"));
    }

    private UserDeviceAccess checkAcceptedPermission(User user, RemoteDevice device) {
        return accessRepository.findByUserAndDeviceAndStatus(user, device, AccessStatus.ACCEPTED)
                .orElseThrow(() -> new AccessDeniedException("User does not have access to this device"));
    }

    // ----- LUỒNG 1: CÀI ĐẶT -----
    @Transactional
    public void claimDevice(ClaimRequest req, String username) {
        User user = getAuthenticatedUser(username);

        if (deviceRepository.findByDeviceId(req.getDeviceId()).isPresent()) {
            throw new IllegalArgumentException("Device is already registered. Use 'Recover Admin' if you lost access.");
        }

        RemoteDevice device = new RemoteDevice();
        device.setDeviceId(req.getDeviceId());
        device.setDevicePassword(passwordEncoder.encode(req.getDevicePassword()));
        device.setName(req.getDeviceId());
        RemoteDevice savedDevice = deviceRepository.save(device);

        UserDeviceAccess access = new UserDeviceAccess(user, savedDevice, DeviceRole.ADMIN, AccessStatus.ACCEPTED);
        accessRepository.save(access);
    }

    // (Không cần hàm provisionDevice)

    // ----- LUỒNG 2: MỜI & DUYỆT -----
    @Transactional
    public InviteResponse generateInvite(String deviceId, String username) {
        User admin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(deviceId);
        checkAdminPermission(admin, device);

        String pin = pinGeneratorService.generateUniquePin();
        Instant expiryDate = Instant.now().plus(5, ChronoUnit.MINUTES);

        InvitePin invitePin = new InvitePin();
        invitePin.setPin(pin);
        invitePin.setDevice(device);
        invitePin.setExpiresAt(expiryDate);
        pinRepository.save(invitePin);

        return new InviteResponse(pin, 300);
    }

    @Transactional
    public void requestAccess(AccessRequest req, String username) {
        User member = getAuthenticatedUser(username);

        InvitePin invitePin = pinRepository.findByPin(req.getPin())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired PIN"));

        if (invitePin.getExpiresAt().isBefore(Instant.now())) {
            pinRepository.delete(invitePin);
            throw new AccessDeniedException("PIN has expired");
        }

        if (!invitePin.getDevice().getDeviceId().equals(req.getDeviceId())) {
            throw new AccessDeniedException("PIN is not valid for this device");
        }

        RemoteDevice device = invitePin.getDevice();

        accessRepository.findByUserAndDevice(member, device)
                .ifPresent(access -> {
                    if (access.getStatus() == AccessStatus.PENDING) {
                        throw new IllegalArgumentException("You already have a pending request for this device.");
                    }
                    if (access.getStatus() == AccessStatus.ACCEPTED) {
                        throw new IllegalArgumentException("You already have access to this device.");
                    }
                });

        UserDeviceAccess accessRequest = new UserDeviceAccess(member, device, DeviceRole.MEMBER, AccessStatus.PENDING);
        accessRepository.save(accessRequest);
        pinRepository.delete(invitePin);
        // (Gửi Push Notification cho Admins)
    }

    @Transactional
    public void approveAccess(ApprovalRequest req, String username) {
        User admin = getAuthenticatedUser(username);

        UserDeviceAccess accessRequest = accessRepository.findById(req.getAccessRequestId())
                .filter(access -> access.getStatus() == AccessStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found or already processed"));

        checkAdminPermission(admin, accessRequest.getDevice());

        accessRequest.setStatus(AccessStatus.ACCEPTED);
        accessRepository.save(accessRequest);
    }

    // ----- LUỒNG 3: ĐIỀU KHIỂN -----
    @Transactional(readOnly = true)
    public void sendCommand(CommandRequest req, String username) {
        User user = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(req.getDeviceId());
        checkAcceptedPermission(user, device);

        boolean sent = sessionManager.send(device.getDeviceId(), req.getAction());

        if (!sent) {
            throw new ResourceNotFoundException("Device is offline. Command not sent.");
        }
        // (Ghi log lịch sử)
    }

    // ----- LUỒNG 4: KHÔI PHỤC -----
    @Transactional
    public void recoverAdmin(RecoverRequest req, String username) {
        User newAdmin = getAuthenticatedUser(username);
        RemoteDevice device = getDeviceByDeviceId(req.getDeviceId());

        if (!passwordEncoder.matches(req.getMasterPassword(), device.getDevicePassword())) {
            throw new AccessDeniedException("Invalid master password");
        }

        List<UserDeviceAccess> oldAdmins = accessRepository.findByDeviceAndRole(device, DeviceRole.ADMIN);
        oldAdmins.forEach(oldAdminAccess -> {
            oldAdminAccess.setRole(DeviceRole.MEMBER);
            accessRepository.save(oldAdminAccess);
        });

        UserDeviceAccess newAdminAccess = accessRepository.findByUserAndDevice(newAdmin, device)
                .orElse(new UserDeviceAccess(newAdmin, device, DeviceRole.ADMIN, AccessStatus.ACCEPTED));

        newAdminAccess.setRole(DeviceRole.ADMIN);
        newAdminAccess.setStatus(AccessStatus.ACCEPTED);
        accessRepository.save(newAdminAccess);
    }

    // ----- LẤY THÔNG TIN -----
    @Transactional(readOnly = true)
    public List<DeviceResponse> getUserDevices(String username) {
        User user = getAuthenticatedUser(username);
        return accessRepository.findByUserAndStatus(user, AccessStatus.ACCEPTED)
                .stream()
                .map(access -> new DeviceResponse(
                        access.getDevice().getDeviceId(),
                        access.getDevice().getName(),
                        access.getRole().name()
                ))
                .collect(Collectors.toList());
    }
}