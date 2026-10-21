package com.survive_the_traffic.performance.service;

import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("performance")
@Service
@RequiredArgsConstructor
public class PerformanceQueryService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;

    // Keep the pre-fetch-join query available only for controlled comparisons.
    @Transactional(readOnly = true)
    public List<ReservationDetailResponse> getUserReservationsBeforeFetchJoin(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }
        return reservationRepository.findAllByUserId(userId).stream()
            .map(ReservationDetailResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public int hikariProbe() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1 FROM pg_sleep(0.01)", Integer.class);
        if(result == null) {
            throw new IllegalStateException("Hikari connection returned null");
        }

        return result;
    }

    public String gracefulShutDownProbe(long delayMs) {
        if(delayMs < 0 || delayMs > 15_000) {
            throw new IllegalArgumentException("delayMs must be between 0 and 15000");
        }

        try {
            Thread.sleep(delayMs);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Graceful shutdown probe was interrupted",e);
        }
        return "completed";
    }
}
