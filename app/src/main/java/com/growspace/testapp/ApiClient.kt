//package com.growspace.testapp
//
//import com.google.gson.annotations.SerializedName
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Body
//import retrofit2.http.Headers
//import retrofit2.http.POST
//
//public data class SpaceLocation(
//    @SerializedName("zoneName") val zoneName: String,
//    @SerializedName("locationX") val locationX: Long,
//    @SerializedName("locationY") val locationY: Long)
//
//data class BeaconRequest(
//    @SerializedName("beaconUuid") val beaconUuid: String
//)
//
//interface ApiService {
//    @Headers("Content-Type: application/json")
//    @POST("grow-space/indoor-location/beacon/my")
//    suspend fun sendBeaconData(
//        @Body request: BeaconRequest
//    ): SpaceLocation
//}
//
//object ApiClient {
//    private const val BASE_URL = "https://v2.api-freegrow-test.com/"
//
//    private val retrofit: Retrofit = Retrofit.Builder()
//        .baseUrl(BASE_URL)
//        .addConverterFactory(GsonConverterFactory.create())
//        .build()
//
//    val service: ApiService = retrofit.create(ApiService::class.java)
//}


package com.growspace.testapp

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ✅ 데이터 클래스 정의
data class SpaceLocation(
    @SerializedName("zoneName") val zoneName: String,
    @SerializedName("locationX") val locationX: Long,
    @SerializedName("locationY") val locationY: Long
)

data class BeaconRequest(
    @SerializedName("beaconUuid") val beaconUuid: String
)

object ApiClient {
    private const val BASE_URL = "https://v2.api-freegrow-test.com/"
    private const val API_KEY = "553f1709-a245-404f-a02a-d3bc4861be43"
    private val gson = Gson()

    // ✅ API 요청 함수
    fun sendBeaconData(beaconUuid: String, callback: (SpaceLocation?) -> Unit) {
        Thread {
            try {
                val url = URL(BASE_URL + "grow-space/indoor-location/beacon/my")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("User-Agent", "Custom-Kotlin-Client")
                connection.setRequestProperty("API-Key", API_KEY)

                // ✅ JSON 변환 및 요청 전송
                val jsonRequest = gson.toJson(BeaconRequest(beaconUuid))
                connection.outputStream.use { os ->
                    os.write(jsonRequest.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                // ✅ 응답 처리
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val responseText = reader.readText()
                    reader.close()

                    val spaceLocation = gson.fromJson(responseText, SpaceLocation::class.java)
                    callback(spaceLocation)
                } else {
                    println("❌ API 요청 실패: HTTP $responseCode")
                    callback(null)
                }
            } catch (e: Exception) {
                println("❌ API 요청 중 오류 발생: ${e.localizedMessage}")
                callback(null)
            }
        }.start()
    }
}