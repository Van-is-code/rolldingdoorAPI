package api.rollingdoor.repository;

import api.rollingdoor.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
public interface InvitePinRepository extends JpaRepository<InvitePin, Long> {
    Optional<InvitePin> findByPin(String pin);
    boolean existsByPin(String pin);
    void deleteByExpiresAtBefore(Instant now);
}