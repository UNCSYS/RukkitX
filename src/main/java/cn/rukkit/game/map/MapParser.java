package cn.rukkit.game.map;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

import cn.rukkit.game.unit.Unit;

public class MapParser {
    private MapInfo mapInfo;

    public MapParser(String filePath) {
        this.mapInfo = parseTiledMap(filePath);
        // 解析Units
        parseUnits();
    }

    public MapInfo getMapInfo() {
        return mapInfo;
    }

    // 安全的属性获取方法
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

    // 主解析方法
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

            // 解析图块集
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

            // 解析图层
            NodeList layers = root.getElementsByTagName("layer");
            for (int i = 0; i < layers.getLength(); i++) {
                Element layer = (Element) layers.item(i);
                LayerInfo layerInfo = new LayerInfo();

                layerInfo.name = getAttribute(layer, "name", "");
                layerInfo.width = getIntAttribute(layer, "width", 0);
                layerInfo.height = getIntAttribute(layer, "height", 0);
                layerInfo.data = extractLayerData(layer, info.width, info.height);

                info.layers.add(layerInfo);
            }

            // 解析对象组
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

            return info;
        } catch (Exception e) {
            System.err.println("解析TMX文件时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 解析单位(unit)方法
    private void parseUnits(){
        MapParser.LayerInfo unitsLayer = mapInfo.getLayerByName("Units");
        if (unitsLayer != null && unitsLayer.data != null) {
            int tileWidth = mapInfo.tileWidth;
            int tileHeight = mapInfo.tileHeight;

            for (int y = 0; y < mapInfo.height; y++) {
                for (int x = 0; x < mapInfo.width; x++) {
                    int tileId = unitsLayer.getTileIdAt(x, y);
                    if (tileId != 0) {
                        Unit currUnitInfo =new Unit();
                        float pixelX = x * tileWidth + tileWidth / 2.0f;
                        currUnitInfo.pixelX=pixelX;
                        float pixelY = y * tileHeight + tileHeight / 2.0f;
                        currUnitInfo.pixelY=pixelY;


                        // 查找图块集来源
                        String tilesetName = "未知";// 根据tileId获取名字 要一个表
                        currUnitInfo.name=tilesetName;

                        int id = mapInfo.units.size()+1;
                        currUnitInfo.id=id;
                        mapInfo.units.add(currUnitInfo);
                    }
                }
            }
        } else {
            //System.out.println("    未找到Units层或层数据为空");
        }
    }
    // 提取图层数据
    private List<Integer> extractLayerData(Element layer, int mapWidth, int mapHeight) {
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

        try {
            if ("base64".equals(encoding)) {
                byte[] decodedData = Base64.getDecoder().decode(textContent);

                if ("gzip".equals(compression)) {
                    decodedData = decompressGZIP(decodedData);
                } else if ("zlib".equals(compression)) {
                    decodedData = decompressZlib(decodedData);
                }

                // 将字节数据转换为图块ID列表
                List<Integer> tileIds = new ArrayList<>();
                for (int i = 0; i < decodedData.length; i += 4) {
                    if (i + 4 <= decodedData.length) {
                        int tileId = ((decodedData[i] & 0xFF) |
                                ((decodedData[i + 1] & 0xFF) << 8) |
                                ((decodedData[i + 2] & 0xFF) << 16) |
                                ((decodedData[i + 3] & 0xFF) << 24));
                        tileIds.add(tileId);
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
        } catch (Exception e) {
            System.err.println("解析层数据时出错: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // GZIP解压缩
    private byte[] decompressGZIP(byte[] compressedData) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
        GZIPInputStream gis = new GZIPInputStream(bis);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = gis.read(buffer)) > 0) {
            bos.write(buffer, 0, len);
        }

        gis.close();
        bos.close();
        return bos.toByteArray();
    }

    // Zlib解压缩
    private byte[] decompressZlib(byte[] compressedData) {
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(compressedData);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedData.length);
            byte[] buffer = new byte[1024];

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            inflater.end();
            return outputStream.toByteArray();
        } catch (DataFormatException | IOException e) {
            System.err.println("Zlib解压缩错误: " + e.getMessage());
            return new byte[0];
        }
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

    // 内部类定义
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

    /*
    public static void main(String[] args) {
        MapParser parser = new MapParser("/media/micro/Work_Space/Rukkit-master/build/outputs/data/你的地图文件.tmx");
        MapParser.MapInfo mapInfo = parser.getMapInfo();

        if (mapInfo != null) {
            // 获取地图基本信息
            System.out.println("地图尺寸: " + mapInfo.width + "x" + mapInfo.height);
            System.out.println("图块尺寸: " + mapInfo.tileWidth + "x" + mapInfo.tileHeight);

            // 获取图层信息
            for (MapParser.LayerInfo layer : mapInfo.layers) {
                System.out.println("图层: " + layer.name + ", 尺寸: " + layer.width + "x" + layer.height);
            }

            // 获取对象信息
            for (String groupName : mapInfo.objects.keySet()) {
                System.out.println("对象组: " + groupName + ", 对象数量: " + mapInfo.objects.get(groupName).size());
            }
            for (UnitInfo cInfo: mapInfo.units){
                System.out.println("ID "+cInfo.id+" Name"+cInfo.name+" x"+cInfo.pixelX+" y"+cInfo.pixelY);
            }
        }
    } */
}