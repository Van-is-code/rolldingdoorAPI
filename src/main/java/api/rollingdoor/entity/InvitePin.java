package api.rollingdoor.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "invite_pins")
@Data
@NoArgsConstructor
public class InvitePin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String pin;

    @ManyToOne
    @JoinColumn(name = "device_id", nullable = false)
    private RemoteDevice device;

    @Column(nullable = false)
    private Instant expiresAt;
}