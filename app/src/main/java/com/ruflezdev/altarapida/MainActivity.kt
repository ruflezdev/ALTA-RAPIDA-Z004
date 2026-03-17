package com.ruflezdev.altarapida

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
    private lateinit var txtLinea: AutoCompleteTextView
    private lateinit var txtUnidad: AutoCompleteTextView
    private lateinit var cbGranel: CheckBox
    private lateinit var cbSpeso: CheckBox
    private lateinit var btnBuscar: ImageButton
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var btnScan: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnIrAConsultar: ImageButton
    private lateinit var btnIrAPedido: ImageButton
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView

    private var apiService: ApiService? = null
    private var listaLineas: MutableList<Linea> = mutableListOf()
    private val ADD_NEW_LINE_TEXT = "+ Crear nueva línea..."
    private var productoCargado: Producto? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        txtCodigo = findViewById(R.id.txtCodigo)
        txtNombre = findViewById(R.id.txtNombre)
        txtPrecio = findViewById(R.id.txtPrecio)
        txtLinea = findViewById(R.id.txtLinea)
        txtUnidad = findViewById(R.id.txtUnidad)
        cbGranel = findViewById(R.id.cbGranel)
        cbSpeso = findViewById(R.id.cbSpeso)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnGuardar = findViewById(R.id.btnGuardar)
        btnLimpiar = findViewById(R.id.btnLimpiar)
        btnScan = findViewById(R.id.btnScan)
        btnSettings = findViewById(R.id.btnSettings)
        btnIrAConsultar = findViewById(R.id.btnIrAConsultar)
        btnIrAPedido = findViewById(R.id.btnIrAPedido)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)

        setupRetrofit()
        verificarConexion()
        setupUnidadDropdown()
        cargarLineas()

        // 1. Enter en Código -> Buscar
        txtCodigo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                ejecutarBusquedaGeneral(txtCodigo.text.toString())
                true
            } else false
        }

        // 2. Enter en Nombre -> Buscar
        txtNombre.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                ejecutarBusquedaGeneral(txtNombre.text.toString())
                true
            } else false
        }

        // 3. Enter en Precio -> Guardar (Rápido)
        txtPrecio.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                guardarProducto()
                true
            } else false
        }

        btnBuscar.setOnClickListener { ejecutarBusquedaGeneral(txtCodigo.text.toString()) }
        btnGuardar.setOnClickListener { guardarProducto() }
        btnLimpiar.setOnClickListener { limpiarCampos() }
        btnScan.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Escaneando...")
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
                setCaptureActivity(CustomCaptureActivity::class.java)
            }
            barcodeLauncher.launch(options)
        }

        btnSettings.setOnClickListener { showSettingsDialog() }
        btnIrAConsultar.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ConsultaActivity::class.java
                )
            )
        }
        btnIrAPedido.setOnClickListener { startActivity(Intent(this, PedidoActivity::class.java)) }

        txtLinea.setOnItemClickListener { parent, _, position, _ ->
            val seleccionado = parent.getItemAtPosition(position)
            if (seleccionado is String && seleccionado == ADD_NEW_LINE_TEXT) {
                txtLinea.setText("")
                mostrarDialogoNuevaLinea()
            } else if (seleccionado is Linea) {
                txtLinea.setText(seleccionado.Descrip, false)
            }
        }
    }

    private fun setupUnidadDropdown() {
        val unidades = arrayOf("PZA", "KG", "GRMS", "CAJA", "PACK", "LITRO", "ML", "SYS")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, unidades)
        txtUnidad.setAdapter(adapter)
        txtUnidad.setText("PZA", false)
    }

    private fun cargarLineas() {
        apiService?.getLineas()?.enqueue(object : Callback<List<Linea>> {
            override fun onResponse(call: Call<List<Linea>>, response: Response<List<Linea>>) {
                if (response.isSuccessful) {
                    val rawList = response.body() ?: emptyList()
                    listaLineas = rawList.sortedBy { it.Descrip.lowercase() }.toMutableList()
                    actualizarAdapterLinea()
                }
            }

            override fun onFailure(call: Call<List<Linea>>, t: Throwable) {}
        })
    }

    private fun mostrarDialogoNuevaLinea() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_nueva_linea, null)
        val inputCodigo = view.findViewById<EditText>(R.id.txtNuevoCodigoLinea)
        val inputDescrip = view.findViewById<EditText>(R.id.txtNuevaDescripLinea)

        AlertDialog.Builder(this)
            .setTitle("Agregar Nueva Línea")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val cod = inputCodigo.text.toString().trim().uppercase()
                val desc = inputDescrip.text.toString().trim()
                if (cod.isNotEmpty() && desc.isNotEmpty()) {
                    val nueva = Linea(cod, desc)
                    if (!listaLineas.any { it.Linea == cod }) {
                        listaLineas.add(nueva)
                        listaLineas.sortBy { it.Descrip.lowercase() }
                        actualizarAdapterLinea()
                    }
                    txtLinea.setText(desc, false)
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> txtLinea.setText("SYS", false) }
            .show()
    }

    private fun actualizarAdapterLinea() {
        val adapter =
            LineaAdapter(this@MainActivity, R.layout.item_linea_dropdown, listaLineas, true)
        txtLinea.setAdapter(adapter)
    }

    private fun ejecutarBusquedaGeneral(query: String) {
        if (query.isBlank()) return
        updateStatus(1)

        apiService?.buscarProducto(query)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                if (response.isSuccessful && response.body() != null) {
                    updateStatus(0)
                    cargarProducto(response.body()!!)
                    txtPrecio.requestFocus() // Salto automático al precio
                } else {
                    ejecutarBusquedaLista(query)
                }
            }

            override fun onFailure(call: Call<Producto>, t: Throwable) {
                ejecutarBusquedaLista(query)
            }
        })
    }

    private fun ejecutarBusquedaLista(query: String) {
        apiService?.buscarProductos(query)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(
                call: Call<List<Producto>>,
                response: Response<List<Producto>>
            ) {
                updateStatus(0)
                if (response.isSuccessful) {
                    val lista = response.body() ?: emptyList()
                    if (lista.isNotEmpty()) {
                        if (lista.size == 1) {
                            cargarProducto(lista[0])
                            txtPrecio.requestFocus()
                        } else {
                            mostrarDialogoSeleccion(lista)
                        }
                    } else {
                        mostrarDialogoNuevoProducto(query)
                    }
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                updateStatus(2)
            }
        })
    }

    private fun mostrarDialogoSeleccion(productos: List<Producto>) {
        val nombres = productos.map { "${it.Producto} ($${it.PrecioPublico})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Selecciona")
            .setItems(nombres) { _, which ->
                cargarProducto(productos[which])
                txtPrecio.requestFocus()
            }
            .show()
    }

    private fun cargarProducto(p: Producto) {
        productoCargado = p
        txtCodigo.setText(p.CodigoBarras)
        txtNombre.setText(p.Producto)
        txtPrecio.setText(p.PrecioPublico.toString())

        val lineaEncontrada = listaLineas.find { it.Linea == p.Linea }
        txtLinea.setText(lineaEncontrada?.Descrip ?: (p.Linea ?: "SYS"), false)
        txtUnidad.setText(p.Unidad ?: "PZA", false)
        cbGranel.isChecked = p.Granel == 1
        cbSpeso.isChecked = p.Speso == 1
    }

    private fun mostrarDialogoNuevoProducto(codigo: String) {
        AlertDialog.Builder(this)
            .setTitle("No encontrado")
            .setMessage("¿Registrar nuevo?")
            .setPositiveButton("Sí") { _, _ ->
                limpiarCampos()
                if (codigo.all { it.isDigit() }) txtCodigo.setText(codigo) else txtNombre.setText(
                    codigo
                )
                txtNombre.requestFocus()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun guardarProducto() {
        val p = Producto(
            CodigoBarras = txtCodigo.text.toString(),
            Producto = txtNombre.text.toString(),
            PrecioPublico = txtPrecio.text.toString().toDoubleOrNull() ?: 0.0,
            Existencias = productoCargado?.Existencias ?: 0.0, // Mantenemos el stock intacto aquí
            Linea = listaLineas.find { it.Descrip == txtLinea.text.toString() }?.Linea ?: "SYS",
            Unidad = txtUnidad.text.toString().ifBlank { "PZA" },
            Granel = if (cbGranel.isChecked) 1 else 0,
            Speso = if (cbSpeso.isChecked) 1 else 0
        )

        updateStatus(1)
        apiService?.actualizarProducto(p)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                updateStatus(0)
                if (response.isSuccessful && response.body() == "OK") {
                    Toast.makeText(this@MainActivity, "Guardado con éxito", Toast.LENGTH_SHORT)
                        .show()
                    limpiarCampos()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                updateStatus(2)
            }
        })
    }

    private fun limpiarCampos() {
        productoCargado = null
        txtCodigo.text.clear()
        txtNombre.text.clear()
        txtPrecio.text.clear()
        txtLinea.setText("SYS", false)
        txtUnidad.setText("PZA", false)
        cbGranel.isChecked = false
        cbSpeso.isChecked = false
        txtCodigo.requestFocus()
    }

    private fun setupRetrofit() {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val ip = prefs.getString("api_ip", "192.168.1.XX") ?: "192.168.1.XX"
        val baseUrl = "http://$ip:3000/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(ApiService::class.java)
    }

    private fun verificarConexion() {
        updateStatus(1)
        apiService?.getLineas()?.enqueue(object : Callback<List<Linea>> {
            override fun onResponse(call: Call<List<Linea>>, response: Response<List<Linea>>) {
                updateStatus(0)
            }

            override fun onFailure(call: Call<List<Linea>>, t: Throwable) {
                updateStatus(2)
            }
        })
    }

    private fun updateStatus(status: Int) {
        val color = ContextCompat.getColor(
            this, when (status) {
                0 -> R.color.success
                1 -> R.color.warning
                else -> R.color.error
            }
        )
        val text = when (status) {
            0 -> "Conectado"
            1 -> "Conectando..."
            else -> "Desconectado"
        }
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
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

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val txtIp = dialogView.findViewById<EditText>(R.id.txtIp)
        val imgDev = dialogView.findViewById<ImageView>(R.id.imgDeveloper)
        txtIp.setText(prefs.getString("api_ip", ""))
        Glide.with(this).load("https://github.com/ruflezdev.png").circleCrop().into(imgDev)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putString("api_ip", txtIp.text.toString().trim()).apply()
                setupRetrofit()
                verificarConexion()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}