package api.rollingdoor.config;

import api.rollingdoor.entity.AccessStatus;
import api.rollingdoor.entity.UserDeviceAccess;
import api.rollingdoor.repository.InvitePinRepository;
import api.rollingdoor.repository.UserDeviceAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final InvitePinRepository pinRepository;
    private final UserDeviceAccessRepository accessRepository;

    // Chạy 1 giờ 1 lần
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredData() {
        Instant now = Instant.now();

        // 1. Xóa tất cả Mã PIN Mời đã hết hạn (quá 5 phút)
        log.info("Cleaning up expired invite PINs...");
        pinRepository.deleteByExpiresAtBefore(now);

        // 2. Xóa tất cả yêu cầu PENDING quá 48 giờ
        log.info("Cleaning up expired access requests (PENDING)...");
        Instant cutoffTime = now.minus(48, ChronoUnit.HOURS);
        List<UserDeviceAccess> expiredRequests = accessRepository.findByStatusAndCreatedAtBefore(
                AccessStatus.PENDING,
                cutoffTime
        );

        if (!expiredRequests.isEmpty()) {
            log.warn("Deleting {} expired access requests.", expiredRequests.size());
            accessRepository.deleteAll(expiredRequests);
        }
    }
}