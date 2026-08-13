package com.survive_the_traffic.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.reservation.domain.Reservation;
import com.survive_the_traffic.reservation.domain.ReservationStatus;
import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.repository.ReservationRepository;
import com.survive_the_traffic.seat.domain.Seat;
import com.survive_the_traffic.user.exception.UserNotFoundException;
import com.survive_the_traffic.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("사용자의 예약 목록을 조회한다")
    void getUserReservations_success() {

        // given
        Long userId = 1L;

        Reservation reservation1 = mock(Reservation.class);
        Reservation reservation2 = mock(Reservation.class);

        Seat seat1 = mock(Seat.class);
        Seat seat2 = mock(Seat.class);

        Concert concert1 = mock(Concert.class);
        Concert concert2 = mock(Concert.class);


        when(userRepository.existsById(userId))
            .thenReturn(true);

        when(reservationRepository.findAllByUserId(userId))
            .thenReturn(List.of(reservation1, reservation2));


        // reservation 1
        when(reservation1.getId())
            .thenReturn(1001L);

        when(reservation1.getSeat())
            .thenReturn(seat1);

        when(reservation1.getStatus())
            .thenReturn(ReservationStatus.PENDING);

        when(seat1.getConcert())
            .thenReturn(concert1);

        when(concert1.getTitle())
            .thenReturn("Backend Festival");

        when(seat1.getSection())
            .thenReturn("A");

        when(seat1.getRowNumber())
            .thenReturn(1);

        when(seat1.getSeatNumber())
            .thenReturn(10);

        when(seat1.getPrice())
            .thenReturn(150000L);


        // reservation 2
        when(reservation2.getId())
            .thenReturn(1002L);

        when(reservation2.getSeat())
            .thenReturn(seat2);

        when(reservation2.getStatus())
            .thenReturn(ReservationStatus.CONFIRMED);

        when(seat2.getConcert())
            .thenReturn(concert2);

        when(concert2.getTitle())
            .thenReturn("Spring Concert");

        when(seat2.getSection())
            .thenReturn("B");

        when(seat2.getRowNumber())
            .thenReturn(2);

        when(seat2.getSeatNumber())
            .thenReturn(5);

        when(seat2.getPrice())
            .thenReturn(120000L);


        // when
        List<ReservationDetailResponse> result =
            userService.getUserReservations(userId);


        // then
        assertEquals(2, result.size());

        ReservationDetailResponse first = result.get(0);

        assertEquals(1001L, first.reservationId());
        assertEquals("Backend Festival", first.concertTitle());
        assertEquals("A", first.section());
        assertEquals(1, first.row());
        assertEquals(10, first.seatNumber());
        assertEquals(150000L, first.price());
        assertEquals(
            ReservationStatus.PENDING,
            first.status()
        );


        ReservationDetailResponse second = result.get(1);

        assertEquals(1002L, second.reservationId());
        assertEquals("Spring Concert", second.concertTitle());
        assertEquals("B", second.section());
        assertEquals(2, second.row());
        assertEquals(5, second.seatNumber());
        assertEquals(120000L, second.price());
        assertEquals(
            ReservationStatus.CONFIRMED,
            second.status()
        );


        verify(userRepository)
            .existsById(userId);

        verify(reservationRepository)
            .findAllByUserId(userId);
    }

    @Test
    @DisplayName("예약 내역이 없는 사용자는 빈 목록을 반환한다")
    void getUserReservations_empty() {

        // given
        Long userId = 1L;

        when(userRepository.existsById(userId))
            .thenReturn(true);

        when(reservationRepository.findAllByUserId(userId))
            .thenReturn(List.of());


        // when
        List<ReservationDetailResponse> result =
            userService.getUserReservations(userId);


        // then
        assertEquals(0, result.size());

        verify(userRepository)
            .existsById(userId);

        verify(reservationRepository)
            .findAllByUserId(userId);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 예약 목록을 조회하면 예외가 발생한다")
    void getUserReservations_userNotFound() {

        // given
        Long userId = 999L;

        when(userRepository.existsById(userId))
            .thenReturn(false);


        // when & then
        assertThrows(
            UserNotFoundException.class,
            () -> userService.getUserReservations(userId)
        );

        verify(userRepository)
            .existsById(userId);

        verifyNoInteractions(reservationRepository);
    }
}