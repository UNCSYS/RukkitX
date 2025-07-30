/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.network.core;
import java.io.DataInputStream;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.rukkit.Rukkit;
import cn.rukkit.event.Event;
import cn.rukkit.event.ListenerList;
import cn.rukkit.event.action.BuildEvent;
import cn.rukkit.event.action.MoveEvent;
import cn.rukkit.event.action.PingEvent;
import cn.rukkit.event.action.TaskEvent;
import cn.rukkit.event.player.PlayerChatEvent;
import cn.rukkit.event.player.PlayerJoinEvent;
import cn.rukkit.event.player.PlayerLeftEvent;
import cn.rukkit.event.player.PlayerReconnectEvent;
import cn.rukkit.event.server.ServerQuestionRespondEvent;
import cn.rukkit.game.GameActions;
import cn.rukkit.game.NetworkPlayer;
import cn.rukkit.game.SaveData;
import cn.rukkit.game.UnitType;
import cn.rukkit.game.unit.InternalUnit;
import cn.rukkit.network.command.GameCommand;
import cn.rukkit.network.core.packet.Packet;
import cn.rukkit.network.core.packet.PacketType;
import cn.rukkit.network.io.GameInputStream;
import cn.rukkit.network.io.GameOutputStream;
import cn.rukkit.network.io.GzipDecoder;
import cn.rukkit.network.io.GzipEncoder;
import cn.rukkit.network.room.NetworkRoom;
import cn.rukkit.network.room.RoomConnection;
import cn.rukkit.util.LangUtil;
import cn.rukkit.util.MathUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

/**
 * 这个类承载整个游戏数据封包的处理
 * 任务繁重
 */

public class ConnectionHandler extends ChannelInboundHandlerAdapter {
	Logger log = LoggerFactory.getLogger(ConnectionHandler.class);

	public ChannelHandlerContext ctx;
	private RoomConnection conn;
	private ScheduledFuture timeoutFuture;

	private NetworkRoom currentRoom;
	private String disconnectReason = "Unknown";
	public class TimeoutTask implements Runnable {
		private int execTime = 0;
		@Override
		public void run() {
			// TODO: Implement this method
			execTime ++;
			if (execTime >= Rukkit.getConfig().registerTimeout) {
				ctx.disconnect();
			}
		}
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		// TODO: Implement this method
		super.channelRegistered(ctx);
		// 保存 ctx 实例
		this.ctx = ctx;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// TODO: Implement this method
		super.channelActive(ctx);
		startTimeout();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		// TODO: Implement this method
		super.channelInactive(ctx);
		// 连接正确才调用事件
		if (conn != null) {
			PlayerLeftEvent.getListenerList().callListeners(new PlayerLeftEvent(conn.player, disconnectReason));
			currentRoom.connectionManager.discard(conn);
			Rukkit.getGlobalConnectionManager().discard(conn);
			conn.stopPingTask();
			conn.stopTeamTask();
		} else {
			if (!(ctx.channel().remoteAddress().toString().contains("18.216.139.119") || ctx.channel().remoteAddress().toString().contains("192.241.156.189"))) { // Whitelist of official server
				log.warn("There is a unexpected connection at connection {}.", ctx.channel().remoteAddress());
			}
		}
	}

	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    	try {
        	PacketHandler handler = new PacketHandler(ctx, msg, this);
        	handler.handle();
   	 	} finally {
        	ReferenceCountUtil.release(msg);
    	}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		//super.exceptionCaught(ctx, cause)
		log.warn("Exception happened", cause);
	}

	public void startTimeout() {
		if (timeoutFuture == null) {
			timeoutFuture = Rukkit.getThreadManager().schedule(new TimeoutTask(), 1000, 1000);
		}
	}

	public void stopTimeout() {
		if (timeoutFuture != null) {
			Rukkit.getThreadManager().shutdownTask(timeoutFuture);
			timeoutFuture = null;
		}
	}
}
