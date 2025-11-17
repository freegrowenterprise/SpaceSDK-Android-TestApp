package com.growspace.testapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import java.nio.charset.StandardCharsets
import java.util.UUID

class MQTTManager(private val context: Context) {
    companion object {
        private const val TAG = "MQTTManager"
        private const val QOS = 1
    }

    private var mqttClient: Mqtt3AsyncClient? = null
    private var isConnected = false
    private val gson = Gson()

    // MQTT 설정
    var brokerHost: String = "3.38.52.15"
    var brokerPort: Int = 1883
    var username: String = "freegrow"
    var password: String = "gogrow!"
    private val androidId = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID
    ) ?: "unknown"
    var clientId: String = "android-$androidId-${System.currentTimeMillis()}"

    // 토픽 설정
    var coordinateTopic: String = "uwb/coordinate"
    var distanceTopic: String = "uwb/distance"

    // MQTT 연결
    fun connect(
        host: String,
        port: Int,
        clientId: String? = null,
        username: String? = null,
        password: String? = null,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Throwable) -> Unit)? = null
    ) {
        this.brokerHost = host
        this.brokerPort = port

        if (clientId != null) {
            this.clientId = clientId
        }
        if (username != null) {
            this.username = username
        }
        if (password != null) {
            this.password = password
        }

        // HiveMQ MQTT Client 생성
        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier(this.clientId)
            .serverHost(this.brokerHost)
            .serverPort(this.brokerPort)
            .buildAsync()

        // 연결 옵션 설정
        val connectBuilder = com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect.builder()
            .keepAlive(60)
            .cleanSession(false)

        // 사용자 인증 추가
        if (this.username.isNotEmpty()) {
            connectBuilder.simpleAuth()
                .username(this.username)
                .password(this.password.toByteArray(StandardCharsets.UTF_8))
                .applySimpleAuth()
        }

        val connectMessage = connectBuilder.build()

        // 비동기 연결
        mqttClient?.connect(connectMessage)
            ?.whenComplete { connAck, throwable ->
                if (throwable != null) {
                    isConnected = false
                    Log.e(TAG, "❌ Connection failed: ${throwable.message}")
                    onFailure?.invoke(throwable)
                } else {
                    isConnected = true
                    Log.d(TAG, "✅ MQTT Connected to tcp://$host:$port")
                    onSuccess?.invoke()
                }
            }

        // 연결 상태 변화 리스너는 HiveMQ에서 별도로 제공하지 않으므로
        // 필요시 주기적인 ping 체크 또는 publish 성공/실패 콜백으로 확인
    }

    // MQTT 연결 해제
    fun disconnect() {
        try {
            mqttClient?.disconnect()?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "❌ Disconnect error: ${throwable.message}")
                } else {
                    isConnected = false
                    Log.d(TAG, "⚠️ MQTT Disconnected")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Disconnect exception: ${e.message}")
        }
    }

    // 좌표 데이터 전송
    fun publishCoordinate(deviceId: String, x: Double, y: Double, accuracy: Double? = null) {
        if (!isConnected) {
            Log.w(TAG, "MQTT not connected")
            return
        }

        val timestamp = System.currentTimeMillis() / 1000
        val payload = mutableMapOf(
            "deviceId" to deviceId,
            "x" to x,
            "y" to y,
            "timestamp" to timestamp
        )

        // accuracy가 있으면 추가
        accuracy?.let {
            payload["accuracy"] = it
        }

        val jsonString = gson.toJson(payload)
        publishMessage(coordinateTopic, jsonString)
    }

    // 거리 데이터 전송 (개별 앵커)
    fun publishDistance(deviceId: String, anchorId: String, distance: Float) {
        if (!isConnected) {
            Log.w(TAG, "MQTT not connected")
            return
        }

        val timestamp = System.currentTimeMillis() / 1000
        val payload = mapOf(
            "deviceId" to deviceId,
            "anchorId" to anchorId,
            "distance" to distance,
            "timestamp" to timestamp
        )

        val jsonString = gson.toJson(payload)
        publishMessage(distanceTopic, jsonString)
    }

    // 메시지 전송 헬퍼 함수
    private fun publishMessage(topic: String, message: String) {
        try {
            val publishMessage = com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish.builder()
                .topic(topic)
                .payload(message.toByteArray(StandardCharsets.UTF_8))
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .retain(false)
                .build()

            mqttClient?.publish(publishMessage)?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "❌ Publish error: ${throwable.message}")
                } else {
                    Log.d(TAG, "📨 Message delivered to $topic")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Publish exception: ${e.message}")
        }
    }

    // 연결 상태 확인
    fun isConnected(): Boolean = isConnected

    // 정리
    fun cleanup() {
        disconnect()
        mqttClient = null
    }
}
