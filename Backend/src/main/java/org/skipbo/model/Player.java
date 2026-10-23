package org.skipbo.model;

public class Player
{
    private final String id;
    private volatile String username;
    private volatile int avatar;

    public Player(String id)
    {
        this.id = id;
        this.username = "user";
        this.avatar = 1;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getAvatar() { return avatar; }
    public void setAvatar(int avatar) { this.avatar = avatar; }
}
