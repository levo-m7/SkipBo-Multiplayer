package org.skipbo.dto.websocket;

public record GameActionRejectedMessage(String type, String actionType, String reason)
{
}
