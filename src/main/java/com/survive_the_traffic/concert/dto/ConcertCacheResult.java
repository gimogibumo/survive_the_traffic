package com.survive_the_traffic.concert.dto;

public record ConcertCacheResult(
    CacheStatus cacheStatus,
    ConcertResponse concert
) {

}
