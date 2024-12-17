package com.nhnacademy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.ip.IpParameters;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import com.influxdb.client.domain.WritePrecision;

public class RobustApp {
    private static final Logger logger = LoggerFactory.getLogger(RobustApp.class);

    private static final String MODBUS_HOST = "192.168.70.203";
    private static final int MODBUS_PORT = 502;
    private static final String MQTT_BROKER = "tcp://192.168.70.203:1883";
    private static final String CLIENT_ID = "seongseong";

    private static final String INFLUX_URL = "http://192.168.71.221:8086";
    private static final String INFLUX_ORG = "seong";
    private static final String INFLUX_BUCKET = "nhnacademy";
    private static final char[] INFLUX_TOKEN = "7BuWmxpqHzyY7dN6jZiysF8SvdXEdnccAq9Uq23Mr7uL9NGc2tkZKrIkiTmaD4QtXY7NxaJSG8hbqs_kvu1kZQ=="
            .toCharArray();

    private ModbusMaster modbusmaster;
    private MqttClient mqttClient;
    private InfluxDBClient influxDBClient;
    private WriteApiBlocking writeApiBlocking;

    public static void main(String[] args) {
        RobustApp app = new RobustApp();
        app.start();
    }

    private void start() {
        setupInfluxDB();
        while (true) {
            try {
                setupModbusMaster();
                setupMqttClient();
                collectAndPublishData();
            } catch (Exception e) {
                logger.error("전체 프로세스 오류 발생", e);
                closeConnections();
                waitBeforeRetry();
            }
        }
    }

    private void setupInfluxDB() {
        try {
            this.influxDBClient = InfluxDBClientFactory.create(INFLUX_URL, INFLUX_TOKEN, INFLUX_ORG, INFLUX_BUCKET);
            this.writeApiBlocking = influxDBClient.getWriteApiBlocking();
            logger.info("InfluxDB 연결 성공");
        } catch (Exception e) {
            logger.error("InfluxDB 연결 실패", e);
        }
    }

    private void setupModbusMaster() {
        try {
            IpParameters params = new IpParameters();
            params.setHost(MODBUS_HOST);
            params.setPort(MODBUS_PORT);

            modbusmaster = new ModbusFactory().createTcpMaster(params, true);
            modbusmaster.init();
            logger.info("Modbus 마스터 초기화 성공");
        } catch (Exception e) {
            logger.error("Modbus 마스터 초기화 실패", e);
            throw new RuntimeException("Modbus 연결 실패", e);
        }
    }

    private void setupMqttClient() {
        try {
            mqttClient = new MqttClient(MQTT_BROKER, CLIENT_ID);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);

            mqttClient.connect(options);
            logger.info("MQTT 브로커 연결 성공");
        } catch (MqttException e) {
            logger.error("MQTT 클라이언트 연결 실패", e);
            throw new RuntimeException("MQTT 연결 실패", e);
        }
    }

    private void collectAndPublishData() throws Exception {
        try (
                InputStream addressMapStream = getClass().getClassLoader().getResourceAsStream("address_map.json");
                InputStream detailedInfoStream = getClass().getClassLoader()
                        .getResourceAsStream("detailed_info.json")) {
            if (addressMapStream == null || detailedInfoStream == null) {
                throw new IllegalStateException("리소스 파일을 찾을 수 없습니다.");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode addressMap = objectMapper.readTree(addressMapStream);
            JsonNode detailedInfo = objectMapper.readTree(detailedInfoStream);

            while (true) {
                processModbusData(addressMap, detailedInfo);
                TimeUnit.SECONDS.sleep(10);
            }
        }
    }

    private void processModbusData(JsonNode addressMap, JsonNode detailedInfo) {
        Iterator<String> addressKeys = addressMap.fieldNames();
        while (addressKeys.hasNext()) {
            String addressKey = addressKeys.next();
            int baseAddress = Integer.parseInt(addressKey);
            String locationName = addressMap.get(addressKey).asText();

            Iterator<String> detailedKeys = detailedInfo.fieldNames();
            while (detailedKeys.hasNext()) {
                String detailedKey = detailedKeys.next();
                JsonNode registerInfo = detailedInfo.get(detailedKey);

                int offset = Integer.parseInt(detailedKey);
                int registerAddress = baseAddress + offset;
                String name = registerInfo.get("Name").asText();
                String type = registerInfo.get("Type").asText();
                int size = registerInfo.get("Size").asInt();
                double scale = registerInfo.has("Scale") && !registerInfo.get("Scale").isNull()
                        ? registerInfo.get("Scale").asDouble()
                        : 1;

                try {
                    ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(1, registerAddress, size);
                    ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) modbusmaster.send(request);

                    if (!response.isException()) {
                        short[] values = response.getShortData();
                        double result = convertAndScale(values, type, scale);

                        if (result != 0) {
                            result = Math.abs(result);
                            publishMqttData(locationName, name, result);
                        }
                    } else {
                        logger.error("주소 {} ({}): Modbus 오류 - {}",
                                registerAddress, name, response.getExceptionMessage());
                    }
                } catch (ModbusTransportException e) {
                    logger.error("주소 {} ({}): 통신 오류", registerAddress, name, e);
                }
            }
        }
    }

    private void publishMqttData(String locationName, String name, double result) {
        try {
            String topic = String.format("seongseong/%s/e/%s", locationName, name);
            String payload = String.format("{\"time\":%d,\"value\":%.2f}",
                    System.currentTimeMillis(), result);

            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);

            mqttClient.publish(topic, message);

            // InfluxDB에 데이터 저장
            Point point = Point.measurement("power")
                    .time(Instant.now(), WritePrecision.MS)
                    .addField(name, result);

            writeApiBlocking.writePoint(point);

            logger.info("MQTT 및 InfluxDB 데이터 발행 성공: {}", topic);
        } catch (Exception e) {
            logger.error("데이터 발행 실패", e);
        }
    }

    private double convertAndScale(short[] values, String type, double scale) {
        long rawValue = 0;
        for (short value : values) {
            rawValue = (rawValue << 16) | (value & 0xFFFF);
        }
        switch (type.toUpperCase()) {
            case "UINT16":
                return (values.length == 1 ? (values[0] & 0xFFFF) : rawValue) / scale;
            case "UINT32":
                return rawValue / scale;
            case "INT16":
                return values.length == 1 ? values[0] / scale : rawValue / scale;
            case "INT32":
                return (rawValue > 0x7FFFFFFF ? rawValue - 0x100000000L : rawValue) / scale;
            default:
                logger.error("알 수 없는 데이터 타입: {}", type);
                return 0;
        }
    }

    private void closeConnections() {
        try {
            if (modbusmaster != null) {
                modbusmaster.destroy();
            }
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            if (influxDBClient != null) {
                influxDBClient.close();
            }
        } catch (Exception e) {
            logger.error("연결 종료 중 오류", e);
        }
    }

    private void waitBeforeRetry() {
        try {
            logger.info("5초 후 재연결 시도...");
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}