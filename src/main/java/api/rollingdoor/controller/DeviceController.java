package api.rollingdoor.controller;

import api.rollingdoor.dto.request.*;
import api.rollingdoor.dto.response.DeviceResponse;
import api.rollingdoor.dto.response.InviteResponse;
import api.rollingdoor.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu xác thực cho tất cả API trong này
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/claim")
    public ResponseEntity<?> claimDevice(@Valid @RequestBody ClaimRequest req, Authentication auth) {
        deviceService.claimDevice(req, auth.getName());
        return ResponseEntity.ok("Device claimed successfully");
    }

    @GetMapping("/{deviceId}/generate-invite")
    public ResponseEntity<InviteResponse> generateInvite(@PathVariable String deviceId, Authentication auth) {
        InviteResponse response = deviceService.generateInvite(deviceId, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/request-access")
    public ResponseEntity<?> requestAccess(@Valid @RequestBody AccessRequest req, Authentication auth) {
        deviceService.requestAccess(req, auth.getName());
        return ResponseEntity.ok("Access request sent. Waiting for admin approval.");
    }

    @PostMapping("/approve-access")
    public ResponseEntity<?> approveAccess(@Valid @RequestBody ApprovalRequest req, Authentication auth) {
        deviceService.approveAccess(req, auth.getName());
        return ResponseEntity.ok("Access approved");
    }

    @PostMapping("/command")
    public ResponseEntity<?> sendCommand(@Valid @RequestBody CommandRequest req, Authentication auth) {
        deviceService.sendCommand(req, auth.getName());
        return ResponseEntity.ok("Command sent");
    }

    @PostMapping("/recover-admin")
    public ResponseEntity<?> recoverAdmin(@Valid @RequestBody RecoverRequest req, Authentication auth) {
        deviceService.recoverAdmin(req, auth.getName());
        return ResponseEntity.ok("Admin rights recovered");
    }

    @GetMapping("/my-devices")
    public ResponseEntity<List<DeviceResponse>> getMyDevices(Authentication auth) {
        List<DeviceResponse> devices = deviceService.getUserDevices(auth.getName());
        return ResponseEntity.ok(devices);
    }

    // (Thêm API từ chối, xóa thành viên, đổi tên thiết bị... ở đây)
}