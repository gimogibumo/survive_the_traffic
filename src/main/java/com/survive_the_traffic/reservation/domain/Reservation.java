package com.survive_the_traffic.reservation.domain;

import com.survive_the_traffic.reservation.exception.ReservationExpiredException;
import com.survive_the_traffic.reservation.exception.ReservationNotPendingException;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public void validatePayable(LocalDateTime now) {
        if (status != ReservationStatus.PENDING) {
            throw new ReservationNotPendingException();
        }

        if (expiresAt.isBefore(now)) {
            throw new ReservationExpiredException();
        }
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }
}
