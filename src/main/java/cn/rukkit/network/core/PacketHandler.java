package cn.rukkit.network.core;

import java.io.DataInputStream;
import java.io.IOException;

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

// PacketHandler.java
public class PacketHandler {
    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);
    
    private final ChannelHandlerContext ctx;
    private final ConnectionHandler handler;
    private final Packet p;
    private RoomConnection conn;
    private NetworkRoom currentRoom;
    private String disconnectReason;

    public PacketHandler(ChannelHandlerContext ctx, Object msg, ConnectionHandler handler) {
        this.ctx = ctx;
        this.p = (Packet) msg;
        this.handler = handler;
    }

    public void handle() throws Exception {
        GameInputStream in = new GameInputStream(p);
        switch (p.type) {
            case PacketType.PREREGISTER_CONNECTION:
                handlePreRegisterConnection();
                break;
            case PacketType.PLAYER_INFO:
                handlePlayerInfo(in);
                break;
            case PacketType.HEART_BEAT_RESPONSE:
                handleHeartBeatResponse();
                break;
            case PacketType.ADD_CHAT:
                handleAddChat(in);
                break;
            case PacketType.ADD_GAMECOMMAND:
                handleAddGameCommand(in);
                break;
            case PacketType.READY:
                handleReady();
                break;
            case PacketType.SYNC:
                handleSync(in);
                break;
            case PacketType.SYNC_CHECKSUM_RESPONCE:
                handleSyncChecksumResponse(in);
                break;
            case PacketType.DISCONNECT:
                handleDisconnect(in);
                break;
            case PacketType.QUESTION_RESPONCE:
                handleQuestionResponse(in);
                break;
        }
    }

    private void handlePreRegisterConnection() throws IOException {
        log.debug("New connection established:{}", ctx.channel().remoteAddress());
        ctx.write(p.preRegister());
        ctx.writeAndFlush(p.chat("SERVER", LangUtil.getString("rukkit.playerRegister"), -1));
    }

    private void handlePlayerInfo(GameInputStream in) throws Exception {
        String packageName = in.readString();
        log.debug("Ints:" + in.readInt());
        int gameVersionCode = in.readInt();
        in.readInt();
        String playerName = in.readString();
        in.readByte(); // 其实是 boolean
        in.readString(); // packageName
        String uuid = in.readString();
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
            ctx.writeAndFlush(Packet.kick(LangUtil.getString("rukkit.gameFull")));
            return;
        }

        if (!currentRoom.isGaming() && targetPlayer != null) {
            log.info("Dup player {} (UUID={}) joined!", playerName, uuid);
            if (Rukkit.getConfig().isDebug) {
                log.info("You are in the debug mode, allowing this situation!");
                targetPlayer = null;
            } else {
                ctx.writeAndFlush(Packet.kick("You are already in server!"));
                return;
            }
        }

        // 刷新房间信息
        ctx.writeAndFlush(Packet.serverInfo(currentRoom.config));

        // 创建 RoomConnection
        conn = new RoomConnection(handler, currentRoom);
        if (targetPlayer != null && Rukkit.getConfig().syncEnabled) {
            conn.player = targetPlayer;
            conn.player.name = playerName;
        } else {
            NetworkPlayer player = new NetworkPlayer(conn);
            player.name = playerName;
            player.uuid = uuid;
            conn.player = player;
        }

        if (currentRoom.connectionManager.size() <= 0) {
            ctx.writeAndFlush(Packet.serverInfo(currentRoom.config));
        } else {
            ctx.writeAndFlush(Packet.serverInfo(currentRoom.config));
        }

        if (currentRoom.isGaming()) {
            if (Rukkit.getConfig().syncEnabled) {
                log.info("Start Syncing!");
                stopTimeout();
                conn.player.updateServerInfo();
                currentRoom.connectionManager.set(conn, conn.player.playerIndex);
                conn.startTeamTask();
                conn.updateTeamList(false);
                conn.startPingTask();
                conn.handler.ctx.writeAndFlush(Packet.startGame());
                currentRoom.syncGame();
                conn.player.isDisconnected = false;
                PlayerReconnectEvent.getListenerList().callListeners(new PlayerReconnectEvent(conn.player));
            } else {
                ctx.writeAndFlush(p.kick(LangUtil.getString("rukkit.gameStarted")));
            }
        }

        Rukkit.getGlobalConnectionManager().add(conn);
        if (targetPlayer == null) {
            currentRoom.connectionManager.add(conn);
        }

        try {
            conn.player.loadPlayerData();
        } catch (Exception e) {
            log.warn("Player {} data load failed!", playerName);
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
            stopTimeout();
            PlayerJoinEvent.getListenerList().callListeners(new PlayerJoinEvent(conn.player));
        }
    }

    private void handleHeartBeatResponse() {
        conn.pong();
    }

    private void handleAddChat(GameInputStream in) throws Exception {
        String chatmsg = in.readString();
        if (chatmsg.startsWith(".") || chatmsg.startsWith("-") || chatmsg.startsWith("_")) {
            Rukkit.getCommandManager().executeChatCommand(conn, chatmsg.substring(1));
        } else {
            if (PlayerChatEvent.getListenerList().callListeners(new PlayerChatEvent(conn.player, chatmsg))) {
                currentRoom.connectionManager.broadcast(p.chat(conn.player.name, chatmsg, conn.player.playerIndex));
            }
        }
    }

    private void handleAddGameCommand(GameInputStream in) throws Exception {
        GameCommand cmd = new GameCommand();
        cmd.arr = in.getDecodeBytes();
        GameInputStream str = new GameInputStream(cmd.arr);
        GameOutputStream out = new GameOutputStream();
        Event act = null;
        boolean isCustom = false;
        byte index, byte1, byte2;
        long l1, l2, l3, l4;
        
        index = str.readByte();
        out.writeByte(index);
        log.debug("-- Command Recording --");
        log.debug("teamIndex=" + index);

        if (str.readBoolean()) {
            log.debug("-- BasicGameAction --");
            out.writeBoolean(true);
            GameActions action = (GameActions) str.readEnum(GameActions.class);
            out.writeEnum(action);
            log.debug("Action=" + action);
            int n2 = str.readInt();
            out.writeInt(n2);
            log.debug("BuildUnit:" + n2);
            String targetUnit = "";
            if (n2 == -2) {
                targetUnit = str.readString();
                out.writeString(targetUnit);
                log.debug("Custom=" + targetUnit);
            }

            if (n2 != -1 && n2 != -2) {
                targetUnit = InternalUnit.units[n2];
            }

            float x = str.readFloat();
            out.writeFloat(x);
            float y = str.readFloat();
            out.writeFloat(y);
            long targetUnitID = str.readLong();
            out.writeLong(targetUnitID);
            log.debug("TargetUnitID=" + targetUnitID);

            byte1 = str.readByte();
            float float1 = str.readFloat();
            float float2 = str.readFloat();
            boolean bool1 = str.readBoolean();
            boolean bool2 = str.readBoolean();
            boolean bool3 = str.readBoolean();
            out.writeByte(byte1);
            out.writeFloat(float1);
            out.writeFloat(float2);
            out.writeBoolean(bool1);
            out.writeBoolean(bool2);
            out.writeBoolean(bool3);
            
            if (str.readBoolean()) {
                out.writeBoolean(true);
                String actionId = str.readString();
                log.debug("SPECIALACTIONID=" + actionId);
                out.writeString(actionId);
            } else {
                out.writeBoolean(false);
            }
            
            switch (action) {
                case BUILD:
                    act = new BuildEvent(conn.player, x, y, targetUnitID, targetUnit);
                    break;
                case MOVE:
                    act = new MoveEvent(conn.player, x, y, targetUnitID);
                    break;
            }
            log.debug("-- End BasicGameAction --");
        } else {
            out.writeBoolean(false);
        }

        boolean bool4, isCancel;
        bool4 = str.readBoolean();
        isCancel = str.readBoolean();
        out.writeBoolean(bool4);
        out.writeBoolean(isCancel);

        int int1, int2;
        int1 = str.readInt();
        int2 = str.readInt();
        out.writeInt(int1);
        out.writeInt(int2);

        if (str.readBoolean()) {
            out.writeBoolean(true);
            float f3 = str.readFloat();
            float f4 = str.readFloat();
            out.writeFloat(f3);
            out.writeFloat(f4);
        } else {
            out.writeBoolean(false);
        }

        boolean bool6 = str.readBoolean();
        int t = str.readInt();
        out.writeBoolean(bool6);
        out.writeInt(t);
        
        for (int i = 0; i < t; i++) {
            long unitidInMatch = str.readLong();
            out.writeLong(unitidInMatch);
        }

        if (str.readBoolean()) {
            out.writeBoolean(true);
            byte2 = str.readByte();
            out.writeByte(byte2);
        } else {
            out.writeBoolean(false);
        }

        float pingX = 0;
        float pingY = 0;
        if (str.readBoolean()) {
            out.writeBoolean(true);
            pingX = str.readFloat();
            pingY = str.readFloat();
            out.writeFloat(pingX);
            out.writeFloat(pingY);
        } else {
            out.writeBoolean(false);
        }

        long l6 = str.readLong();
        out.writeLong(l6);

        String buildUnit = str.readString();
        if (!buildUnit.equals("-1")) {
            if (buildUnit.startsWith("c_6_")) {
                act = new PingEvent(conn.player, pingX, pingY, buildUnit);
            } else {
                act = new TaskEvent(conn.player, buildUnit, l6, isCancel);
            }
        }
        out.writeString(buildUnit);

        boolean bool7 = str.readBoolean();
        out.writeBoolean(bool7);

        short short1 = str.readShort();
        out.stream.writeShort(32767);

        if (str.readBoolean()) {
            out.writeBoolean(true);
            str.readByte();
            out.writeByte(0);
            float f1 = str.readFloat();
            float f2 = str.readFloat();
            int i1 = str.readInt();
            out.writeFloat(f1);
            out.writeFloat(f2);
            out.writeInt(i1);
        } else {
            out.writeBoolean(false);
        }

        int movementUnitCount = str.readInt();
        out.writeInt(movementUnitCount);
        for (int i = 0; i < movementUnitCount; i++) {
            long unitid = str.readLong();
            float sx = str.readFloat();
            float sy = str.readFloat();
            float ex = str.readFloat();
            float ey = str.readFloat();
            out.writeLong(unitid);
            out.writeFloat(sx);
            out.writeFloat(sy);
            out.writeFloat(ex);
            out.writeFloat(ey);
            int timestamp = str.readInt();
            out.writeInt(timestamp);
            UnitType u = (UnitType) str.readEnum(UnitType.class);
            out.writeEnum(u);

            if (str.readBoolean()) {
                out.writeBoolean(true);
                if (str.readBoolean()) {
                    out.writeBoolean(true);
                    GzipEncoder outstr = out.getEncodeStream("p", true);
                    byte[] bytes = str.getDecodeBytes();
                    GzipDecoder dec = new GzipDecoder(bytes);
                    DataInputStream ins = dec.stream;

                    int pathCount = ins.readInt();
                    outstr.stream.writeInt(pathCount);
                    if (pathCount > 0) {
                        short unitx = ins.readShort();
                        short unity = ins.readShort();
                        outstr.stream.writeShort(unitx);
                        outstr.stream.writeShort(unity);
                        for (int i2 = 1; i2 < pathCount; i2++) {
                            int len = ins.readByte();
                            outstr.stream.writeByte(len);
                            if (len < 128) {
                                int i6 = (len & 3) - 1;
                                int i7 = ((len & 12) >> 2) - 1;
                                boolean bool = MathUtil.abs(i6) > 1 || MathUtil.abs(i7) > 1;
                                if (bool) {
                                    log.warn("Bad unit path.");
                                }
                                unitx = (short) (unitx + i6);
                                unity = (short) (unity + i7);
                            } else {
                                unitx = ins.readShort();
                                unity = ins.readShort();
                                outstr.stream.writeShort(unitx);
                                outstr.stream.writeShort(unity);
                            }
                        }
                    }
                    out.flushEncodeData(outstr);
                } else {
                    out.writeBoolean(false);
                }
            } else {
                out.writeBoolean(false);
            }
        }

        boolean bool = str.readBoolean();
        out.writeBoolean(bool);
        log.debug("-- Command recording end --");
        
        Packet packet = out.createPacket(10);
        cmd.arr = packet.bytes;
        if (act != null) {
            ListenerList list = (ListenerList) act.getClass().getMethod("getListenerList").invoke(null);
            if (list.callListeners(act)) {
                conn.sendGameCommand(cmd);
            } else {
                log.debug("Event {} cancelled!", act);
            }
        } else {
            conn.sendGameCommand(cmd);
        }
    }

    private void handleReady() {
        currentRoom.connectionManager.broadcastServerMessage(String.format("Player '%s' is ready.", conn.player.name));
    }

    private void handleSync(GameInputStream in) throws Exception {
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

    private void handleSyncChecksumResponse(GameInputStream in) throws Exception {
        in.readByte();
        int serverTick = in.readInt();
        int clientTick = in.readInt();
        log.info("[{}] Server tick: {}, Client tick: {}", conn.player.name, serverTick, clientTick);
        conn.lastSyncTick = clientTick;
        if (in.readBoolean()) {
            log.info("Player {} send checksum!", conn.player.name);
            in.readLong();
            in.readLong();
            DataInputStream din = in.getUnDecodeStream();
            din.readInt();
            int checkSumConut = din.readInt();
            log.debug("Total checksum: {}", checkSumConut);
            for (int i = 0; i < checkSumConut; i++) {
                din.readLong();
                long clientCheckData = din.readLong();
                log.trace("{}: client={}", conn.player.checkList.get(i).getDescription(), clientCheckData);
                conn.player.checkList.get(i).setCheckData(clientCheckData);
            }
            conn.currectRoom.checkSumReceived.incrementAndGet();
            conn.checkSumSent = true;
            synchronized (conn.currectRoom.checkSumReceived) {
                conn.currectRoom.checkSumReceived.notifyAll();
            }
        } else {
            log.info("Player {} did'n send checksum!We can sent back again!", conn.player.name);
            conn.doChecksum();
        }
    }

    private void handleDisconnect(GameInputStream in) throws Exception {
        String reason = in.readString();
        disconnectReason = reason;
        ctx.disconnect();
    }

    private void handleQuestionResponse(GameInputStream in) throws Exception {
        in.readByte();
        int qid = in.readInt();
        String response = in.readString();
        ServerQuestionRespondEvent.getListenerList().callListeners(new ServerQuestionRespondEvent(conn.player, qid, response));
    }

    private void stopTimeout() {
        // Implementation of stopTimeout if needed
    }
// // Original class with refactored channelRead method
// public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//     try {
//         PacketHandler handler = new PacketHandler(ctx, msg);
//         handler.handle();
//     } finally {
//         ReferenceCountUtil.release(msg);
//     }
// }
}
