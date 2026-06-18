package com.pda.app.data.api

import com.pda.app.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** 会话存在时为请求附带 Authorization: Bearer <token>；登录等公开请求原样放行。 */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionManager.currentToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)
        // 已登录请求收到 401 → token 过期，触发会话过期事件（登录失败时 token 为 null，不会误触）。
        if (token != null && response.code == 401) {
            sessionManager.expire()
        }
        return response
    }
}
