package com.survive_the_traffic.seat.repository;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.seat.domain.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByConcertId(Long concertId);

}
