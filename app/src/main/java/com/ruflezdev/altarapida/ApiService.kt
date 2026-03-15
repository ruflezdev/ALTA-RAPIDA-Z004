package com.ruflezdev.altarapida

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    // Cambiamos a List<Producto> para soportar múltiples coincidencias (búsqueda por nombre)
    @GET("productos/buscar")
    fun buscarProductos(@Query("q") query: String): Call<List<Producto>>

    // Mantenemos la búsqueda por ID exacto si es necesaria, pero la búsqueda general es más flexible
    @GET("producto/{id}")
    fun buscarProducto(@Path("id") id: String): Call<Producto>

    @POST("producto/actualizar")
    fun actualizarProducto(@Body producto: Producto): Call<String>
}