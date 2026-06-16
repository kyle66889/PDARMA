package com.pda.app.data.api

import com.pda.app.data.api.model.LoginRequest
import com.pda.app.data.api.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    /** POST /api/auth/login — public endpoint, no JWT required. */
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
