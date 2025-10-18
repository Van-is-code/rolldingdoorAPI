package api.rollingdoor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor public class InviteResponse {
    private String pin;
    private long expiresInSeconds;
}