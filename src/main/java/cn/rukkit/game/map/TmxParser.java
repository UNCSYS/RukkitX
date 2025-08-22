package cn.rukkit.game.map;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cn.rukkit.game.map.MapParser.LayerInfo;
import cn.rukkit.game.map.MapParser.ObjectInfo;
import cn.rukkit.game.map.MapParser.TilesetInfo;
import cn.rukkit.game.unit.Unit;


public class TmxParser {
    private void parseTiledMap(String filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();
            MapInfo info = new MapInfo();

            // 提取地图基本信息
            info.width = getIntAttribute(root, "width", 0);
            info.height = getIntAttribute(root, "height", 0);
            info.tileWidth = getIntAttribute(root, "tilewidth", 0);
            info.tileHeight = getIntAttribute(root, "tileheight", 0);

        } catch (Exception e) {
            System.err.println("解析TMX文件时出错: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
    }
    //================================= XML解析用辅助方法 =================================
    private String getAttribute(Element element, String attributeName, String defaultValue) {
        if (element.hasAttribute(attributeName)) {
            String value = element.getAttribute(attributeName);
            return value.isEmpty() ? defaultValue : value;
        }
        return defaultValue;
    }

    private int getIntAttribute(Element element, String attributeName, int defaultValue) {
        String value = getAttribute(element, attributeName, "");
        if (value.isEmpty())
            return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    //================================= 结构体 =================================
    public static class MapInfo {
        public int width;
        public int height;
        public int tileWidth;
        public int tileHeight;
        public List<TilesetInfo> tilesets = new ArrayList<>();
        public List<LayerInfo> layers = new ArrayList<>();
        public List<Unit> units = new ArrayList<>();
        public Map<String, List<ObjectInfo>> objects = new HashMap<>();

        // 辅助方法
        public LayerInfo getLayerByName(String name) {
            for (LayerInfo layer : layers) {
                if (layer.name.equals(name)) {
                    return layer;
                }
            }
            return null;
        }

        public List<ObjectInfo> getObjectsByType(String type) {
            List<ObjectInfo> result = new ArrayList<>();
            for (List<ObjectInfo> objectList : objects.values()) {
                for (ObjectInfo obj : objectList) {
                    if (obj.type.equals(type)) {
                        result.add(obj);
                    }
                }
            }
            return result;
        }
    }

    public static class TilesetInfo {
        public int firstGid;
        public String name;
        public int tileWidth;
        public int tileHeight;
        public int tileCount;
        public int columns;
        public String source;
    }

    public static class LayerInfo {
        public String name;
        public int width;
        public int height;
        public List<Integer> data;

        public int getTileIdAt(int x, int y) {
            if (data == null || x < 0 || x >= width || y < 0 || y >= height) {
                return 0;
            }
            int index = y * width + x;
            if (index < data.size()) {
                return data.get(index);
            }
            return 0;
        }
    }

    public static class ObjectInfo {
        public int id;
        public String name;
        public String type;
        public float x;
        public float y;
        public float width;
        public float height;
        public Map<String, String> properties;

        public String getProperty(String key, String defaultValue) {
            return properties != null ? properties.getOrDefault(key, defaultValue) : defaultValue;
        }
    }

}