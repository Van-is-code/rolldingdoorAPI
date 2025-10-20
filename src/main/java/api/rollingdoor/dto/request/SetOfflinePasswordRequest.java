package api.rollingdoor.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetOfflinePasswordRequest {
    @NotBlank
    private String deviceId; // MAC Address

    @NotBlank
    @Size(min = 6, message = "Offline password must be at least 6 characters")
    private String newPassword;
}