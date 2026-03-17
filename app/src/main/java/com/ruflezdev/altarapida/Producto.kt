package com.ruflezdev.altarapida

data class Producto(
    val CodigoBarras: String,
    val Producto: String,
    val PrecioPublico: Double,
    val Existencias: Double,
    val Linea: String? = "SYS",
    val DescripLinea: String? = null,
    val Unidad: String? = "PZA",
    val Granel: Int? = 0,
    val Speso: Int? = 0
)