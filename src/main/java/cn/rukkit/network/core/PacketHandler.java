package cn.rukkit.network.core;

import cn.rukkit.network.room.NetworkRoom;
import cn.rukkit.network.room.RoomConnection;
import io.netty.channel.ChannelHandlerContext;

public abstract class PacketHandler {
    public RoomConnection conn;
    public NetworkRoom currentRoom;
    public abstract void handle() throws Exception;
    public abstract void updateMsg(ChannelHandlerContext ctx,Object msg);
	public abstract void onConnectionClose(ChannelHandlerContext ctx);
}