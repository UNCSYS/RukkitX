package cn.rukkit.network.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

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
import cn.rukkit.network.command.NewGameCommand;
import cn.rukkit.network.core.packet.Packet;
import cn.rukkit.network.core.packet.PacketType;
import cn.rukkit.network.core.packet.UniversalPacket;
import cn.rukkit.network.io.GameInputStream;
import cn.rukkit.network.io.GameOutputStream;
import cn.rukkit.network.io.GzipDecoder;
import cn.rukkit.network.io.GzipEncoder;
import cn.rukkit.network.room.NetworkRoom;
import cn.rukkit.network.room.RoomConnection;
import cn.rukkit.util.LangUtil;
import cn.rukkit.util.MathUtil;
import io.netty.channel.ChannelHandlerContext;

// PacketHandler.java
public class ServerPacketHandler extends PacketHandler {
	private static final Logger log = LoggerFactory.getLogger(ServerPacketHandler.class);

	private ChannelHandlerContext ctx;
	private ConnectionHandler handler;
	private Packet p;
	public RoomConnection conn;
	public NetworkRoom currentRoom;
	private String disconnectReason;

	public ServerPacketHandler(ConnectionHandler handler) {
		this.ctx = handler.ctx;
		this.handler = handler;
	}

	@Override
	public void updateMsg(ChannelHandlerContext ctx, Object msg) {
		this.ctx = ctx;
		this.p = (Packet) msg;
	}

	@Override
	public void onConnectionClose(ChannelHandlerContext ctx) {
		if (this.conn != null) {
			PlayerLeftEvent.getListenerList().callListeners(new PlayerLeftEvent(this.conn.player, disconnectReason));
			currentRoom.connectionManager.discard(this.conn);
			Rukkit.getGlobalConnectionManager().discard(this.conn);
			this.conn.stopPingTask();
			this.conn.stopTeamTask();
		} else {
			if (!(ctx.channel().remoteAddress().toString().contains("18.216.139.119")
					|| ctx.channel().remoteAddress().toString().contains("192.241.156.189"))) { // Whitelist of official
																								// server
				log.warn("There is a unexpected connection at connection {}.", ctx.channel().remoteAddress());
			}
			ctx.close();
		}
	}

	@Override
	public void handle() throws Exception {
		switch (p.type) {
			case PacketType.PREREGISTER_CONNECTION:
				preRegisterHandler();
				break;
			case PacketType.HEART_BEAT_RESPONSE:
				heartResponsePacketHandler();
				break;
			case PacketType.PLAYER_INFO:
				playerInfoPacketHandler();
				break;
			case PacketType.ADD_CHAT:
				addChatPacketHandler();
				break;
			case PacketType.ADD_GAMECOMMAND:
				addGameCommandPacketHandler();
				break;
			case PacketType.READY:
				playerReadyPacketHandler();
				break;
			case PacketType.SYNC:
				syncPacketHandler();
			case PacketType.SYNC_CHECKSUM_RESPONCE:
				syncChecksumResponcePacketHandler();
				break;
			case PacketType.DISCONNECT:
				disconnectPacketHandler();
				break;
			case PacketType.QUESTION_RESPONCE:
				questionResponcePacketHandler();
				break;
			default:
				break;
		}
	}

	// =============== 实际对封包处理 =================//
	private void preRegisterHandler() throws IOException {
		log.debug("New connection established:{}", ctx.channel().remoteAddress());
		ctx.write(UniversalPacket.preRegister());
		ctx.writeAndFlush(UniversalPacket.chat("SERVER", LangUtil.getString("rukkit.playerRegister"), -1));
	}

