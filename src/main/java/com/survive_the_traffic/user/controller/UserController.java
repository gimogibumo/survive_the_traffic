package com.survive_the_traffic.user.controller;

import com.survive_the_traffic.reservation.dto.ReservationDetailResponse;
import com.survive_the_traffic.reservation.dto.ReservationResponse;
import com.survive_the_traffic.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}/reservations")
    public ResponseEntity<List<ReservationDetailResponse>> getUserReservations(@PathVariable Long userId) {
        return ResponseEntity.ok().body(userService.getUserReservations(userId));
    }

}
