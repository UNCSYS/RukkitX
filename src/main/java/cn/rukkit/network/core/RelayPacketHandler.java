package cn.rukkit.network.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.sql.Time;

import javax.xml.crypto.Data;

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
import cn.rukkit.network.room.RelayNetworkRoom;
import cn.rukkit.network.room.RelayRoomConnection;
import cn.rukkit.network.room.RoomConnection;
import cn.rukkit.util.LangUtil;
import cn.rukkit.util.MathUtil;
import io.netty.channel.ChannelHandlerContext;

// PacketHandler.java
public class RelayPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(RelayPacketHandler.class);

    private final ChannelHandlerContext ctx;
    private final ConnectionHandler handler;
    private Packet packet;
    private RelayRoomConnection conn;
    private RelayNetworkRoom currentRoom;
    private String disconnectReason;
    public boolean host = true;
    public int connectionType = 1;
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

    public RelayPacketHandler(ChannelHandlerContext ctx, Object msg, ConnectionHandler handler) {
        this.ctx = ctx;
        this.packet = (Packet) msg;
        this.handler = handler;

        currentRoom = new RelayNetworkRoom(0);
        conn = new RelayRoomConnection(handler, currentRoom);
    }
    public void updateMsg(Object msg){
        this.packet = (Packet) msg;
    }

    public void handle() throws Exception {
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

        // if (permissionStatus.ordinal >= PlayerPermission.ordinal) {
        // return false
        // }

        if (packet.type == PacketType.PREREGISTER_CONNECTION) {
            connectionType = 2;// GetPlayerInfo
            conn.setCachePacket(packet);
            GameOutputStream registerServer = new GameOutputStream();
            registerServer.writeString("net.rwhps.server.relayGetUUIDHex.Dr");// 原先为 SERVER_ID_RELAY_GET
            registerServer.writeInt(1);
            registerServer.writeInt(0);
            registerServer.writeInt(0);
            registerServer.writeString("com.corrodinggames.rts.server");
            registerServer.writeString("Dr (dr@der.kim) & Tiexiu.xyz Core Team");// SERVER_RELAY_UUID
            registerServer.writeInt("Dr @ 2022".hashCode());
            ctx.writeAndFlush(registerServer.createPacket(PacketType.REGISTER_CONNECTION));
        } else {
            // 原hps debug包 此处不处理 -OLD: conn.exCommand(packet);
        }

        if (packet.type == PacketType.PLAYER_INFO) {
            relayRegisterConnection(packet);
            // Wait Certified
            ctx.writeAndFlush(relayServerInitInfoInternalPacket());
            connectionType = 4; // 如果实现机器人验证 则改为 3
            // con.sendVerifyClientValidity(); //POW 机器人验证 - 暂时未实现
            // 没有验证 ->
            relayDirectInspection(null);
        }

        if (packet.type == PacketType.RELAY_118_117_RETURN) {
            sendRelayServerTypeReply(packet);
        }
        return true;
    }

    private void relayRegisterConnection(Packet packet) {
        Packet sendPacket = packet;
        String registerPlayerId = null;

        if (registerPlayerId == null) {// 原.isNullOrBlank()
            try {
                GameInputStream stream = new GameInputStream(packet);
                stream.readString();
                stream.skip(12);
                String name = stream.readString();
                stream.readIsString();
                stream.readString();
                registerPlayerId = stream.readString();
            } catch (Exception e) {
                log.error("[No UUID-Hex]", e);
            }
        }
        // 此处代码未完成 ============================= TODO: finish this ！！
        /*
         * else if (permissionStatus.ordinal >= RelayStatus.PlayerPermission.ordinal) {
         * if (permissionStatus == RelayStatus.PlayerPermission) {
         * if (!currentRoom.isStartGame()) {
         * currentRoom.abstractNetConnectIntMap.forEach((key, player) -> {
         * if (player.getPermissionStatus() == RelayStatus.PlayerJoinPermission &&
         * player.getRegisterPlayerId().equals(registerPlayerId)) {
         * //
         * // 通过检测房间已有的 UUID-Hex, 来激进的解决一些问题
         * //
         * // Luke 对此的回答 :
         * // True might make more sense, but a connection might be stale for a bit need
         * to be careful with that.
         * // example just after a crash
         * //
         * // kick("[UUID Check] HEX 重复, 换个房间试试");
         * return true;
         * }
         * return false;
         * });
         * return;
         * }
         * 
         * // 这里的代码任然未完成同步 ======================== TODO: Finish this!
         * 
         * // Relay-EX
         * if (playerRelay == null) {
         * playerRelay = room!!.relayPlayersData[registerPlayerId] ?: PlayerRelay(this,
         * registerPlayerId!!, name).also {
         * room!!.relayPlayersData[registerPlayerId!!] = it
         * }
         * playerRelay!!.nowName = name
         * playerRelay!!.disconnect = false
         * playerRelay!!.con = this
         * }
         * 
         * if (room!!.relayKickData.containsKey("BAN$ip")) {
         * kick("[BAN] 您被这个房间BAN了 请换一个房间")
         * return
         * }
         * 
         * val time: Int? = room!!.relayKickData["KICK$registerPlayerId"].ifNullResult(
         * currentRoom!!.relayKickData["KICK${connectionAgreement.ipLong24}"]
         * ) { null }
         * 
         * if (time != null) {
         * if (time > Time.concurrentSecond()) {
         * kick("[踢出等待] 您被这个房间踢出了 请稍等一段时间 或者换一个房间")
         * return
         * } else {
         * room!!.relayKickData.remove("KICK$registerPlayerId")
         * room!!.relayKickData.remove("KICK${connectionAgreement.ipLong24}")
         * }
         * }
         * 
         * if (currentRoom.isStartGame) {
         * if (!currentRoom.syncFlag) {
         * kick("[Sync Lock] 这个房间拒绝重连")
         * return
         * }
         * 
         * // TODO: 完成同步数量检查
         * // if (playerRelay.playerSyncCount.checkStatus()) {
         * // playerRelay.playerSyncCount.count++
         * // } else {
         * // room!!.relayKickData["KICK$registerPlayerId"] = 300
         * // kick("[同步检测] 您同步次数太多 请稍等一段时间 或者换一个房间")
         * // return
         * // }
         * }
         * }
         * 
         * if (conn.currectRoom.isGaming()) {
         * if (room.replacePlayerHex != "") {
         * replacePlayerHex = room.replacePlayerHex;
         * room.replacePlayerHex = "";
         * room.sendMsg("玩家 $name, 取代了旧玩家");
         * }
         * if (replacePlayerHex != "") {
         * GameOutputStream out = new GameOutputStream();
         * GameInputStream stream = new GameInputStream(packet);
         * out.writeString(stream.readString());
         * out.transferToFixedLength(stream, 12);
         * out.writeString(stream.readString());
         * out.writeIsString(stream);
         * out.writeString(stream.readString());
         * out.writeString(replacePlayerHex);
         * stream.readString();
         * out.transferTo(stream);
         * sendPacket = out.createPacket(PacketType.REGISTER_PLAYER);
         * 
         * }
         * }
         * 
         * connectionType = 6; //PlayerJoinPermission
         * //sendPackageToHOST(sendPacket);
         * }
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

    Packet relayServerTypeInternal(String msg) throws IOException {
        GameOutputStream o = new GameOutputStream();
        // Theoretically random numbers?
        o.writeByte(1);
        o.writeInt(5); // 可能和-AX一样
        // Msg
        o.writeString(msg);
        return o.createPacket(PacketType.RELAY_117); /// -> 118
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
        if (relayRoom == null) {
            if (isBlank(queryString) || "RELAYCN".equalsIgnoreCase(queryString)) {
                relayServerTypeInternal("[Relay CN+ #0] 这台服务器是CN非官方的Relay房间 ");// Data.SERVER_CORE_VERSION
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
            if (relaySelect == null) {
                idCustom(id);
            } else {
                // relaySelect.apply(id); //TODO: ///////////
            }
        } catch (Exception e) {
        }
    }

    Packet fromRelayJumpsToAnotherServerInternalPacket(String ip) throws IOException {
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

    private void idCustom(String inId) {
        /*
         * // 过滤 制表符 空格 换行符
         * String id = inId.replaceAll("\\s", "");
         * if ("old".equalsIgnoreCase(id)) {
         * id = RelayRoom.serverRelayOld.get(registerPlayerId, "");
         * } else {
         * RelayRoom.serverRelayOld.put(registerPlayerId, id);
         * }
         * 
         * if (id.isEmpty()) {
         * relayServerTypeInternal("什么都没有输入");
         * return;
         * }
         * if (EmojiManager.containsEmoji(id)) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.server.error.id",
         * "不能使用Emoji"));
         * return;
         * }
         * 
         * if (idDistribute(id)) {
         * if (Data.configRelay.mainServer) {
         * //挑转包
         * sendPacket(fromRelayJumpsToAnotherServerInternalPacket("127.0.0.1"));//原id.
         * charAt(1) + ".relay.rwhps.net/" + id
         * return;
         * } else {
         * id = id.substring(2);
         * }
         * } else if ("R".equalsIgnoreCase(String.valueOf(id.charAt(0)))) {
         * id = id.substring(1);
         * }
         * 
         * if (id.isEmpty()) {
         * relayServerTypeInternal("你输入为空 输入new");
         * return;
         * }
         * 
         * boolean uplist = false;
         * boolean mods = id.toLowerCase().contains("mod");
         * String customId = "";
         * boolean newRoom = true;
         * 
         * if ("C".equalsIgnoreCase(String.valueOf(id.charAt(0)))) {
         * id = id.substring(1);
         * if (id.isEmpty()) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.id.re"));
         * return;
         * }
         * 
         * String[] ary = id.split("@");
         * id = ary.length > 1 ? ary[1] : "";
         * customId = ary[0];
         * 
         * if ("M".equalsIgnoreCase(String.valueOf(customId.charAt(0)))) {
         * customId = customId.substring(1);
         * if (!checkLength(customId)) {
         * return;
         * }
         * if (RelayRoom.getCheckRelay(customId) || idDistribute("R" + customId)) {
         * relayServerTypeInternal("太长或者太短");
         * return;
         * }
         * mods = true;
         * } else {
         * if (!checkLength(customId)) {
         * return;
         * }
         * if (RelayRoom.getCheckRelay(customId) || idDistribute("R" + customId)) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.id.re"));
         * return;
         * }
         * }
         * } else {
         * if (id.toLowerCase().startsWith("news") ||
         * id.toLowerCase().startsWith("mods")) {
         * id = id.substring(4);
         * } else if (id.toLowerCase().startsWith("new") ||
         * id.toLowerCase().startsWith("mod")) {
         * id = id.substring(3);
         * } else {
         * newRoom = false;
         * }
         * }
         * 
         * if (newRoom) {
         * CustomRelayData custom = new CustomRelayData();
         * 
         * try {
         * if (id.toUpperCase().startsWith("P")) {
         * id = id.substring(1);
         * String[] arry = id.contains("，") ? id.split("，") : id.split(",");
         * custom.maxPlayerSize = Integer.parseInt(arry[0]);
         * if (custom.maxPlayerSize < 0 || custom.maxPlayerSize > 100) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.id.maxPlayer.re"));
         * return;
         * }
         * if (arry.length > 1) {
         * if (arry[1].toUpperCase().contains("I")) {
         * String[] ay = arry[1].split("(?i)I");
         * if (ay.length > 1) {
         * custom.maxUnitSizt = Integer.parseInt(ay[0]);
         * custom.income = Float.parseFloat(ay[1]);
         * } else {
         * custom.income = Float.parseFloat(ay[0]);
         * }
         * } else {
         * custom.maxUnitSizt = Integer.parseInt(arry[1]);
         * }
         * if (custom.maxUnitSizt < 0) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.id.maxUnit.re"));
         * return;
         * }
         * }
         * }
         * if (id.toUpperCase().startsWith("I")) {
         * id = id.substring(1);
         * String[] arry = id.contains("，") ? id.split("，") : id.split(",");
         * custom.income = Float.parseFloat(arry[0]);
         * if (custom.income < 0) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.id.income.re"));
         * return;
         * }
         * }
         * } catch (NumberFormatException e) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.server.error",
         * e.getMessage()));
         * return;
         * }
         * 
         * newRelayId(customId, mods, custom);
         * if (custom.maxPlayerSize != -1 || custom.maxUnitSizt != 200) {
         * sendPacket(
         * rwHps.abstractNetPacket.getChatMessagePacket(
         * "自定义人数: " + custom.maxPlayerSize + " 自定义单位: " + custom.maxUnitSizt,
         * "RELAY_CN-Custom",
         * 5));
         * }
         * } else {
         * try {
         * if (id.contains(".")) {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.server.error",
         * "不能包含 [ . ]"));
         * return;
         * }
         * room = RelayRoom.getRelay(id);
         * if (room != null) {
         * addRelayConnect();
         * } else {
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.server.no", id));
         * }
         * } catch (Exception e) {
         * debug(e);
         * relayServerTypeInternal(Data.i18NBundle.getinput("relay.server.error",
         * e.getMessage()));
         * }
         * }
         */
    }
}
