package com.survive_the_traffic.reservation.repository;

import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByUserId(Long userId);

    @Query("""
        SELECT r
        FROM Reservation r
        JOIN FETCH r.seat s
        JOIN FETCH s.concert
        WHERE r.user.id = :userId
        ORDER BY r.id ASC
    """)
    List<Reservation> findAllByUserIdWithSeatAndConcert(
        @Param("userId") Long userId
    );

    long countBySeatId(Long seatId);

    long countBySeatIdAndStatus(Long seatId, ReservationStatus status);
}
