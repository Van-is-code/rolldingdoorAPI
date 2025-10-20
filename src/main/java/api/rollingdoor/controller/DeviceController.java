package api.rollingdoor.controller;

import api.rollingdoor.dto.request.*;
import api.rollingdoor.dto.response.DeviceResponse;
import api.rollingdoor.dto.response.InviteResponse;
import api.rollingdoor.dto.response.MessageResponse; // Cần import MessageResponse
import api.rollingdoor.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // Cần import Map

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu xác thực cho tất cả API trong này
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/claim")
    public ResponseEntity<MessageResponse> claimDevice(@Valid @RequestBody ClaimRequest req, Authentication auth) {
        deviceService.claimDevice(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Device claimed successfully"));
    }

    @GetMapping("/{deviceId}/generate-invite")
    public ResponseEntity<InviteResponse> generateInvite(@PathVariable String deviceId, Authentication auth) {
        InviteResponse response = deviceService.generateInvite(deviceId, auth.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/request-access")
    public ResponseEntity<MessageResponse> requestAccess(@Valid @RequestBody AccessRequest req, Authentication auth) {
        deviceService.requestAccess(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Access request sent. Waiting for admin approval."));
    }

    @PostMapping("/approve-access")
    public ResponseEntity<MessageResponse> approveAccess(@Valid @RequestBody ApprovalRequest req, Authentication auth) {
        deviceService.approveAccess(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Access approved"));
    }

    @PostMapping("/command")
    public ResponseEntity<MessageResponse> sendCommand(@Valid @RequestBody CommandRequest req, Authentication auth) {
        deviceService.sendCommand(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Command sent to device via WebSocket"));
    }

    @PostMapping("/recover-admin")
    public ResponseEntity<MessageResponse> recoverAdmin(@Valid @RequestBody RecoverRequest req, Authentication auth) {
        deviceService.recoverAdmin(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Admin rights recovered successfully"));
    }

    @GetMapping("/my-devices")
    public ResponseEntity<List<DeviceResponse>> getMyDevices(Authentication auth) {
        List<DeviceResponse> devices = deviceService.getUserDevices(auth.getName());
        return ResponseEntity.ok(devices);
    }

    @PostMapping("/set-offline-password")
    public ResponseEntity<MessageResponse> setOfflinePassword(@Valid @RequestBody SetOfflinePasswordRequest req, Authentication auth) {
        deviceService.setOfflinePassword(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Offline password update command sent to device."));
    }

    // --- CÁC API MỚI ---

    /**
     * API để Admin từ chối yêu cầu tham gia (PENDING) của Member.
     * @param req Chứa accessRequestId (ID của bản ghi UserDeviceAccess đang PENDING).
     * @param auth Thông tin Admin đã xác thực.
     * @return Thông báo từ chối thành công.
     */
    @PostMapping("/reject-access")
    public ResponseEntity<MessageResponse> rejectAccess(@Valid @RequestBody ApprovalRequest req, Authentication auth) {
        deviceService.rejectAccess(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Access request rejected"));
    }

    /**
     * API để Admin xóa một Member đã được duyệt (ACCEPTED) khỏi thiết bị.
     * @param req Chứa accessId (ID của bản ghi UserDeviceAccess cần xóa).
     * @param auth Thông tin Admin đã xác thực.
     * @return Thông báo xóa thành công.
     */
    @DeleteMapping("/remove-member")
    public ResponseEntity<MessageResponse> removeMember(@Valid @RequestBody RemoveMemberRequest req, Authentication auth) {
        deviceService.removeMember(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Member removed successfully"));
    }

    /**
     * API để Admin đổi tên thiết bị.
     * @param req Chứa deviceId (MAC) và newName (tên mới).
     * @param auth Thông tin Admin đã xác thực.
     * @return Thông báo đổi tên thành công.
     */
    @PutMapping("/rename-device")
    public ResponseEntity<MessageResponse> renameDevice(@Valid @RequestBody RenameDeviceRequest req, Authentication auth) {
        deviceService.renameDevice(req, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Device renamed successfully"));
    }

    /**
     * API để Admin xóa vĩnh viễn một thiết bị khỏi hệ thống.
     * @param deviceId MAC address của thiết bị cần xóa (lấy từ URL path).
     * @param auth Thông tin Admin đã xác thực.
     * @return Thông báo xóa thành công.
     */
    @DeleteMapping("/delete-device/{deviceId}")
    public ResponseEntity<MessageResponse> deleteDevice(@PathVariable String deviceId, Authentication auth) {
        deviceService.deleteDevice(deviceId, auth.getName());
        return ResponseEntity.ok(new MessageResponse("Device deleted successfully"));
    }
}