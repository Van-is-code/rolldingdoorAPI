package api.rollingdoor.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RenameDeviceRequest {
    @NotBlank
    private String deviceId; // MAC Address của thiết bị

    @NotBlank(message = "New name cannot be blank")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String newName; // Tên mới
}