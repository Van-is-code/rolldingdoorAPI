package api.rollingdoor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data public class ClaimRequest {
    @NotBlank @Pattern(regexp = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$", message = "Invalid MAC address format")
    private String deviceId;
    @NotBlank @Size(min = 6, message = "Master password must be at least 6 characters")
    private String devicePassword;
}