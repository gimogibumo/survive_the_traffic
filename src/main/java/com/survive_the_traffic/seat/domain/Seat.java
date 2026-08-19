package com.survive_the_traffic.seat.domain;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.seat.exception.SeatNotAvailableException;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Entity
@Table(
    name = "seat",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_seat_concert_position",
            columnNames = {
                "concert_id",
                "section",
                "row_number",
                "seat_number"
            }
        )
    }
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    public void hold() {
        if(status != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException();
        }
        this.status = SeatStatus.HELD;
    }

    public void sell() {
        if(status != SeatStatus.HELD) {
            throw new SeatNotAvailableException();
        }
        this.status = SeatStatus.SOLD;
    }

    @Version
    private Long version;
}