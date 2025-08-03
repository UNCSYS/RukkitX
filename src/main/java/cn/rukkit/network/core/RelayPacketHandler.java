package cn.rukkit.network.core;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.rukkit.Rukkit;
import cn.rukkit.network.core.packet.Packet;
import cn.rukkit.network.core.packet.PacketType;
import cn.rukkit.network.io.GameInputStream;
import cn.rukkit.network.io.GameOutputStream;
import cn.rukkit.network.room.NetworkRoom;
import cn.rukkit.network.room.RelayNetworkRoom;
import cn.rukkit.network.room.RelayRoomConnection;
import cn.rukkit.network.room.RelayRoomManager;
import io.netty.channel.ChannelHandlerContext;

// PacketHandler.java
public class RelayPacketHandler extends PacketHandler {
    private static final Logger log = LoggerFactory.getLogger(RelayPacketHandler.class);

    private ChannelHandlerContext ctx;
    private final ConnectionHandler handler;
    private Packet packet;
    private RelayRoomConnection conn;
    private RelayNetworkRoom currentRoom;
    private String disconnectReason;
    public boolean host = true;
    public int connectionType = 1;
    public static final String SERVER_RELAY_UUID = "Dr (dr@der.kim) & Tiexiu.xyz Core Team";
    // 懒得加一个枚举了 意思如下 以后再改
    /** 链接初始化 */
    // InitialConnection, = 1
    /** 获取链接UUID-Hex */
    // GetPlayerInfo, =2
    /** 等待认证(Pow) */
    // WaitCertified, =3
    /** 认证(Pow)结束 */
    // CertifiedEnd, =4
    /** 向对应房主注册, 但还未进行游戏注册 */
    // PlayerPermission, =5
    /** 向对应房主完成注册, 且游戏完成注册 */

    // PlayerJoinPermission, =6
    /** 该链接为房间 HOST */
    // HostPermission, =7
    private final static boolean multicast = false;

    public RelayPacketHandler(ConnectionHandler handler) {
        this.handler = handler;
        currentRoom = new RelayNetworkRoom(0, null);
        conn = new RelayRoomConnection(handler, currentRoom);
    }

    @Override
    public void updateMsg(ChannelHandlerContext ctx, Object msg) {
        this.ctx = ctx;
        this.packet = (Packet) msg;
    }

    @Override
    public void onConnectionClose(ChannelHandlerContext ctx) {
        log.warn("有一个连接未激活 {}",ctx.toString());
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'onConnectionClose'");
    }

    public void handle() throws Exception {
        log.info("PacketType" + packet.type + " ConnType" + connectionType);
        if (packet.type==Packet.PACKET_HEART_BEAT_RESPONSE) {
            conn.pong();
        }
        if (relayCheck()) {
            return;
        }
        GameInputStream in = new GameInputStream(packet);
        if (host) {
            switch (packet.type) {
                case PacketType.PREREGISTER_CONNECTION:
                    //
                    break;
            }
        } else {
            switch (packet.type) {
                case PacketType.PREREGISTER_CONNECTION:
                    //
                    break;
            }

        }
    }

