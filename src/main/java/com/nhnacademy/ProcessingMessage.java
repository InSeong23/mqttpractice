// package com.nhnacademy;

// import org.eclipse.paho.client.mqttv3.*;
// import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;

// import java.util.HashMap;
// import java.util.Map;
// import java.util.concurrent.BlockingQueue;
// import java.util.concurrent.Executors;
// import java.util.concurrent.LinkedBlockingQueue;
// import java.util.concurrent.ScheduledExecutorService;
// import java.util.concurrent.TimeUnit;

// public class ProcessingMessage {
// private static final Logger logger =
// LogManager.getLogger(ProcessingMessage.class);

// private static final String BROKER2 = "tcp://192.168.71.226:1883";
// private static final String PUB_CLIENT_ID = "TopicTransformerPublisher";

// private static final ProcessingMessage INSTANCE = new ProcessingMessage();

// private final MqttClient publisherClient;
// private final BlockingQueue<MqttMessage> messageQueue = new
// LinkedBlockingQueue<>();
// private final Map<String, String> transformedMessages = new HashMap<>();

// private ProcessingMessage() {
// try {
// publisherClient = new MqttClient(BROKER2, PUB_CLIENT_ID, new
// MemoryPersistence());
// publisherClient.connect();

// ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
// scheduler.scheduleAtFixedRate(this::publishMessages, 0, 1, TimeUnit.SECONDS);
// } catch (MqttException e) {
// throw new RuntimeException("MQTTPublisher 생성 실패", e);
// }
// }

// public static ProcessingMessage getInstance() {
// return INSTANCE;
// }

// public void processMessage(String sourceTopic, MqttMessage message) {
// try {
// logger.info("받은 메시지 - 토픽: {}, 페이로드: {}", sourceTopic, new
// String(message.getPayload()));
// String transformedTopic = transformTopic(sourceTopic);

// if (transformedTopic != null) {
// messageQueue.put(new MqttMessage(transformedTopic.getBytes()));
// }
// } catch (Exception e) {
// logger.error("메시지 처리 중 오류", e);
// }
// }

// private String transformTopic(String sourceTopic) {
// String[] segments = sourceTopic.split("/");

// if (segments.length < 8) {
// logger.warn("토픽 형식이 올바르지 않음: {}", sourceTopic);
// return null;
// }

// Map<String, String> topicMap = parseTopicSegments(segments);
// StringBuilder transformedTopic = new StringBuilder("samsa/p/");
// transformedTopic.append(topicMap.get("p")).append("/");

// if (topicMap.containsKey("sp")) {
// transformedTopic.append("sp/").append(topicMap.get("sp")).append("/");
// } else {
// transformedTopic.append("sp/~/");
// }

// transformedTopic.append("n/").append(topicMap.get("n")).append("/");
// transformedTopic.append("e/").append(topicMap.get("e"));

// return transformedTopic.toString();
// }

// private Map<String, String> parseTopicSegments(String[] segments) {
// Map<String, String> topicMap = new HashMap<>();

// for (int i = 0; i < segments.length; i++) {
// if (i + 1 < segments.length) {
// switch (segments[i]) {
// case "p":
// case "sp":
// case "n":
// case "e":
// topicMap.put(segments[i], segments[i + 1]);
// break;
// }
// }
// }

// return topicMap;
// }

// void publishMessages() {
// while (!messageQueue.isEmpty()) {
// try {
// MqttMessage message = messageQueue.poll();
// if (message != null) {
// publish("samsa/p/...", new String(message.getPayload()));
// }
// } catch (Exception e) {
// logger.error("메시지 발행 중 오류", e);
// }
// }
// }

// private void publish(String topic, String payload) {
// try {
// MqttMessage outputMessage = new MqttMessage(payload.getBytes());
// outputMessage.setQos(0);
// publisherClient.publish(topic, outputMessage);
// } catch (MqttException e) {
// logger.error("메시지 발행 중 오류", e);
// }
// }

// public void stop() {
// try {
// if (publisherClient.isConnected()) {
// publisherClient.disconnect();
// }
// publisherClient.close();
// } catch (MqttException e) {
// logger.error("MQTT 클라이언트 종료 중 오류", e);
// }
// }
// }
