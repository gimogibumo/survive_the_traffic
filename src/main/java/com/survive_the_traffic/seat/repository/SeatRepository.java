package com.survive_the_traffic.seat.repository;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.seat.domain.Seat;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :seatId")
    Optional<Seat> findByIdWithPessimisticLock(
       @Param("seatId") Long seatId
    );

    List<Seat> findAllByConcertId(Long concertId);

}
