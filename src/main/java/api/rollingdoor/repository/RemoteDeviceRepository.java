package api.rollingdoor.repository;

import api.rollingdoor.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RemoteDeviceRepository extends JpaRepository<RemoteDevice, Long> {
    Optional<RemoteDevice> findByDeviceId(String deviceId); // Tìm bằng MAC
}