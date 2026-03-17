package com.ruflezdev.altarapida

data class Linea(
    val Linea: String, // El código de 16 caracteres
    val Descrip: String // La descripción más completa
) {
    override fun toString(): String {
        return Descrip // Para que el Spinner muestre la descripción
    }
}