    private Boolean relayCheck() throws IOException {
        // lastReceivedTime = Time.concurrentMillis()
        // connectReceiveData.receiveBigPacket = false

        // val permissionStatus = con.permissionStatus

        // Check if connection is already authenticated
        if (connectionType >= 5) {
            return false;
        }

        if (connectionType == 1 && packet.type == PacketType.PREREGISTER_CONNECTION) {
            connectionType = 2;// GetPlayerInfo
            conn.setCachePacket(packet);
            GameOutputStream registerServer = new GameOutputStream();
            registerServer.writeString("net.rwhps.server.relayGetUUIDHex.Dr");// 原先为 SERVER_ID_RELAY_GET
            registerServer.writeInt(1);
            registerServer.writeInt(0);
            registerServer.writeInt(0);
            registerServer.writeString("com.corrodinggames.rts.server");
            registerServer.writeString(SERVER_RELAY_UUID);// SERVER_RELAY_UUID
            registerServer.writeInt("Dr @ 2022".hashCode());
            ctx.writeAndFlush(registerServer.createPacket(PacketType.REGISTER_CONNECTION));
        } else {
            // 原hps debug包 此处不处理 -OLD: conn.exCommand(packet);
        }

        if (connectionType == 2 && packet.type == PacketType.PLAYER_INFO) {
            relayRegisterConnection(packet);
            // Wait Certified
            connectionType = 3;
            ctx.writeAndFlush(relayServerInitInfoInternalPacket());
            sendVerifyClientValidity(); // POW 机器人验证 - 暂时未实现
            // 没有验证 ->
        }

        if (connectionType == 3 && packet.type == PacketType.RELAY_POW_RECEIVE) {
            if (receiveVerifyClientValidity(packet)) {
                // Certified End
                connectionType = 4;
                relayDirectInspection(null);
                conn.startPingTask();
            } else {
                sendVerifyClientValidity();
            }
        }

        if (connectionType == 4 && packet.type == PacketType.RELAY_118_117_RETURN) {
            sendRelayServerTypeReply(packet);
        }
        return true;
    }

    private void relayRegisterConnection(Packet packet) throws IOException {
        Packet sendPacket = packet;
        String registerPlayerId = conn.registerPlayerId;

        if (registerPlayerId == null || registerPlayerId.trim().isEmpty()) {
            try {
                GameInputStream stream = new GameInputStream(packet);
                stream.readString(); // server id
                stream.skip(12); // skip some bytes
                String name = stream.readString(); // player name
                stream.readIsString(); // read optional string
                stream.readString(); // read another string
                registerPlayerId = stream.readString(); // get player UUID hex
                conn.registerPlayerId = registerPlayerId;
                conn.playerName = name;

                log.info(name);
            } catch (Exception e) {
                log.error("[No UUID-Hex]", e);
                return;
            }
        } else if (connectionType >= 5) { // PlayerPermission or higher

            if (connectionType == 5) { /*
                                        * // PlayerPermission
                                        * if (!currentRoom.isGaming()) {
                                        * // Check for duplicate UUID in room
                                        * for (RelayRoomConnection player : currentRoom.getConnections()) {
                                        * if (player.getConnectionType() == 6 && // PlayerJoinPermission
                                        * player.getRegisterPlayerId().equals(registerPlayerId)) {
                                        * // Kick player with duplicate UUID
                                        * kick("[UUID Check] HEX 重复, 换个房间试试");
                                        * return;
                                        * }
                                        * }
                                        * }
                                        * 
                                        * // Create player data if not exists
                                        * if (conn.getPlayerRelay() == null) {
                                        * PlayerRelay playerRelay =
                                        * currentRoom.getRelayPlayersData().get(registerPlayerId);
                                        * if (playerRelay == null) {
                                        * playerRelay = new PlayerRelay(conn, registerPlayerId, conn.getName());
                                        * currentRoom.getRelayPlayersData().put(registerPlayerId, playerRelay);
                                        * }
                                        * playerRelay.setNowName(conn.getName());
                                        * playerRelay.setDisconnect(false);
                                        * playerRelay.setConnection(conn);
                                        * conn.setPlayerRelay(playerRelay);
                                        * }
                                        * 
                                        * // Check bans
                                        * if (currentRoom.getRelayKickData().containsKey("BAN" + conn.getIp())) {
                                        * kick("[BAN] 您被这个房间BAN了 请换一个房间");
                                        * return;
                                        * }
                                        * 
                                        * // Check kicks
                                        * Integer time = currentRoom.getRelayKickData().get("KICK" + registerPlayerId);
                                        * if (time == null) {
                                        * time = currentRoom.getRelayKickData().get("KICK" + conn.getIpLong24());
                                        * }
                                        * 
                                        * if (time != null) {
                                        * if (time > System.currentTimeMillis() / 1000) {
                                        * kick("[踢出等待] 您被这个房间踢出了 请稍等一段时间 或者换一个房间");
                                        * return;
                                        * } else {
                                        * currentRoom.getRelayKickData().remove("KICK" + registerPlayerId);
                                        * currentRoom.getRelayKickData().remove("KICK" + conn.getIpLong24());
                                        * }
                                        * }
                                        * 
                                        * if (currentRoom.isGaming()) {
                                        * if (!currentRoom.isSyncFlag()) {
                                        * kick("[Sync Lock] 这个房间拒绝重连");
                                        * return;
                                        * }
                                        * 
                                        * // TODO: Implement sync count check
                                        * // This would require implementing PlayerSyncCount class
                                        * }
                                        */
            }

            // Handle player replacement in ongoing game
            if (currentRoom.isGaming()) {/*
                                          * if (!currentRoom.getReplacePlayerHex().isEmpty()) {
                                          * conn.setReplacePlayerHex(currentRoom.getReplacePlayerHex());
                                          * currentRoom.setReplacePlayerHex("");
                                          * currentRoom.sendMsg("玩家 " + conn.getName() + ", 取代了旧玩家");
                                          * }
                                          * if (!conn.getReplacePlayerHex().isEmpty()) {
                                          * GameOutputStream out = new GameOutputStream();
                                          * GameInputStream stream = new GameInputStream(packet);
                                          * out.writeString(stream.readString());
                                          * out.transferToFixedLength(stream, 12);
                                          * out.writeString(stream.readString());
                                          * out.writeIsString(stream);
                                          * out.writeString(stream.readString());
                                          * out.writeString(conn.getReplacePlayerHex());
                                          * stream.readString();
                                          * out.transferTo(stream);
                                          * sendPacket = out.createPacket(PacketType.PLAYER_INFO);
                                          * }
                                          */
            }

            connectionType = 6; // PlayerJoinPermission
            sendPackageToHOST(sendPacket);
        }
    }

