package api.rollingdoor.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RemoveMemberRequest {
    @NotNull(message = "Access ID cannot be null")
    private Long accessId; // ID của bản ghi UserDeviceAccess cần xóa
}