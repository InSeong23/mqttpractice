package com.nhnacademy;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.paho.client.mqttv3.*;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.write.Point;
import com.influxdb.client.domain.WritePrecision;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MQTTSub {
    private static final Logger logger = LogManager.getLogger(MQTTSub.class);

    private static final String BROKER = "tcp://192.168.70.203:1883";
    private static final String CLIENT_ID = "Seong";
    private static final String TOPIC = "seongseong/#";
    private static final String TOPIC2 = "data/#";

    private static final String URL = "http://192.168.71.221:8086";
    private static final String ORG = "seong";
    private static final String BUCKET = "nhnacademy";
    private static final char[] TOKEN = "7BuWmxpqHzyY7dN6jZiysF8SvdXEdnccAq9Uq23Mr7uL9NGc2tkZKrIkiTmaD4QtXY7NxaJSG8hbqs_kvu1kZQ=="
            .toCharArray();

    private final InfluxDBClient influxDBClient;
    private final WriteApiBlocking writeApiBlocking;

    public MQTTSub() {
        this.influxDBClient = InfluxDBClientFactory.create(URL, TOKEN, ORG, BUCKET);
        this.writeApiBlocking = influxDBClient.getWriteApiBlocking();
    }

    public static void main(String[] args) {
        MQTTSub app = new MQTTSub();
        app.start();
    }

    public void start() {
        try (MqttClient client = new MqttClient(BROKER, CLIENT_ID)) {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    logger.error("Connection lost: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    logger.info("Delivery complete: {}", token.getMessageId());
                }
            });

            logger.info("Connecting to broker...");
            client.connect(options);
            logger.info("Connected!");

            logger.info("Subscribing to topics: {}, {}", TOPIC, TOPIC2);
            client.subscribe(TOPIC);
            client.subscribe(TOPIC2);

            while (true) {
                TimeUnit.SECONDS.sleep(1);
            }
        } catch (Exception e) {
            logger.error("Error in MQTT client", e);
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            logger.info("Received message from topic: {}", topic);
            String payload = new String(message.getPayload());
            logger.info("Payload: {}", payload);

            if (topic.contains("lora")) {
                System.out.println("Lora data received, skipping...");
                return;
            }

            String[] topicSegments = topic.split("/");
            // logger.debug("Topic segments: ");
            // for (int i = 0; i < topicSegments.length; i++) {
            // logger.debug("[{}]: {}", i, topicSegments[i]);
            // }

            JsonObject jsonPayload = parsePayload(payload);
            if (jsonPayload == null)
                return;

            JsonElement valueElement = jsonPayload.get("value");
            if (valueElement == null) {
                logger.error("Invalid message: no 'value' field found.");
                return;
            }

            if (topic.startsWith("seongseong/")) {
                String measurement = "power";
                String field = topicSegments[topicSegments.length - 1];

                Point point = Point.measurement(measurement)
                        .time(Instant.now(), WritePrecision.MS)
                        .addField(field, valueElement.getAsDouble());

                writeApiBlocking.writePoint(point);
                logger.info("Data written to InfluxDB: {}", point);

            } else if (topic.startsWith("data/")) {
                String measurement = "sensor";
                String deviceName = topicSegments.length > 12 ? topicSegments[10] : "unknown_device";
                String field = topicSegments[topicSegments.length - 1];

                Point point = Point.measurement(measurement)
                        .addTag("deviceName", deviceName)
                        .time(Instant.now(), WritePrecision.MS)
                        .addField(field, valueElement.getAsDouble());

                writeApiBlocking.writePoint(point);
                logger.info("Data written to InfluxDB: {}", point);
            } else {
                logger.warn("Unhandled topic: {}", topic);
            }
        } catch (Exception e) {
            logger.error("Error processing message", e);
        }
    }

    private JsonObject parsePayload(String payload) {
        try {
            return JsonParser.parseString(payload).getAsJsonObject();
        } catch (Exception e) {
            logger.error("Invalid JSON payload: {}", payload);
            return null;
        }
    }

    private Point createPoint(String measurement, String tagKey, String tagValue, JsonElement valueElement) {
        Point point = Point.measurement(measurement)
                .addTag(tagKey, tagValue)
                .time(Instant.now(), WritePrecision.MS);

        if (valueElement.isJsonPrimitive()) {
            if (valueElement.getAsJsonPrimitive().isNumber()) {
                point.addField("value", valueElement.getAsDouble());
            } else if (valueElement.getAsJsonPrimitive().isBoolean()) {
                point.addField("value", valueElement.getAsBoolean());
            } else if (valueElement.getAsJsonPrimitive().isString()) {
                point.addField("value", valueElement.getAsString());
            }
        } else if (valueElement.isJsonObject()) {
            point.addField("value", valueElement.toString());
        }
        return point;
    }
}