    private static NetConnectProofOfWork netConnectAuthenticate = new NetConnectProofOfWork();

    public void sendVerifyClientValidity() {
        int authenticateType = netConnectAuthenticate.getAuthenticateType();
        try {
            GameOutputStream o = new GameOutputStream();
            o.writeInt(netConnectAuthenticate.getResultInt());
            o.writeInt(authenticateType);

            if (authenticateType == 0 || (authenticateType >= 2 && authenticateType <= 4) || authenticateType == 6) {
                o.writeBoolean(true);
                o.writeInt(netConnectAuthenticate.getInitInt_1());
            } else {
                o.writeBoolean(false);
            }

            if (authenticateType == 1 || (authenticateType >= 2 && authenticateType <= 4)) {
                o.writeBoolean(true);
                o.writeInt(netConnectAuthenticate.getInitInt_2());
            } else {
                o.writeBoolean(false);
            }

            if (authenticateType >= 5 && authenticateType <= 6) {
                o.writeString(netConnectAuthenticate.getOutcome());
                o.writeString(netConnectAuthenticate.getFixedInitial());
                o.writeInt(netConnectAuthenticate.getMaximumNumberOfCalculations());
            }

            o.writeBoolean(false);
            ctx.writeAndFlush(o.createPacket(PacketType.RELAY_POW));
        } catch (Exception e) {
        }
    }

    public boolean receiveVerifyClientValidity(Packet packet) throws IOException {
        GameInputStream inStream = new GameInputStream(packet);
        if (netConnectAuthenticate != null) {
            if (netConnectAuthenticate.verifyPOWResult(
                    inStream.readInt(),
                    inStream.readInt(),
                    inStream.readString())) {
                netConnectAuthenticate = new NetConnectProofOfWork(); //= null
                return true;
            }
        } else {
            // Ignore, Under normal circumstances, it should not reach here,
            // and the processor will handle it
        }
        // Ignore, There should be no errors in this part,
        // errors will only come from constructing false error packets
        return false;
    }

