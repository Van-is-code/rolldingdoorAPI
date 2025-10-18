package api.rollingdoor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "user_device_access")
@Data
@NoArgsConstructor
public class UserDeviceAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private RemoteDevice device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceRole role; // ADMIN, MEMBER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessStatus status; // PENDING, ACCEPTED

    @Column(nullable = false)
    private Instant createdAt;

    public UserDeviceAccess(User user, RemoteDevice device, DeviceRole role, AccessStatus status) {
        this.user = user;
        this.device = device;
        this.role = role;
        this.status = status;
        this.createdAt = Instant.now();
    }
}