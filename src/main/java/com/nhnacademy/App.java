package com.nhnacademy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serotonin.modbus4j.ModbusFactory;
import com.serotonin.modbus4j.ModbusMaster;
import com.serotonin.modbus4j.exception.ModbusTransportException;
import com.serotonin.modbus4j.ip.IpParameters;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersRequest;
import com.serotonin.modbus4j.msg.ReadHoldingRegistersResponse;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class App {
    public static void main(String[] args) {
        IpParameters params = new IpParameters();
        params.setHost("192.168.70.203");
        params.setPort(502);

        ModbusMaster master = new ModbusFactory().createTcpMaster(params, false);

        String broker = "tcp://192.168.70.203:1883";
        String clientId = "seongseong";

        while (true) { // 계속 재연결을 시도
            try (
                    InputStream addressMapStream = App.class.getClassLoader().getResourceAsStream("address_map.json");
                    InputStream detailedInfoStream = App.class.getClassLoader()
                            .getResourceAsStream("detailed_info.json");
                    MqttClient mqttClient = new MqttClient(broker, clientId)) {
                if (addressMapStream == null || detailedInfoStream == null) {
                    System.err.println("리소스 파일을 찾을 수 없습니다.");
                    return;
                }

                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode addressMap = objectMapper.readTree(addressMapStream);
                JsonNode detailedInfo = objectMapper.readTree(detailedInfoStream);

                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);

                while (!mqttClient.isConnected()) {
                    try {
                        mqttClient.connect(connOpts);
                        System.out.println("Connected to MQTT broker");
                    } catch (MqttException e) {
                        System.err.println("MQTT 연결 오류, 5초 후 재시도: " + e.getMessage());
                        TimeUnit.SECONDS.sleep(5);
                    }
                }

                master.init();

                while (mqttClient.isConnected()) {
                    Iterator<String> addressKeys = addressMap.fieldNames();
                    while (addressKeys.hasNext()) {
                        String addressKey = addressKeys.next();
                        int baseAddress = Integer.parseInt(addressKey);
                        String locationName = addressMap.get(addressKey).asText();

                        System.out.printf("=== 주소 %d (%s) 데이터 ===%n", baseAddress, locationName);

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
                                ReadHoldingRegistersRequest request = new ReadHoldingRegistersRequest(1,
                                        registerAddress, size);
                                ReadHoldingRegistersResponse response = (ReadHoldingRegistersResponse) master
                                        .send(request);

                                if (response.isException()) {
                                    System.out.printf("주소 %d (%s): Modbus 오류 - %s%n", registerAddress, name,
                                            response.getExceptionMessage());
                                } else {
                                    short[] values = response.getShortData();
                                    double result = convertAndScale(values, type, scale);

                                    if (result != 0) {
                                        if (result < 0) {
                                            result = Math.abs(result);
                                        }

                                        String topic = String.format("seongseong/%s/e/%s", locationName, name);
                                        String payload = String.format("{\"time\":%d,\"value\":%.2f}",
                                                System.currentTimeMillis(), result);

                                        MqttMessage message = new MqttMessage(payload.getBytes());
                                        message.setQos(1);

                                        mqttClient.publish(topic, message);
                                        System.out.printf("Published to %s: %s%n", topic, payload);
                                    }
                                }
                            } catch (ModbusTransportException e) {
                                System.err.printf("주소 %d (%s): 통신 오류 - %s%n", registerAddress, name, e.getMessage());
                            }
                        }
                        System.out.println();
                    }

                    TimeUnit.SECONDS.sleep(10);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                master.destroy();
            }
        }
    }

    private static double convertAndScale(short[] values, String type, double scale) {
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
                System.err.println("알 수 없는 데이터 타입: " + type);
                return 0;
        }
    }
}
