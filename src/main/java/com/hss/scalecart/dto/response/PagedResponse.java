package com.hss.scalecart.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PagedResponse<T> {
    private List<T> items;
    private int pageSize;
    private boolean hasMore;
    private UUID nextCursor;   // client sends this back as lastSeenId
}