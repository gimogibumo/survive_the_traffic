package com.survive_the_traffic.queue.controller;

import com.survive_the_traffic.auth.security.AuthenticatedUser;
import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/queues")
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/{concertId}/join")
    public ResponseEntity<WaitingQueueResponse> join(
        @PathVariable Long concertId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        WaitingQueueResponse response =
            waitingQueueService.join(
                concertId,
                authenticatedUser.getUserId()
            );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{concertId}/position")
    public ResponseEntity<WaitingQueueResponse> getPosition(
        @PathVariable Long concertId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        WaitingQueueResponse response =
            waitingQueueService.getPosition(
                concertId,
                authenticatedUser.getUserId()
            );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{concertId}/status")
    public ResponseEntity<QueueStatusResponse> getStatus(
        @PathVariable Long concertId,
        @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        QueueStatusResponse response =
            waitingQueueService.getStatus(
                concertId,
                authenticatedUser.getUserId()
            );

        return ResponseEntity.ok(response);
    }
}