    private void sendPackageToHOST(Packet packet) throws IOException {/*
                                                                       * GameOutputStream o = new GameOutputStream();
                                                                       * o.writeInt(conn.getSite());
                                                                       * o.writeInt(packet.bytes.length + 8);
                                                                       * o.writeInt(packet.bytes.length);
                                                                       * o.writeBytes(packet.type.typeIntBytes);
                                                                       * o.writeBytes(packet.bytes);
                                                                       * currentRoom.getAdmin().getCtx().writeAndFlush(o
                                                                       * .createPacket(PacketType.
                                                                       * PACKET_FORWARD_CLIENT_FROM));
                                                                       */
    }

    private static Packet relayServerInitInfoInternalPacket() throws IOException {
        GameOutputStream o = new GameOutputStream();
        o.writeByte(0);
        // RELAY Version
        o.writeInt(151);
        // ?
        o.writeInt(1);
        // ?
        o.writeBoolean(false);
        return o.createPacket(PacketType.RELAY_VERSION_INFO);
    }

    public String relayServerTypeReplyInternalPacket(Packet packet) throws IOException {
        GameInputStream inStream = new GameInputStream(packet);
        // Skip the previously useless data
        inStream.skip(5);
        // Read data and remove leading and trailing spaces
        return inStream.readString().trim();

    }

    void sendRelayServerTypeInternal(String msg) throws IOException {
        GameOutputStream o = new GameOutputStream();
        // Theoretically random numbers?
        o.writeByte(1);
        o.writeInt(5); // 可能和-AX一样 // QuestionID
        // Msg
        o.writeString(msg);
        log.info("============debug 1");
        ctx.writeAndFlush(o.createPacket(PacketType.RELAY_117)); /// -> 118
    }

    private static boolean isBlank(Object string) {
        return string == null || "".equals(string.toString().trim());
    }

    public String relaySelect;

    public void relayDirectInspection(RelayNetworkRoom relayRoom) throws IOException {
        GameInputStream inStream = new GameInputStream(conn.cachePacket);
        inStream.readString();
        int packetVersion = inStream.readInt();
        int clientVersion = inStream.readInt();
        // betaGameVersion = getBetaVersion(clientVersion);

        if (packetVersion >= 1) {
            inStream.skip(4);
        }
        String queryString = "";
        if (packetVersion >= 2) {
            queryString = inStream.readIsString();
        }
        if (packetVersion >= 3) {
            // Player Name
            inStream.readString();
        }

        log.info("============debug rdi room=null is-{}", relayRoom == null);
        if (relayRoom == null) {
            if (isBlank(queryString) || "RELAYCN".equalsIgnoreCase(queryString)) {
                sendRelayServerTypeInternal("[Relay CN+ #0] 这台服务器是CN非官方的Relay房间");// Data.SERVER_CORE_VERSION
                relaySelect = "3.0.0";
            } else {
                idCustom(queryString);
            }
        } else {
            // this.room = relayRoom;
            // addRelayConnect();
        }
    }

    public void sendRelayServerTypeReply(Packet packet) {
        try {

            String id = relayServerTypeReplyInternalPacket(packet);
            log.info("debug Question Responed {}",id);
            if (relaySelect == null) {
                idCustom(id);
            } else {
                // relaySelect.apply(id); //TODO: ///////////
            }
        } catch (Exception e) {
        }
    }

    private Packet fromRelayJumpsToAnotherServerInternalPacket(String ip) throws IOException {
        GameOutputStream o = new GameOutputStream();
        // The message contained in the package
        o.writeByte(0);
        // Protocol version? (I don't know)
        o.writeInt(3);
        // Debug
        o.writeBoolean(false);
        // For
        o.writeInt(1);
        o.writeString(ip);

        return o.createPacket(PacketType.PACKET_RECONNECT_TO);
    }

