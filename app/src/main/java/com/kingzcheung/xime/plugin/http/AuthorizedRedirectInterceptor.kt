package com.kingzcheung.xime.plugin.http

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Application interceptor: authorize every hop before DNS, connection or request bytes. */
internal class AuthorizedRedirectInterceptor(
    private val rejection: (String) -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var redirects = 0
        while (true) {
            rejection(request.url.toString())?.let { throw IOException(it) }
            val response = chain.proceed(request)
            if (response.code !in setOf(300, 301, 302, 303, 307, 308)) return response
            val target = response.header("Location")?.let { request.url.resolve(it) } ?: return response
            response.close()
            if (++redirects > 10) throw IOException("重定向次数过多")
            if (request.url.isHttps && !target.isHttps) throw IOException("拒绝重定向到未加密连接")
            val builder = request.newBuilder().url(target)
            if (target.host != request.url.host || target.port != request.url.port) {
                builder.removeHeader("Authorization").removeHeader("Proxy-Authorization")
                    .removeHeader("Cookie").removeHeader("Host")
            }
            if (response.code !in setOf(307, 308) && request.method !in setOf("GET", "HEAD")) {
                builder.method("GET", null).removeHeader("Content-Type")
                    .removeHeader("Content-Length").removeHeader("Transfer-Encoding")
            }
            request = builder.build()
        }
    }
}
