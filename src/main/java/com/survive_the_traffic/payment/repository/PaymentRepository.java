package com.survive_the_traffic.payment.repository;

import com.survive_the_traffic.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

}
