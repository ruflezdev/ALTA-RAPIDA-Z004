package com.ruflezdev.altarapida

data class ProductoConteo(
    val producto: Producto,
    var cantidadFisica: Double = 0.0,
    var motivoDiferencia: String? = null,
    var productoRelacionado: String? = null
)