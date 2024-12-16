// package com.nhnacademy;

// public interface Node {
// // 노드 생명주기 관리
// void start(); // 노드 시작

// void stop(); // 노드 중지

// // 메시지 처리
// void onMessage(Message message); // 메시지 수신 및 처리

// void emit(Message message); // 메시지 전송

// // 포트 관리
// void addInputPort(Port port); // 입력 포트 추가

// void addOutputPort(Port port); // 출력 포트 추가

// void removeInputPort(Port port); // 입력 포트 제거

// void removeOutputPort(Port port); // 출력 포트 제거

// // 노드 상태 관리
// NodeStatus getStatus(); // 현재 노드 상태 조회

// String getId(); // 노드 식별자 조회

// void setId(String id); // 노드 식별자 설정

// // 에러 처리
// void handleError(Throwable error); // 에러 처리
// }

// // 노드 상태 열거형
// public enum NodeStatus {
// CREATED, // 생성됨
// RUNNING, // 실행 중
// STOPPED, // 중지됨
// ERROR // 에러 상태
// }

// // 메시지 인터페이스
// public interface Message {
// String getId(); // 메시지 식별자

// Object getPayload(); // 메시지 내용

// Map<String, Object> getMetadata(); // 메타데이터
// }

// // 포트 인터페이스
// public interface Port {
// String getId(); // 포트 식별자

// Node getNode(); // 포트가 속한 노드

// void connect(Port port); // 다른 포트와 연결

// void disconnect(); // 연결 해제

// boolean isConnected(); // 연결 상태 확인
// }