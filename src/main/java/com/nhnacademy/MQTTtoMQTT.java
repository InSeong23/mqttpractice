// package com.nhnacademy;

// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;

// public class MQTTtoMQTT {
// private static final Logger logger = LogManager.getLogger(MQTTtoMQTT.class);

// public static void main(String[] args) {
// try {
// MQTTSubscriber subscriber = new MQTTSubscriber();
// subscriber.start();

// MQTTPublisher publisher = new MQTTPublisher();

// Runtime.getRuntime().addShutdownHook(new Thread(() -> {
// subscriber.stop();
// publisher.stop();
// logger.info("MQTT 토픽 변환기 종료");
// }));

// Thread.currentThread().join();
// } catch (Exception e) {
// logger.error("메인 실행 중 오류", e);
// }
// }
// }
