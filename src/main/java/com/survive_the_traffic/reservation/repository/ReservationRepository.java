package com.survive_the_traffic.reservation.repository;

import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByUserId(Long userId);

    long countBySeatId(Long seatId);

    long countBySeatIdAndStatus(Long seatId, ReservationStatus status);
}
