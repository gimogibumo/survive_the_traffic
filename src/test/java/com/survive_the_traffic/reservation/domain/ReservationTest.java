package com.survive_the_traffic.reservation.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.survive_the_traffic.reservation.exception.ReservationExpiredException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReservationTest {

    @Test
    void 만료시각과_현재시각이_같으면_예약은_만료된다() {

        // given
        LocalDateTime expiresAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                22,
                30
            );

        Reservation reservation =
            Reservation.builder()
                .status(
                    ReservationStatus.PENDING
                )
                .expiresAt(
                    expiresAt
                )
                .build();


        // when + then
        assertThatThrownBy(
            () ->
                reservation.validatePayable(
                    expiresAt
                )
        )
            .isInstanceOf(
                ReservationExpiredException.class
            );
    }

    @Test
    void 만료시각_직전에는_예약을_결제할_수_있다() {

        // given
        LocalDateTime expiresAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                22,
                30
            );

        Reservation reservation =
            Reservation.builder()
                .status(
                    ReservationStatus.PENDING
                )
                .expiresAt(
                    expiresAt
                )
                .build();

        LocalDateTime now =
            expiresAt.minusSeconds(1);


        // when + then
        assertThatCode(
            () ->
                reservation.validatePayable(
                    now
                )
        )
            .doesNotThrowAnyException();
    }

    @Test
    void 만료시각이_지났으면_예약은_만료된다() {

        // given
        LocalDateTime expiresAt =
            LocalDateTime.of(
                2026,
                10,
                20,
                22,
                30
            );

        Reservation reservation =
            Reservation.builder()
                .status(
                    ReservationStatus.PENDING
                )
                .expiresAt(
                    expiresAt
                )
                .build();

        LocalDateTime now =
            expiresAt.plusSeconds(1);


        // when + then
        assertThatThrownBy(
            () ->
                reservation.validatePayable(
                    now
                )
        )
            .isInstanceOf(
                ReservationExpiredException.class
            );
    }
}