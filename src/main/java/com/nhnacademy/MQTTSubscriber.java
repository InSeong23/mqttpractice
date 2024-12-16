// package com.nhnacademy;

// import org.eclipse.paho.client.mqttv3.*;
// import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;

// public class MQTTSubscriber {
// private static final Logger logger =
// LogManager.getLogger(MQTTSubscriber.class);

// private static final String BROKER = "tcp://192.168.70.203:1883";
// private static final String SUB_CLIENT_ID = "TopicTransformerSubscriber";
// private static final String INPUT_TOPIC = "data/#";

// private final MqttClient subscriberClient;
// private final ProcessingMessage processingMessage;

// public MQTTSubscriber() throws MqttException {
// subscriberClient = new MqttClient(BROKER, SUB_CLIENT_ID, new
// MemoryPersistence());
// processingMessage = ProcessingMessage.getInstance();
// }

// public void start() throws MqttException {
// MqttConnectOptions subOptions = new MqttConnectOptions();
// subOptions.setCleanSession(true);
// subscriberClient.connect(subOptions);

// subscriberClient.subscribe(INPUT_TOPIC, (topic, message) -> {
// processingMessage.processMessage(topic, message);
// });

// logger.info("MQTT 토픽 변환기 시작. 입력 토픽: {}", INPUT_TOPIC);
// }

// public void stop() {
// try {
// subscriberClient.disconnect();
// subscriberClient.close();
// } catch (Exception e) {
// logger.error("셧다운 중 오류", e);
// }
// }
// }