    private void idCustom(String inId) throws IOException {
        // 过滤制表符、空格、换行符
        String id = inId.replaceAll("\\s", "");

        // 处理"old"关键字
        // if ("old".equalsIgnoreCase(id)) {
        // id = RelayRoom.serverRelayOld.getOrDefault(registerPlayerId, "");
        // } else {
        // RelayRoom.serverRelayOld.put(registerPlayerId, id);
        // }

        if (id.isEmpty()) {
            sendRelayServerTypeInternal("[提示] 请输入房间ID或'new'创建新房间");
            return;
        }

        // 检查Emoji
        if (containsEmoji(id)) {
            sendRelayServerTypeInternal("[错误] 不能使用Emoji");
            return;
        }

        // 新建房间逻辑
        if (id.equalsIgnoreCase("new")) {
            createNewRoom();
            return;
        }

        // 加入现有房间逻辑
        try {
            if (id.contains(".")) {
                sendRelayServerTypeInternal("[错误] ID不能包含点号(.)");
                return;
            }

            if (RelayRoomManager.containsRoom(Integer.parseInt(id))) {
                // addRelayConnect(room);
                currentRoom = RelayRoomManager.getRoom(Integer.parseInt(id));
                connectionType = 6;
            } else {
                sendRelayServerTypeInternal("[错误] 找不到房间: " + id);
            }
        } catch (Exception e) {
            log.debug("Error finding relay room", e);
            sendRelayServerTypeInternal("[错误] " + e.getMessage());
        }
    }

    private void addRelayConnect() {

    }

    // 创建新房间
    private void createNewRoom() throws IOException {
        try {
            // 由服务器自动生成房间ID
            currentRoom = new RelayNetworkRoom(10000, this.conn);
            RelayRoomManager.addRelayRoom(currentRoom);

            // 发送成功消息
            sendRelayServerTypeInternal("[成功] 已创建新房间，ID: " + currentRoom.roomId);

            // 设置默认参数
            sendDefaultRoomSettings();

            // 设置为房主
            sendRelayServerId();
        } catch (Exception e) {
            log.error("Failed to create new room", e);
            sendRelayServerTypeInternal("[错误] 创建房间失败: " + e.getMessage());
        }
    }

    private static int clientVersion = 151;

    // 发送默认房间设置
    private void sendDefaultRoomSettings() {
        try {
            // 发送基本注册信息
            GameOutputStream registerServer = new GameOutputStream();
            registerServer.writeString("net.rwhps.server");
            registerServer.writeInt(1);
            registerServer.writeInt(clientVersion);
            registerServer.writeInt(clientVersion);
            registerServer.writeString("com.corrodinggames.rts.server");
            registerServer.writeString(SERVER_RELAY_UUID);
            registerServer.writeInt("Dr @ 2022".hashCode());
            ctx.writeAndFlush(registerServer.createPacket(PacketType.REGISTER_CONNECTION));

            // 发送服务器信息
            GameOutputStream serverInfo = new GameOutputStream();
            serverInfo.writeString("net.rwhps.server.relay");
            serverInfo.writeInt(clientVersion);
            serverInfo.writeInt(1); // MapType.CustomMap
            serverInfo.writeString("RW-HPS RELAY 默认房间");
            serverInfo.writeInt(0); // credits
            serverInfo.writeInt(2); // mist
            serverInfo.writeBoolean(true);
            serverInfo.writeInt(1);
            serverInfo.writeByte(0);
            serverInfo.writeBoolean(false);
            serverInfo.writeBoolean(false);
            ctx.writeAndFlush(serverInfo.createPacket(PacketType.SERVER_INFO));

            // 发送默认队伍设置
            GameOutputStream teamList = new GameOutputStream();
            teamList.writeInt(0);
            teamList.writeBoolean(false);
            teamList.writeInt(10); // 默认10玩家
            byte[] teamsData = new byte[10]; // 10个玩家的初始数据
            Arrays.fill(teamsData, (byte) 0);
            teamList.write(teamsData);
            teamList.writeInt(2); // mist
            teamList.writeInt(0); // credits
            teamList.writeBoolean(true);
            teamList.writeInt(1);
            teamList.writeByte(5);
            teamList.writeInt(200); // 默认200单位
            teamList.writeInt(200);
            teamList.writeInt(1); // initUnit
            teamList.writeFloat(1.0f); // 默认1.0收入
            teamList.writeBoolean(true); // Ban nuke
            teamList.writeBoolean(false);
            teamList.writeBoolean(false);
            teamList.writeBoolean(false); // sharedControl
            teamList.writeBoolean(false); // gamePaused
            ctx.writeAndFlush(teamList.createPacket(PacketType.TEAM_LIST));

            // 发送欢迎消息
            ctx.writeAndFlush(Packet.chat(
                    "欢迎来到RW-HPS RELAY服务器",
                    "系统",
                    5));
        } catch (IOException e) {
            log.error("Failed to send default room settings", e);
        }
    }

