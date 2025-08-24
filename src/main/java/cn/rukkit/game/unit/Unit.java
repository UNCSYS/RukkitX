package cn.rukkit.game.unit;

public class Unit {
    public int id;// 这个是顺序id 每个unit独有一个
    public int unitId;// 这个是组id 例如 所有建造者A的id都是200
    public String name;// Example: 建造者A
    public float pixelX;
    public float pixelY;
    public int index;//player index
    public boolean isMapUnit = false;//区别map原有的单位与玩家游戏中产生的单位
}