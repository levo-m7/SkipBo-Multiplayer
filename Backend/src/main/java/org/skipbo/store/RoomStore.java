package org.skipbo.store;

import org.skipbo.model.room.Room;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoomStore
{
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public void save(Room room) { rooms.put(room.getId(), room); }
    public Room findById(String roomId) { return rooms.get(roomId); }

    public Room findByPlayerId(String playerId)
    {
        for (Room room : rooms.values())
        {
            if (room.hasPlayer(playerId)) { return room; }
        }
        return null;
    }

    public void remove(String roomId)
    {
        rooms.remove(roomId);
    }

    public boolean exists(String roomId)
    {
        return rooms.containsKey(roomId);
    }

    public List<Room> getAllRooms()
    {
        return List.copyOf(rooms.values());
    }

}