    // 辅助方法保持不变
    private boolean idDistribute(String id) {
        return false;
    }

    private boolean containsEmoji(String input) {
        return input.matches(".*[\\p{So}].*");
    }

    public void sendRelayServerId() throws IOException {
        // 确保连接已准备好
        // connectReceiveData.setInputPassword(false);

        if (currentRoom == null) {
            log.info("sendRelayServerId -> relay : null");
            // currentRoom = NetStaticData.getRelayRoom();
        }

        // 如果已有位置(site != -1)，先移除旧连接
        // if (site != -1) {
        // currentRoom.removeAbstractNetConnect(site);
        // // -2 表示这是第二任房主
        // site = -2;
        // }

        // 设置当前连接为房主
        currentRoom.adminConn = this.conn;
        host = true;
        connectionType = 7; // HostPermission

        boolean isPublic = false; // 默认房间不公开
        GameOutputStream o = new GameOutputStream();

        if (clientVersion >= 172) {
            // 新协议版本(172+)的数据包格式
            o.writeByte(2);
            o.writeBoolean(true); // allowThisConnectionForwarding
            o.writeBoolean(true); // removeThisConnection
            o.writeBoolean(true); // ?
            o.writeString(SERVER_RELAY_UUID);
            o.writeBoolean(false); // MOD标记
            o.writeBoolean(isPublic); // 是否公开
            o.writeBoolean(true); // ?
            o.writeString(
                    "{{RELAY-CN}} Room ID : (未完成) \n" +
                            "你的房间是 <" + (isPublic ? "开放" : "隐藏") + "> 在列表\n" +
                            "This Server Use RW-HPS Project (Test)");
            o.writeBoolean(multicast); // 是否使用组播
            o.writeIsString(conn.registerPlayerId); // 注册玩家ID
        } else {
            // 旧协议版本的数据包格式
            o.writeByte(1);
            o.writeBoolean(true); // allowThisConnectionForwarding
            o.writeBoolean(true); // removeThisConnection
            o.writeIsString(SERVER_RELAY_UUID); // 服务器UUID
            o.writeBoolean(false); // MOD标记
            o.writeBoolean(isPublic); // 是否公开
            o.writeString(
                    "{{RELAY-CN}} Room ID : (未完成) \n" +
                            "你的房间是 <" + (isPublic ? "开放" : "隐藏") + "> 在列表\n" +
                            "This Server Use RW-HPS Project");
            o.writeBoolean(multicast); // 是否使用组播
        }

        // 发送RELAY_BECOME_SERVER数据包
        ctx.writeAndFlush(o.createPacket(PacketType.RELAY_BECOME_SERVER));

        // 禁止玩家使用 Server/Relay 做玩家名
        if (conn.playerName.equalsIgnoreCase("SERVER") || conn.playerName.equalsIgnoreCase("RELAY")) {
            // currentRoom.closeRoom(); // 关闭房间
            log.error("有人使用了SEVR作为玩家名字");
        }
    }
}
