package com.survive_the_traffic.reservation.repository;

import com.survive_the_traffic.reservation.domain.Reservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByUserId(Long userId);

    long countBySeatId(Long seatId);
}