	private void playerInfoPacketHandler() throws IOException {
		GameInputStream in = new GameInputStream(p);
		String packageName = in.readString();
		log.debug("Ints:" + in.readInt());
		int gameVersionCode = in.readInt();
		in.readInt();
		String playerName = in.readString();
		// 服务器密码判定
		in.readByte(); // 其实是 boolean
		// 如果上面那个为 true，则接下来读 String 做密码
		in.readString(); // packageName
		// 玩家固有的uuid，前提是verifyCode不改变 (在rw内叫clientKey)
		String uuid = in.readString();
		// 核心单位检查，用于判断玩家是否对客户端进行修改
		// 1.14:1198432602
		// 1.15:678359601
		int coreUnitCheck = in.readInt();
		String verifyResult = in.readString();
		log.debug(String.format("Got Player(package=%s, version=%d, name=%s, uuid=%s, coreUnit=%d",
				packageName, gameVersionCode, playerName, uuid, coreUnitCheck));

		// 获取当前的可用房间
		NetworkRoom room = Rukkit.getRoomManager().getAvailableRoom();

		// 获取是否为断线玩家
		NetworkPlayer targetPlayer = Rukkit.getGlobalConnectionManager().getAllPlayerByUUID(uuid);
		// 是否启用同步
		if (targetPlayer != null) {
			if (Rukkit.getConfig().syncEnabled) {
				// 获取上次断线时的断线房间
				currentRoom = targetPlayer.getRoom();
				log.info("Found offline room {}", currentRoom.toString());
			} else {
				currentRoom = room;
			}
		} else {
			currentRoom = room;
		}

		// 无可用房间，踢出
		if (currentRoom == null) {
			ctx.writeAndFlush(UniversalPacket.kick(LangUtil.getString("rukkit.gameFull")));
			return;
		}

		if (!currentRoom.isGaming() && targetPlayer != null) { // 如果 room 不在游戏，说明该uuid玩家发起了重复连接
			log.info("Dup player {} (UUID={}) joined!", playerName, uuid);
			if (Rukkit.getConfig().isDebug) {
				log.info("You are in the debug mode, allowing this situation!");
				targetPlayer = null; // 释放来保证正确加入
			} else {
				ctx.writeAndFlush(UniversalPacket.kick("You are already in server!"));
				return;
			}
		}

		// 刷新房间信息
		ctx.writeAndFlush(UniversalPacket.serverInfo(currentRoom.config));

		// 创建 RoomConnection
		conn = new RoomConnection(handler, currentRoom);
		// 玩家实例判断
		if (targetPlayer != null && Rukkit.getConfig().syncEnabled) {
			conn.player = targetPlayer;
			conn.player.name = playerName;
		} else {
			NetworkPlayer player = new NetworkPlayer(conn);
			player.name = playerName;
			player.uuid = uuid;
			conn.player = player;
		}

		// 检查房主
		/*
		 * if (currentRoom.connectionManager.size() <= 0) {
		 * conn.sendServerMessage(LangUtil.getString("rukkit.playerGotAdmin"));
		 * conn.player.isAdmin = true;
		 * ctx.writeAndFlush(Packet.serverInfo(currentRoom.config,true));
		 * 
		 * //==========
		 * } else {
		 * ctx.writeAndFlush(Packet.serverInfo(currentRoom.config));
		 * }
		 */
		if (currentRoom.connectionManager.size() <= 0) {
			conn.sendServerMessage(LangUtil.getString("rukkit.playerGotAdmin"));
			conn.player.isAdmin = true;
			conn.sendPacket(UniversalPacket.serverInfo(currentRoom.config,true));

			// ==========
		} else {
			conn.sendPacket(UniversalPacket.serverInfo(currentRoom.config));
		}

		// 当前房间是否在游戏
		if (currentRoom.isGaming()) {
			// 是否启用同步
			if (Rukkit.getConfig().syncEnabled) {
				log.info("Start Syncing!");
				handler.stopTimeout();
				conn.player.updateServerInfo();
				currentRoom.connectionManager.set(conn, conn.player.playerIndex);
				conn.startTeamTask();
				conn.updateTeamList(false);
				conn.startPingTask();
				// Sync game
				conn.handler.ctx.writeAndFlush(UniversalPacket.startGame());
				// conn.handler.ctx.writeAndFlush(Packet.sendSave(currentRoom,
				// Rukkit.getDefaultSave().arr, false));
				currentRoom.syncGame();
				conn.player.isDisconnected = false;

				// PlayerJoinEvent.getListenerList().callListeners(new
				// PlayerJoinEvent(conn.player));
				PlayerReconnectEvent.getListenerList().callListeners(new PlayerReconnectEvent(conn.player));
			} else {
				conn.sendPacket(UniversalPacket.kick(LangUtil.getString("rukkit.gameStarted")));
			}
		}

		// Adding into GlobalConnectionManager.
		Rukkit.getGlobalConnectionManager().add(conn);
		// Adding into RoomConnectionManager.
		if (targetPlayer == null) {
			currentRoom.connectionManager.add(conn);
		}

		// load player Data.
		try {
			conn.player.loadPlayerData();
		} catch (Exception e) {
			log.warn("Player {} data load failed! E: {}", playerName, e.toString());
		}
		conn.sendServerMessage(LangUtil.getFormatString("rukkit.room", currentRoom.roomId));
		conn.sendServerMessage(Rukkit.getConfig().welcomeMsg
				.replace("{playerName}", playerName)
				.replace("{simpleUUID}", uuid.substring(0, 7))
				.replace("{packageName}", packageName)
				.replace("{versionCode}", String.valueOf(gameVersionCode)));

		if (targetPlayer == null) {
			conn.startPingTask();
			conn.startTeamTask();
			conn.updateTeamList(false);
			handler.stopTimeout();
			PlayerJoinEvent.getListenerList().callListeners(new PlayerJoinEvent(conn.player));
		}
	}

