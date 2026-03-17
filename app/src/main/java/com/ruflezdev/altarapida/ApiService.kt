package com.ruflezdev.altarapida

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("productos/buscar")
    fun buscarProductos(@Query("q") query: String): Call<List<Producto>>

    @GET("producto/{id}")
    fun buscarProducto(@Path("id") id: String): Call<Producto>

    @POST("producto/actualizar")
    fun actualizarProducto(@Body producto: Producto): Call<String>

    @GET("lineas")
    fun getLineas(): Call<List<Linea>>

    @GET("productos/linea/{linea}")
    fun getProductosPorLinea(@Path("linea") linea: String): Call<List<Producto>>
}