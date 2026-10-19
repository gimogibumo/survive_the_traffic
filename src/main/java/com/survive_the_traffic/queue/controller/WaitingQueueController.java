package com.survive_the_traffic.queue.controller;

import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.dto.WaitingQueueJoinRequest;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.service.WaitingQueueService;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/queues")
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/{concertId}/join")
    public ResponseEntity<WaitingQueueResponse> join(
        @PathVariable Long concertId,
        @Nonnull @RequestBody WaitingQueueJoinRequest request
    ) {
        WaitingQueueResponse response =
            waitingQueueService.join(concertId, request.userId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{concertId}/position")
    public ResponseEntity<WaitingQueueResponse> getPosition(
        @PathVariable Long concertId,
        @RequestParam Long userId
    ) {
        WaitingQueueResponse response =
            waitingQueueService.getPosition(concertId, userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{concertId}/status")
    public ResponseEntity<QueueStatusResponse> getStatus(
        @PathVariable Long concertId,
        @RequestParam Long userId
    ) {
        return ResponseEntity.ok(waitingQueueService.getStatus(concertId, userId));
    }
}
