/*
 * Copyright 2025 Micro(MCLDY@outlook.com) and contributors.
 * 
 * 本衍生作品基于 AGPLv3 许可证
 * This derivative work is licensed under AGPLv3
 */

package cn.rukkit.game;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.rukkit.Rukkit;
import cn.rukkit.event.EventHandler;
import cn.rukkit.event.EventListener;
import cn.rukkit.event.action.MoveEvent;
import cn.rukkit.event.action.PingEvent;
import cn.rukkit.game.unit.Unit;
import cn.rukkit.network.core.ServerPacketHandler;

public class VirtualWorld implements EventListener {
    /*
     * 借Event 完成实时同步一切
     */
    private static final Logger log = LoggerFactory.getLogger(VirtualWorld.class);
    public List<Unit> units = new ArrayList<>();
    //public int unparser = 0;//固定前几个id不计入解析

    
    public VirtualWorld(){
        Rukkit.getPluginManager().registerEventListener(this);
        //TODO: 根据玩家数量与队伍重新分配utils
        //TODO: 完成自定义utils解析
    }
    public void unitMove(int id,float sx,float sy, float ex, float ey){
        Unit curr = units.get(id);
        if (curr.pixelX==sx&&curr.pixelY==sy) {//应该有一个合理阈值 而不是严格一致
            //TODO: 终止已有的move事件(如果有)
            //TODO: 添加tick线程处理每tick的坐标变更
        }
    }

    @EventHandler
    public void onMove(MoveEvent event) {
        log.info("player "+event.getPlayer().name+" unit "+event.getActionUnitIds().toString()+" target X"+event.getTargetX()+" Y"+event.getTargetY());
    }
}