	private void heartResponsePacketHandler() throws IOException {
		conn.pong();
	}

	private void addChatPacketHandler() throws IOException {
		GameInputStream in = new GameInputStream(p);
		String chatmsg = in.readString();
		if (chatmsg.startsWith(".") || chatmsg.startsWith("-") || chatmsg.startsWith("_")) {
			Rukkit.getCommandManager().executeChatCommand(conn, chatmsg.substring(1));
		} else {
			if (PlayerChatEvent.getListenerList().callListeners(new PlayerChatEvent(conn.player, chatmsg))) {
				currentRoom.connectionManager
						.broadcast(UniversalPacket.chat(conn.player.name, chatmsg, conn.player.playerIndex));
			}
		}
	}

	private void addGameCommandPacketHandler() throws IOException, IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
				
		GameInputStream in = new GameInputStream(p);
		NewGameCommand cmd = new NewGameCommand(in.getDecodeBytes(),conn);
		
		Packet packet = cmd.out.createPacket(10);
		cmd.arr = packet.bytes;
		if (cmd.act != null) {
			ListenerList list = (ListenerList) cmd.act.getClass().getMethod("getListenerList").invoke(null);
			if (list.callListeners(cmd.act)) {
				conn.sendGameCommand(cmd);
			} else {
				log.debug("Event {} cancelled!", cmd.act);
			}
		} else {
			conn.sendGameCommand(cmd);
		}
	}

	private void playerReadyPacketHandler() {
		currentRoom.connectionManager.broadcastServerMessage(String.format("Player '%s' is randy.", conn.player.name));
	}

	private void syncPacketHandler() throws IOException {
		GameInputStream in = new GameInputStream(p);
		in.readByte();
		int frame = in.readInt();
		int time = in.readInt() / 15;
		log.debug("{}, {}, {}, {}", in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean());
		byte[] save = new byte[in.stream.available()];
		in.stream.read(save);
		if (save.length > 20) {
			SaveData data = new SaveData();
			data.arr = save;
			data.time = time;
			conn.save = data;
		}

	}

	private void syncChecksumResponcePacketHandler() throws IOException {
		GameInputStream in = new GameInputStream(p);
		in.readByte();
		int frame = in.readInt();
		int time = in.readInt() / 15;
		log.debug("{}, {}, {}, {}", in.readFloat(), in.readFloat(), in.readBoolean(), in.readBoolean());
		byte[] save = new byte[in.stream.available()];
		in.stream.read(save);
		if (save.length > 20) {
			SaveData data = new SaveData();
			data.arr = save;
			data.time = time;
			conn.save = data;
		}

	}

	private void disconnectPacketHandler() throws IOException {
		GameInputStream in = new GameInputStream(p);

		String reason = in.readString();
		disconnectReason = reason;
		// Disconnects gracefully.
		ctx.disconnect();

	}

	private void questionResponcePacketHandler() throws IOException {
		GameInputStream in = new GameInputStream(p);

		in.readByte(); // Always 1;
		int qid = in.readInt();
		String response = in.readString();
		ServerQuestionRespondEvent.getListenerList()
				.callListeners(new ServerQuestionRespondEvent(conn.player, qid, response));

	}
}
