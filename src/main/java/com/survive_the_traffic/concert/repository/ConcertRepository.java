package com.survive_the_traffic.concert.repository;

import com.survive_the_traffic.concert.domain.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

}
