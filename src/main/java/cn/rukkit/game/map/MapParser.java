package cn.rukkit.game.map;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jline.utils.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cn.rukkit.game.unit.Unit;
import cn.rukkit.game.unit.UnitTsx;
import cn.rukkit.util.MathUtil;

public class MapParser {
    private MapInfo mapInfo;
    // private static final int FLAG_FLIP_HORIZONTALLY = Integer.MIN_VALUE;
    // private static final int FLAG_FLIP_DIAGONALLY = 0x20000000;
    // private static final int FLAG_FLIP_VERTICALLY = 0x40000000;

    public MapParser(String filePath) {
        this.mapInfo = parseTiledMap(filePath);
        // 解析Units
        parseUnits(this.mapInfo);
    }

    public MapInfo getMapInfo() {
        return mapInfo;
    }

    // ===============================XML获取方法=================================
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

    private float getFloatAttribute(Element element, String attributeName, float defaultValue) {
        String value = getAttribute(element, attributeName, "");
        if (value.isEmpty())
            return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ===============================CORE Parser=================================
    private MapInfo parseTiledMap(String filePath) {
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

            // 解析图块集 <Tilsets>
            NodeList tilesets = root.getElementsByTagName("tileset");
            for (int i = 0; i < tilesets.getLength(); i++) {
                Element tileset = (Element) tilesets.item(i);
                TilesetInfo tilesetInfo = new TilesetInfo();

                tilesetInfo.firstGid = getIntAttribute(tileset, "firstgid", 0);
                tilesetInfo.name = getAttribute(tileset, "name", "未知");
                tilesetInfo.tileWidth = getIntAttribute(tileset, "tilewidth", 0);
                tilesetInfo.tileHeight = getIntAttribute(tileset, "tileheight", 0);
                tilesetInfo.tileCount = getIntAttribute(tileset, "tilecount", 0);
                tilesetInfo.columns = getIntAttribute(tileset, "columns", 0);
                tilesetInfo.source = getAttribute(tileset, "source", "");

                info.tilesets.add(tilesetInfo);
            }

            // 解析图层 <Layer>
            NodeList layers = root.getElementsByTagName("layer");
            for (int i = 0; i < layers.getLength(); i++) {
                Element layer = (Element) layers.item(i);
                LayerInfo layerInfo = new LayerInfo();

                layerInfo.name = getAttribute(layer, "name", "");
                layerInfo.width = getIntAttribute(layer, "width", 0);
                layerInfo.height = getIntAttribute(layer, "height", 0);
                layerInfo.data = extractLayerData(layer, info.width, info.height);
                // int[]tileIds = layerInfo.data;

                info.layers.add(layerInfo);
            }

            // 解析对象组 <ObjectGroup>
            NodeList objectGroups = root.getElementsByTagName("objectgroup");
            for (int i = 0; i < objectGroups.getLength(); i++) {
                Element objectGroup = (Element) objectGroups.item(i);
                String groupName = getAttribute(objectGroup, "name", "objects");

                NodeList objects = objectGroup.getElementsByTagName("object");
                for (int j = 0; j < objects.getLength(); j++) {
                    Element obj = (Element) objects.item(j);
                    ObjectInfo objectInfo = new ObjectInfo();

                    objectInfo.id = getIntAttribute(obj, "id", 0);
                    objectInfo.name = getAttribute(obj, "name", "");
                    objectInfo.type = getAttribute(obj, "type", "");
                    objectInfo.x = getFloatAttribute(obj, "x", 0);
                    objectInfo.y = getFloatAttribute(obj, "y", 0);
                    objectInfo.width = getFloatAttribute(obj, "width", 0);
                    objectInfo.height = getFloatAttribute(obj, "height", 0);
                    objectInfo.properties = extractProperties(obj);

                    info.objects.putIfAbsent(groupName, new ArrayList<>());
                    info.objects.get(groupName).add(objectInfo);
                }
            }

            // 解析原有单位 <Units>
            info.units = parseUnits(info);

            return info;
        } catch (Exception e) {
            System.err.println("解析TMX文件时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 提取图层数据
    private List<Integer> extractLayerData(Element layer, int mapWidth, int mapHeight) throws IOException {
        NodeList dataNodes = layer.getElementsByTagName("data");
        if (dataNodes.getLength() == 0) {
            return new ArrayList<>();
        }

        Element data = (Element) dataNodes.item(0);
        String encoding = getAttribute(data, "encoding", "");
        String compression = getAttribute(data, "compression", "");
        String textContent = data.getTextContent().trim();

        if (textContent.isEmpty()) {
            return new ArrayList<>();
        }

        if ("base64".equals(encoding)) {
            // base64解码
            byte[] decodedData = MathUtil.decodeBase64Custom(textContent);
            // zlib gzip解码
            InputStream layerDataInputStream = null;
            if ("gzip".equals(compression)) {
                layerDataInputStream = new BufferedInputStream(
                        new GZIPInputStream(new ByteArrayInputStream(decodedData), decodedData.length));
            } else if ("zlib".equals(compression)) {
                layerDataInputStream = new BufferedInputStream(
                        new InflaterInputStream(new ByteArrayInputStream(decodedData)));
            } else if (compression == null) {
                layerDataInputStream = new ByteArrayInputStream(decodedData);
            }
            // 核心数据处理
            byte[] arr = new byte[4];
            int[] coreMapData = new int[mapHeight * mapWidth];
            List<Integer> tileIds = new ArrayList<>();
            for (int i = 0; i < mapHeight; i++) {
                for (int j = 0; j < mapWidth; j++) {
                    int read = layerDataInputStream.read(arr);
                    while (read < 4) {
                        int read2 = layerDataInputStream.read(arr, read, 4 - read);
                        if (read2 == -1) {
                            break;
                        }
                        read += read2;
                    }
                    if (read != 4) {
                        Log.error("TMXParser: Premature end of data");
                    }
                    coreMapData[i * mapWidth] = MathUtil.unsignedByteToInt(arr[0])
                            | (MathUtil.unsignedByteToInt(arr[1]) << 8)
                            | (MathUtil.unsignedByteToInt(arr[2]) << 16)
                            | (MathUtil.unsignedByteToInt(arr[3]) << 24);
                    tileIds.add(coreMapData[i * mapWidth]);
                }
            }
            return tileIds;
        } else if ("csv".equals(encoding)) {
            // 处理CSV格式
            String[] tileIdsStr = textContent.replace("\n", "").split(",");
            List<Integer> tileIds = new ArrayList<>();
            for (String idStr : tileIdsStr) {
                String trimmed = idStr.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        tileIds.add(Integer.parseInt(trimmed));
                    } catch (NumberFormatException e) {
                        System.err.println("无法解析图块ID: " + trimmed);
                        tileIds.add(0);
                    }
                }
            }
            return tileIds;
        } else {
            // 处理XML格式（无编码）
            List<Integer> tileIds = new ArrayList<>();
            NodeList tileNodes = data.getElementsByTagName("tile");
            for (int i = 0; i < tileNodes.getLength(); i++) {
                Element tile = (Element) tileNodes.item(i);
                int gid = getIntAttribute(tile, "gid", 0);
                tileIds.add(gid);
            }
            return tileIds;
        }

    }

    // 解析单位(unit)方法
    private List<Unit> parseUnits(MapInfo info) {
        MapParser.LayerInfo unitsLayer = info.getLayerByName("Units");
        List<Unit> units = new ArrayList<>();
        int firstGid = 0;
        // 获取first gid
        for (TilesetInfo info2 : info.tilesets) {
            Log.info("aaaa"+info2.source+"bbbb"+info2.firstGid);
            if (info2.source.equals("units.tsx")||info2.name.equals("units")) {
                firstGid = info2.firstGid;
            }
        }
        if (unitsLayer != null && unitsLayer.data != null) {
            int tileWidth = info.tileWidth;
            int tileHeight = info.tileHeight;

            for (int y = 0; y < info.height; y++) {
                for (int x = 0; x < info.width; x++) {
                    int tileId = unitsLayer.getTileIdAt(x, y);
                    if (tileId != 0) {
                        Unit currUnitInfo = new Unit();
                        float pixelX = x * tileWidth + tileWidth / 2.0f;
                        currUnitInfo.pixelX = pixelX;
                        float pixelY = y * tileHeight + tileHeight / 2.0f;
                        currUnitInfo.pixelY = pixelY;

                        int id = units.size() + 1;
                        currUnitInfo.id = id;
                        currUnitInfo.unitId = tileId - firstGid;// 别问 别动
                        currUnitInfo.isMapUnit = true;
                        currUnitInfo.team=UnitTsx.getTeam(currUnitInfo.unitId);
                        currUnitInfo.name=UnitTsx.getName(currUnitInfo.unitId);
                        units.add(currUnitInfo);
                    }
                }
            }
        } else {
            // 未找到Units层或层数据为空
        }
        return units;
    }

    // 提取属性
    private Map<String, String> extractProperties(Element element) {
        Map<String, String> properties = new HashMap<>();
        NodeList propertyNodes = element.getElementsByTagName("property");

        for (int i = 0; i < propertyNodes.getLength(); i++) {
            Element property = (Element) propertyNodes.item(i);
            String name = getAttribute(property, "name", "");
            String value = getAttribute(property, "value", "");
            if (!name.isEmpty()) {
                properties.put(name, value);
            }
        }

        return properties;
    }

    // =========================== 内部类定义- 结构体 =================================
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