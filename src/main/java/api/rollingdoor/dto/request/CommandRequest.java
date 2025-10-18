package api.rollingdoor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
@Data public class CommandRequest {
    @NotBlank private String deviceId;
    @NotBlank @Pattern(regexp = "^(OPEN|CLOSE|STOP)$", message = "Action must be OPEN, CLOSE, or STOP")
    private String action;
}