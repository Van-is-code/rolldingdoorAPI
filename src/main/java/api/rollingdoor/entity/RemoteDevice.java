package api.rollingdoor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Entity
@Table(name = "remote_devices")
@Data
@NoArgsConstructor
public class RemoteDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String deviceId; // MAC Address

    @Column(nullable = false)
    private String devicePassword; // Mật khẩu gốc (đã hash)

    private String name;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserDeviceAccess> accesses;
}