package org.skipbo.dto.websocket;

import java.util.List;

public record RoomListMessage(String type, List<RoomSummary> rooms)
{
}