package com.ruflezdev.altarapida

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputLayout
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var txtCodigo: EditText
    private lateinit var txtNombre: EditText
    private lateinit var txtPrecio: EditText
    private lateinit var txtExistencia: EditText
    private lateinit var layoutExistencia: TextInputLayout
    private lateinit var cbInventarioReal: CheckBox
    private lateinit var btnBuscar: Button
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnScan: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnIrAConsultar: Button
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView

    private var apiService: ApiService? = null
    private var stockActualBaseDeDatos: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        txtCodigo = findViewById(R.id.txtCodigo)
        txtNombre = findViewById(R.id.txtNombre)
        txtPrecio = findViewById(R.id.txtPrecio)
        txtExistencia = findViewById(R.id.txtExistencia)
        layoutExistencia = findViewById(R.id.layoutExistencia)
        cbInventarioReal = findViewById(R.id.cbInventarioReal)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnScan = findViewById(R.id.btnScan)
        btnSettings = findViewById(R.id.btnSettings)
        btnIrAConsultar = findViewById(R.id.btnIrAConsultar)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)

        setupRetrofit()
        verificarConexion()

        txtCodigo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_NEXT) {
                ejecutarBusquedaGeneral(txtCodigo.text.toString())
                true
            } else {
                false
            }
        }

        txtNombre.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                ejecutarBusquedaGeneral(txtNombre.text.toString())
                true
            } else {
                false
            }
        }

        btnBuscar.setOnClickListener { ejecutarBusquedaGeneral(txtCodigo.text.toString()) }
        btnGuardar.setOnClickListener { guardarProducto() }
        btnLimpiar.setOnClickListener { limpiarCampos() }
        btnScan.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Centra el código de barras en el recuadro")
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
                setCaptureActivity(CustomCaptureActivity::class.java)
                addExtra("SCAN_CAMERA_ID", 0)
            }
            barcodeLauncher.launch(options)
        }

        btnSettings.setOnClickListener { showSettingsDialog() }

        btnIrAConsultar.setOnClickListener {
            startActivity(Intent(this, ConsultaActivity::class.java))
        }
    }

    private fun setupRetrofit() {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val ip = prefs.getString("api_ip", "192.168.1.XX") ?: "192.168.1.XX"
        val baseUrl = "http://$ip:3000/"

        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofit.create(ApiService::class.java)
        } catch (e: Exception) {
            updateStatus(2)
        }
    }

    private fun verificarConexion() {
        updateStatus(1) // Conectando (Amarillo)
        apiService?.buscarProductos("test_ping_connection")
            ?.enqueue(object : Callback<List<Producto>> {
                override fun onResponse(
                    call: Call<List<Producto>>,
                    response: Response<List<Producto>>
                ) {
                    updateStatus(0) // Conectado (Verde)
                }

                override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                    updateStatus(2) // Desconectado (Rojo)
                }
            })
    }

    private fun updateStatus(status: Int) {
        val color = when (status) {
            0 -> R.color.success // Verde
            1 -> R.color.accent  // Amarillo
            else -> R.color.error // Rojo
        }
        val text = when (status) {
            0 -> "Conectado"
            1 -> "Conectando..."
            else -> "Desconectado"
        }

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(ContextCompat.getColor(this, color))
        viewStatusDot.background = drawable
        tvStatusText.text = text
    }

    private val barcodeLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            if (result.contents != null) {
                txtCodigo.setText(result.contents)
                ejecutarBusquedaGeneral(result.contents)
            }
        }

    private fun ejecutarBusquedaGeneral(query: String) {
        if (query.isBlank()) return
        updateStatus(1)

        apiService?.buscarProductos(query)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(
                call: Call<List<Producto>>,
                response: Response<List<Producto>>
            ) {
                updateStatus(0)
                if (response.isSuccessful) {
                    val lista = response.body() ?: emptyList()
                    if (lista.isNotEmpty()) {
                        if (lista.size == 1) cargarProducto(lista[0])
                        else mostrarDialogoSeleccion(lista)
                    } else {
                        buscarPorIdExacto(query)
                    }
                } else {
                    buscarPorIdExacto(query)
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                updateStatus(2)
                buscarPorIdExacto(query)
            }
        })
    }

    private fun buscarPorIdExacto(id: String) {
        apiService?.buscarProducto(id)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                updateStatus(0)
                if (response.isSuccessful && response.body() != null) {
                    cargarProducto(response.body()!!)
                } else {
                    stockActualBaseDeDatos = 0.0
                    mostrarDialogoNuevoProducto(id)
                }
            }

            override fun onFailure(call: Call<Producto>, t: Throwable) {
                updateStatus(2)
                Toast.makeText(this@MainActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun mostrarDialogoSeleccion(productos: List<Producto>) {
        val nombres = productos.map { "${it.Producto} ($${it.PrecioPublico})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Selecciona el producto")
            .setItems(nombres) { _, which ->
                cargarProducto(productos[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun cargarProducto(p: Producto) {
        txtCodigo.setText(p.CodigoBarras)
        txtNombre.setText(p.Producto)
        txtPrecio.setText(p.PrecioPublico.toString())
        stockActualBaseDeDatos = p.Existencias
        txtExistencia.setText("")
        layoutExistencia.helperText = "Stock en sistema: $stockActualBaseDeDatos"
        txtExistencia.requestFocus()
    }

    private fun mostrarDialogoNuevoProducto(codigo: String) {
        AlertDialog.Builder(this)
            .setTitle("No encontrado")
            .setMessage("¿Registrar '$codigo'?")
            .setPositiveButton("Sí") { _, _ ->
                txtNombre.text.clear()
                txtPrecio.text.clear()
                txtExistencia.setText("")
                layoutExistencia.helperText = null
                if (codigo.all { it.isDigit() }) txtCodigo.setText(codigo) else txtNombre.setText(
                    codigo
                )
                txtNombre.requestFocus()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun guardarProducto() {
        val cantidadIngresada = txtExistencia.text.toString().toDoubleOrNull() ?: 0.0
        val stockFinal = if (cbInventarioReal.isChecked) {
            cantidadIngresada
        } else {
            stockActualBaseDeDatos + cantidadIngresada
        }

        val p = Producto(
            txtCodigo.text.toString(),
            txtNombre.text.toString(),
            txtPrecio.text.toString().toDoubleOrNull() ?: 0.0,
            stockFinal
        )

        updateStatus(1)
        apiService?.actualizarProducto(p)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                updateStatus(0)
                if (response.isSuccessful && response.body() == "OK") {
                    Toast.makeText(
                        this@MainActivity,
                        "¡Guardado! Stock: $stockFinal",
                        Toast.LENGTH_LONG
                    ).show()
                    limpiarCampos()
                } else {
                    Toast.makeText(this@MainActivity, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                updateStatus(2)
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun limpiarCampos() {
        txtCodigo.text.clear()
        txtNombre.text.clear()
        txtPrecio.text.clear()
        txtExistencia.text.clear()
        layoutExistencia.helperText = null
        cbInventarioReal.isChecked = false
        stockActualBaseDeDatos = 0.0
        txtCodigo.requestFocus()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val currentIp = prefs.getString("api_ip", "192.168.1.XX")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val txtIp = dialogView.findViewById<EditText>(R.id.txtIp)
        val imgDev = dialogView.findViewById<ImageView>(R.id.imgDeveloper)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvDevEmail)
        
        txtIp.setText(currentIp)
        tvEmail.text = "ruben.dev@example.com" // Cambia por tu correo real

        // Cargar foto de GitHub (Se actualiza automáticamente)
        Glide.with(this)
            .load("https://github.com/ruflezdev.png") // Basado en tu namespace
            .circleCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgDev)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val newIp = txtIp.text.toString().trim()
                if (newIp.isNotEmpty()) {
                    prefs.edit().putString("api_ip", newIp).apply()
                    setupRetrofit()
                    verificarConexion()
                    Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
