package api.rollingdoor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor public class DeviceResponse {
    private String deviceId;
    private String name;
    private String role;
}