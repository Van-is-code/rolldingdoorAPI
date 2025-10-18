package api.rollingdoor.repository;

import api.rollingdoor.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserDeviceAccessRepository extends JpaRepository<UserDeviceAccess, Long> {
    Optional<UserDeviceAccess> findByUserAndDevice(User user, RemoteDevice device);
    List<UserDeviceAccess> findByDeviceAndRole(RemoteDevice device, DeviceRole role);
    Optional<UserDeviceAccess> findByUserAndDeviceAndStatus(User user, RemoteDevice device, AccessStatus status);
    List<UserDeviceAccess> findByStatusAndCreatedAtBefore(AccessStatus status, Instant cutoffTime);
    List<UserDeviceAccess> findByUserAndStatus(User user, AccessStatus status);
}