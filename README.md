# MyBusiness POS 20 - Android Quick Inventory

Esta es una aplicación nativa para Android diseñada para optimizar la gestión de inventarios en **MyBusiness POS 20**. 
El objetivo principal es permitir el **"Alta Rápida"** de productos, emulando la funcionalidad del formulario **Z004** de forma móvil y eficiente.

## 🚀 Características
* **Conexión Remota:** Se comunica con la base de datos de MyBusiness POS a través de una API intermediaria.
* **Alta Rápida:** Registro de productos con la misma agilidad que el proceso Z004.
* **Interfaz Móvil:** Optimizado para dispositivos Android para facilitar el trabajo en almacén o piso de venta.

## 🛠 Arquitectura
Para que esta aplicación funcione, requiere de su contraparte en el servidor:
* **API de Conexión:** [MyBusinessBridge](https://github.com/ruflezdev/MyBusinessBridge)
  * *Esta API actúa como puente seguro entre la app móvil y la base de datos SQL del POS.*

## 📋 Requisitos
* Dispositivo con Android 8.0 o superior.
* Servidor con MyBusiness POS 20 activo.
* API de conexión configurada y accesible vía red local o internet.
