package cn.rukkit.network.room;

import cn.rukkit.Rukkit;
import cn.rukkit.config.RoundConfig;
import cn.rukkit.config.RukkitConfig;
import cn.rukkit.event.room.RoomStartGameEvent;
import cn.rukkit.event.room.RoomStopGameEvent;
import cn.rukkit.game.*;
import cn.rukkit.network.command.GameCommand;
import cn.rukkit.network.core.packet.Packet;
import cn.rukkit.util.Vote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class RelayNetworkRoom {
    public PlayerManager playerManager;
    public RoomConnectionManager connectionManager;
    /**
     * 命令列表。在采用更稳定的同步(useCommandQuere)时会启用，减少同步错误但是会提高操作延迟。
     */
    private LinkedList<GameCommand> commandQuere = new LinkedList<GameCommand>();

    public RoundConfig config;
    public int stepRate = 200;
    public int currentStep = 0;
    public int checkSumFrame = 0;
    public final AtomicInteger checkSumReceived = new AtomicInteger();
    public int syncCount = 0;
    public int roomId;
    public RelayRoomConnection adminConn;

    private volatile boolean checkRequested = false;

    /**
     * NoStop模式下的房间存档
     */
    public SaveData lastNoStopSave;
    private boolean isGaming = false;
    private boolean isPaused = false;
    private ScheduledFuture gameTaskFuture;
    private SaveManager saveManager;

    public Vote vote;

    @Override
    public String toString() {
        return MessageFormat.format("NetworkRoom [id = {0}, isGaming = {1}, isPaused = {2}, currentStep = {3}, stepRate = {4}]",
                roomId, isGaming, isPaused, currentStep, stepRate);
    }

    public RelayNetworkRoom(int id,RelayRoomConnection adminConn) {
        // 指定房间id
        roomId = id;
        this.adminConn = adminConn;
        //初始化玩家控制器，连接控制器，和存档管理器
        //playerManager = new PlayerManager(this, Rukkit.getConfig().maxPlayer);
        //connectionManager = new RoomConnectionManager(this);
        //saveManager = new SaveManager(this);
        //config = Rukkit.getRoundConfig();
        //vote = new Vote(this);
    }






    //=========================================

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        isPaused = paused;
    }

    /**
     * Broadcast a packet.
     * @param packet the packet wants to broadcast in this room.
     */
    public void broadcast(Packet packet) {
        connectionManager.broadcast(packet);
    }

    public void discard() {
        playerManager.reset();
        connectionManager.disconnect();
        connectionManager.clearAllSaveData();
        playerManager = null;
        connectionManager = null;
    }

    public boolean isGaming() {
//        if (Rukkit.getConfig().nonStopMode) {
//            return true;
//        }
        if (currentStep <= 0) {
            isGaming = false;
        } else {
            isGaming = true;
        }
        return isGaming;
    }


    public void changeMapWhileRunning(String mapName, int type) {
        Rukkit.getRoundConfig().mapName = mapName;
        Rukkit.getRoundConfig().mapType = type;
        try {
            connectionManager.broadcast(Packet.gameStart());
            // Set shared control.
            if (Rukkit.getRoundConfig().sharedControl) {
                for (NetworkPlayer p:playerManager.getPlayerArray()) {
                    try {
                        p.isNull();
                        // p.isSharingControl = true;
                    } catch (NullPointerException ignored) {continue;}
                }
            }
            // Reset tick time
            currentStep = 0;
            // Broadcast start packet.
            connectionManager.broadcast(Packet.serverInfo(config));
            for(RoomConnection conn : connectionManager.getConnections()) {
                conn.updateTeamList(false);
            }
        } catch (IOException ignored) {}
    }

    public void notifyGameTask() {
        //hreadLock.notify();
        setPaused(false);
    }

    public int getTickTime() {
        return currentStep;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void addCommand(GameCommand cmd) {
        if (Rukkit.getConfig().useCommandQuere) {
            commandQuere.addLast(cmd);
        } else {
            try {
                broadcast(Packet.gameCommand(this.currentStep, cmd));
            } catch (IOException ignored) {}
        }
    }

    public void summonUnit(String unitName, float x, float y, int player) {
        try {
            broadcast(Packet.gameSummon(getCurrentStep(), unitName, x, y, player));
        } catch (IOException e) {

        }
    }

    public void summonUnit(String unitName, float x, float y) {
        try {
            broadcast(Packet.gameSummon(getCurrentStep(), unitName, x, y));
        } catch (IOException e) {

        }
    }
}
