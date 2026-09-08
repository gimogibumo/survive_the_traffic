package com.survive_the_traffic.performance.service;

import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("performance")
@Service
@RequiredArgsConstructor
public class PerformanceQueryService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

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
}
