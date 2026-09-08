package com.survive_the_traffic.performance.dto;

import java.util.List;

public record ExplainResponse(
    String query,
    List<String> plan
) {
}
