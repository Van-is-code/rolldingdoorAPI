package api.rollingdoor.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class ApprovalRequest {
    @NotNull private Long accessRequestId;
}