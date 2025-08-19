/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package cn.rukkit.network.core.packet;

import cn.rukkit.*;
import cn.rukkit.command.CommandHandler;
import cn.rukkit.game.NetworkPlayer;
import cn.rukkit.network.*;
import cn.rukkit.network.io.GameOutputStream;
import cn.rukkit.util.*;
import java.io.*;
import java.util.*;
import java.net.InetAddress;
import java.sql.Time;
import java.util.regex.Pattern;

import javax.xml.crypto.Data;

import java.util.regex.Matcher;

import org.jline.utils.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketRelay {

    private static final int newRelayProtocolVersion = 172;

    public static Packet relayServerInitInfoInternalPacket() {
        GameOutputStream o = new GameOutputStream();
        try {
            o.writeByte(0);
            o.writeInt(151);// RELAY Version
            o.writeInt(1);// ?
            o.writeBoolean(false);// ?
        } catch (IOException e) {
            e.printStackTrace();
        }
        return o.createPacket(PacketType.RELAY_VERSION_INFO);
    }

    public static Packet relayServerIdPacket(Boolean multicast,NetworkPlayer player,int clientVersion) {
            GameOutputStream o = new GameOutputStream();
        try {

            /*if (site != -1) {
                room!!.removeAbstractNetConnect(site)
                // 这个代表是第二任 (后妈)
                site = -2
            } */

            player.isAdmin = true;
            boolean isPublic = false;
            if (clientVersion >= newRelayProtocolVersion) {
                o.writeByte(2);
                o.writeBoolean(true);
                o.writeBoolean(true);
                o.writeBoolean(true);
                o.writeString(/*serverUuid*/"1234567890"); //TODO: ok this
                o.writeBoolean(false); //MOD
                o.writeBoolean(isPublic);
                o.writeBoolean(true);
                o.writeString(
                """
                    {{RELAY-CN}} Room ID : ${Data.configRelay.mainID + room!!.id}
                    你的房间是 <${if (public) "开放" else "隐藏"}> 在列表
                    This Server Use RW-HPS Project (Test)
                """
                );
                o.writeBoolean(multicast);
                //TODO: o.writeIsString(registerPlayerId);
            } else {
                // packetVersion
                o.writeByte(1);
                // allowThisConnectionForwarding
                o.writeBoolean(true);
                // removeThisConnection
                o.writeBoolean(true);
                // useServerId
                o.writeString(/*serverUuid*/"1234567890");//TODO: writeIsString
                // useMods
                o.writeBoolean(false); //MOD
                // showPublicly
                o.writeBoolean(isPublic);
                // relayMessageOnServer
                o.writeString(
                """
                    {{RELAY-CN}} Room ID : ${Data.configRelay.mainID + room!!.id}
                    你的房间是 <${if (public) "开放" else "隐藏"}> 在列表
                    This Server Use RW-HPS Project
                """
                );
                // useMulticast
                o.writeBoolean(multicast);
            }

            /* //这两个是无关紧要的聊天
            sendPacket(
                    rwHps.abstractNetPacket.getChatMessagePacket(
                            Data.i18NBundle.getinput(
                                    "relay.server.admin.connect",
                                    Data.configRelay.mainID + room!!.id,
                                    Data.configRelay.mainID + room!!.internalID.toString()
                            ), "RELAY_CN-ADMIN", 5
                    )
            );
            sendPacket(
                    rwHps.abstractNetPacket.getChatMessagePacket(
                            Data.i18NBundle.getinput("relay", Data.configRelay.mainID + room!!.id), "RELAY_CN-ADMIN", 5
                    )
            );*/

            //TODO: this
            // 人即像树，树枝越向往光明的天空，树根越伸向阴暗的地底
            /**
            //禁止玩家使用 Server/Relay 做玩家名
            
            if (name.equals("SERVER", ignoreCase = true) || name.equals("RELAY", ignoreCase = true)) {
                room!!.re() // Close Room
            } */
        }catch(IOException e){

        }
        return o.createPacket(PacketType.RELAY_BECOME_SERVER);
    }

    /*
    public static Packet addRelayConnect() {
        try {
            permissionStatus = RelayStatus.PlayerPermission

            connectReceiveData.inputPassword = false
            if (room == null) {
                Log.clog("?????")
                room = NetStaticData.relayRoom
            }


            site = room!!.setAddPosition()
            room!!.setAbstractNetConnect(this)

            val o = GameOutputStream()
            if (clientVersion >= newRelayProtocolVersion) {
                o.writeByte(1)
                o.writeInt(site)
                // ?
                o.writeString(registerPlayerId!!)
                //o.writeBoolean(false)
                // User UUID
                o.writeIsString(null)
                o.writeIsString(ip)
                room!!.admin!!.sendPacket(o.createPacket(PacketType.FORWARD_CLIENT_ADD))
            } else {
                o.writeByte(0)
                o.writeInt(site)
                o.writeString(registerPlayerId!!)
                o.writeIsString(null)
                room!!.admin!!.sendPacket(o.createPacket(PacketType.FORWARD_CLIENT_ADD))
            }

            sendPackageToHOST(cachePacket!!)
            connectionAgreement.add(room!!.groupNet)
            sendPacket(
                    rwHps.abstractNetPacket.getChatMessagePacket(
                            Data.i18NBundle.getinput("relay", Data.configRelay.mainID + room!!.id), "RELAY_CN-ADMIN", 5
                    )
            )
            this.room!!.setAddSize()
        } catch (e: Exception) {
            permissionStatus = RelayStatus.CertifiedEnd

            connectionAgreement.remove(room!!.groupNet)

            error("[Relay] addRelayConnect", e)

            relayDirectInspection()
            return
        } finally {
            //cachePacket = null;
        }
    }*/
}