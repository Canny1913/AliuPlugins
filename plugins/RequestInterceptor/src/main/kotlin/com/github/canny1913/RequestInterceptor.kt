package com.github.canny1913

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.discord.utilities.rest.RestAPI
import i0.x
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.lang.reflect.Proxy


@AliucordPlugin(requiresRestart = true)
class RequestInterceptor : Plugin() {
    init {
        manifest.description = "Adds ability to intercept Rest API requests/responses."
    }

    override fun start(context: Context) {
        // WIP
        Utils.showToast("Can't load unfinished plugin.")
        return

        // // API is already initialized by the time core starts, so doing this is totally fine
        //val apiInterface = RestAPI.`access$get_api$p`(RestAPI.api)
        //val handlerInstance = Proxy.getInvocationHandler(apiInterface) as x
        //val okhttpInstance = handlerInstance.d.b as f0.x
        //val interceptors = okhttpInstance.o.toMutableList()
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}
//fun Headers.a.get(name: String): String? {
//    val size = a.size
//    var i = size - 2
//
//    while (i >= 0) {
//        val key = a[i]
//
//        if (key != null && name.length == key.length) {
//            var match = true
//            var j = 0
//            while (j < name.length) {
//                val c1 = name[j]
//                val c2 = key[j]
//                if (c1.uppercaseChar() != c2.uppercaseChar()) {
//                    match = false
//                    break
//                }
//                j++
//            }
//
//            if (match) {
//                return a[i + 1]
//            }
//        }
//
//        i -= 2
//    }
//
//    return null
//}
//fun Request.a.get(name: String): String? {
//    return this.c.get(name)
//}
//fun Response.a.get(name: String): String? {
//    return this.f.get(name)
//}
//fun Response.a.set(name: String, value: String) {
//    this.f.a(name, value)
//}
