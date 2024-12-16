// package com.nhnacademy;

// import org.eclipse.paho.client.mqttv3.*;
// import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

// import com.google.gson.Gson;
// import com.google.gson.JsonObject;
// import com.google.gson.JsonParser;

// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;

// import java.util.HashMap;
// import java.util.Map;
// import java.util.concurrent.Executors;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.TimeUnit;

// public class MQTTTopicTransformer {
// private static final Logger logger =
// LogManager.getLogger(MQTTTopicTransformer.class);

// private static final String BROKER = "tcp://192.168.70.203:1883";

// private static final String BROKER2 = "tcp://192.168.71.226:1883";

// private static final String SUB_CLIENT_ID = "TopicTransformerSubscriber";
// private static final String PUB_CLIENT_ID = "TopicTransformerPublisher";

// private static final String INPUT_TOPIC = "data/#";
// private static final String OUTPUT_TOPIC = "samsa/p/#";

// private final MqttClient subscriberClient;
// private final MqttClient publisherClient;
// private final Gson gson = new Gson();
// private final ExecutorService executorService =
// Executors.newFixedThreadPool(2);

// public MQTTTopicTransformer() throws MqttException {
// subscriberClient = new MqttClient(BROKER, SUB_CLIENT_ID, new
// MemoryPersistence());
// publisherClient = new MqttClient(BROKER2, PUB_CLIENT_ID, new
// MemoryPersistence());
// }

// public void start() throws MqttException {
// MqttConnectOptions subOptions = new MqttConnectOptions();
// subOptions.setCleanSession(true);
// subscriberClient.connect(subOptions);

// MqttConnectOptions pubOptions = new MqttConnectOptions();
// pubOptions.setCleanSession(true);
// publisherClient.connect(pubOptions);

// subscriberClient.subscribe(INPUT_TOPIC, (topic, message) -> {
// executorService.submit(() -> processMessage(topic, message));
// });

// logger.info("MQTT 토픽 변환기 시작. 입력 토픽: {}, 출력 토픽: {}", INPUT_TOPIC,
// OUTPUT_TOPIC);
// }

// private void processMessage(String sourceTopic, MqttMessage message) {
// try {
// String payload = new String(message.getPayload());
// logger.info("받은 메시지 - 토픽: {}, 페이로드: {}", sourceTopic, payload);

// String transformedTopic = transformTopic(sourceTopic);

// if (transformedTopic != null) {
// MqttMessage outputMessage = new MqttMessage(payload.getBytes());
// outputMessage.setQos(0);

// publisherClient.publish(transformedTopic, outputMessage);
// logger.info("변환된 메시지 발행 - 토픽: {}, 페이로드: {}", transformedTopic, payload);
// }

// } catch (Exception e) {
// logger.error("메시지 처리 중 오류", e);
// }
// }

// private String transformTopic(String sourceTopic) {
// // 토픽 세그먼트 분리
// String[] segments = sourceTopic.split("/");

// // 최소한 필요한 세그먼트 수 체크
// if (segments.length < 8) {
// logger.warn("토픽 형식이 올바르지 않음: {}", sourceTopic);
// return null;
// }

// // 맵 생성을 위한 세그먼트 파싱
// Map<String, String> topicMap = parseTopicSegments(segments);

// // 변환된 토픽 생성
// StringBuilder transformedTopic = new StringBuilder("samsa/p/");

// // 공간(p) 세그먼트 추가
// transformedTopic.append(topicMap.get("p")).append("/");

// // 선택적 sp 세그먼트 처리
// if (topicMap.containsKey("sp")) {
// transformedTopic.append("sp/").append(topicMap.get("sp")).append("/");
// } else {
// transformedTopic.append("sp/~/");
// }

// // 네임 세그먼트 추가
// transformedTopic.append("n/").append(topicMap.get("n")).append("/");

// // 엔티티 세그먼트 추가
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

// public void stop() {
// try {
// subscriberClient.disconnect();
// publisherClient.disconnect();
// executorService.shutdown();
// executorService.awaitTermination(5, TimeUnit.SECONDS);
// } catch (Exception e) {
// logger.error("셧다운 중 오류", e);
// }
// }

// public static void main(String[] args) {
// try {
// MQTTTopicTransformer transformer = new MQTTTopicTransformer();
// transformer.start();

// Runtime.getRuntime().addShutdownHook(new Thread(() -> {
// transformer.stop();
// logger.info("MQTT 토픽 변환기 종료");
// }));

// Thread.currentThread().join();
// } catch (Exception e) {
// e.printStackTrace();
// }
// }
// }