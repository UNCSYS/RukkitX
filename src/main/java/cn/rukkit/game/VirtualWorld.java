package cn.rukkit.game;

import java.util.ArrayList;
import java.util.List;

import cn.rukkit.game.unit.Unit;

public class VirtualWorld {
    public List<Unit> units = new ArrayList<>();
    //public int unparser = 0;//固定前几个id不计入解析
    public VirtualWorld(){
        //TODO: 根据玩家数量与队伍重新分配utils
        //TODO: 完成自定义utils解析
    }
    public void unitMove(int id,float sx,float sy, float ex, float ey){
        Unit curr = units.get(id);
        if (curr.pixelX==sx&&curr.pixelY==sy) {//应该有一个合理阈值 而不是严格一致
            //TODO: 终止已有的move事件(如果有)
            //TODo: 添加tick线程处理每tick的坐标变更
        }
    }
}
