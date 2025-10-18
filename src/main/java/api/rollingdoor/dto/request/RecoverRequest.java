package api.rollingdoor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class RecoverRequest {
    @NotBlank private String deviceId;
    @NotBlank private String masterPassword;
}