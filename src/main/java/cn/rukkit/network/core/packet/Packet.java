/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.network.core.packet;

import cn.rukkit.*;
import cn.rukkit.config.RoundConfig;
import cn.rukkit.game.PingType;
import cn.rukkit.game.map.CustomMapLoader;
import cn.rukkit.game.mod.Mod.*;
import cn.rukkit.network.command.*;
import cn.rukkit.network.io.GameOutputStream;
import cn.rukkit.network.room.NetworkRoom;
import cn.rukkit.util.*;
import java.io.*;
import java.util.*;
import cn.rukkit.game.unit.InternalUnit;
import cn.rukkit.game.GameActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Packet {
	//Server Commands
	public static final int PACKET_REGISTER_CONNECTION = 161;//A1
	public static final int PACKET_TEAM_LIST = 115;//73
	public static final int PACKET_HEART_BEAT = 108;//6C
	public static final int PACKET_SEND_CHAT = 141;//8D
	public static final int PACKET_SERVER_INFO = 106;//6A
	public static final int PACKET_START_GAME = 120;//78
	public static final int PACKET_QUESTION = 117;//75
	public static final int PACKET_QUESTION_RESPONCE = 118;//76
	public static final int PACKET_KICK = 150;

	//Client Commands
	public static final int PACKET_PREREGISTER_CONNECTION = 160;//A0
	public static final int PACKET_HEART_BEAT_RESPONSE = 109;//6D 心跳包应答
	public static final int PACKET_ADD_CHAT = 140;//8C
	public static final int PACKET_PLAYER_INFO = 110;//6E
	public static final int PACKET_DISCONNECT = 111;//6F
	public static final int PACKET_RANDY = 112;//70 应该是ready RW-HPS 给出的是ACCEPT_START_GAME


	//Game Commands
	public static final int PACKET_ADD_GAMECOMMAND = 20;//14
	public static final int PACKET_TICK = 10;//0A
	public static final int PACKET_SYNC_CHECKSUM = 30;//1E
	public static final int PACKET_SYNC_CHECKSUM_RESPONCE = 31;//1F
    public static final int PACKET_SYNC = 35;//23

	
	private static final Logger log = LoggerFactory.getLogger(Packet.class);
	public byte[] bytes;
    public int type;

    public Packet(int type) {
        this.type = type;
    }

	public Packet() {
		this.type = 0;
	}
}
