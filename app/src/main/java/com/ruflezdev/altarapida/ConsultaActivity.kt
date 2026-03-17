package com.ruflezdev.altarapida

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class ConsultaActivity : AppCompatActivity() {

    private lateinit var layoutSeleccionInicial: LinearLayout
    private lateinit var layoutTrabajoLinea: LinearLayout
    private lateinit var spinnerLineaInicial: AutoCompleteTextView
    private lateinit var txtBusquedaIndividual: EditText
    private lateinit var txtBusqueda: EditText
    private lateinit var btnScanConsulta: ImageButton
    private lateinit var rvResultados: RecyclerView
    private lateinit var tvLineaActiva: TextView
    private lateinit var btnFinalizarReporte: View
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView

    private var apiService: ApiService? = null
    private var productosAuditados: MutableList<ProductoConteo> = mutableListOf()
    private var adapter: ProductConteoAdapter? = null
    private var lineaSeleccionada: Linea? = null
    private var listaLineas: List<Linea> = emptyList()
    private val productosContadosSet = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consulta)

        layoutSeleccionInicial = findViewById(R.id.layoutSeleccionInicial)
        layoutTrabajoLinea = findViewById(R.id.layoutTrabajoLinea)
        spinnerLineaInicial = findViewById(R.id.spinnerLineaInicial)
        txtBusquedaIndividual = findViewById(R.id.txtBusquedaIndividual)
        txtBusqueda = findViewById(R.id.txtBusqueda)
        btnScanConsulta = findViewById(R.id.btnScanConsulta)
        rvResultados = findViewById(R.id.rvResultados)
        tvLineaActiva = findViewById(R.id.tvLineaActiva)
        btnFinalizarReporte = findViewById(R.id.btnFinalizarReporte)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)

        rvResultados.layoutManager = LinearLayoutManager(this)
        adapter = ProductConteoAdapter(
            productosAuditados,
            onItemClick = { mostrarDialogoConteo(it) },
            onItemLongClick = { mostrarMenuProductoAuditado(it) })
        rvResultados.adapter = adapter

        setupRetrofit()
        verificarConexion()
        cargarLineas()

        txtBusquedaIndividual.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscarIndividualUrgente(txtBusquedaIndividual.text.toString())
                true
            } else false
        }

        spinnerLineaInicial.setOnItemClickListener { parent, _, position, _ ->
            iniciarAuditoriaPorLinea(parent.getItemAtPosition(position) as Linea)
        }

        txtBusqueda.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscarEnLista(txtBusqueda.text.toString())
                true
            } else false
        }

        btnScanConsulta.setOnClickListener {
            barcodeLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Escaneando...")
                setCaptureActivity(CustomCaptureActivity::class.java)
            })
        }

        btnFinalizarReporte.setOnClickListener { mostrarConfirmacionFinalizar() }
    }

    private fun mostrarDialogoConteo(item: ProductoConteo, esIndividual: Boolean = false) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(item.producto.Producto)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val tvInfo = TextView(this).apply {
            text = "Sistema: ${item.producto.Existencias} | Conteo actual: ${item.cantidadFisica}"
        }
        layout.addView(tvInfo)

        val input = EditText(this).apply {
            hint = "Cantidad física encontrada"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (item.cantidadFisica > 0) setText(item.cantidadFisica.toString())
            setSelectAllOnFocus(true)
        }
        layout.addView(input)

        builder.setView(layout)
        builder.setPositiveButton("Guardar") { _, _ ->
            val cant = input.text.toString().toDoubleOrNull()
            if (cant == null) {
                Toast.makeText(this, "Ingresa un número válido", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            item.cantidadFisica = cant
            productosContadosSet.add(item.producto.CodigoBarras)
            adapter?.notifyDataSetChanged()

            val dif = item.cantidadFisica - item.producto.Existencias
            val unidad = item.producto.Unidad ?: "PZA"
            val mensaje = when {
                dif > 0 -> "SOBRA ${abs(dif)} $unidad"
                dif < 0 -> "FALTA ${abs(dif)} $unidad"
                else -> "CORRECTO: Lo físico concuerda con sistema"
            }
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()

            if (esIndividual) confirmarSincronizacionIndividual(item)

            txtBusqueda.text.clear()
            txtBusquedaIndividual.text.clear()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun confirmarSincronizacionIndividual(item: ProductoConteo) {
        val pActualizado = item.producto.copy(Existencias = item.cantidadFisica)
        apiService?.actualizarProducto(pActualizado)?.enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful && response.body()?.trim()?.contains("OK") == true) {
                    Toast.makeText(
                        this@ConsultaActivity,
                        "Actualizado en MyBusiness",
                        Toast.LENGTH_SHORT
                    ).show()
                    val index =
                        productosAuditados.indexOfFirst { it.producto.CodigoBarras == item.producto.CodigoBarras }
                    if (index != -1) {
                        productosAuditados[index] = item.copy(producto = pActualizado)
                        adapter?.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(
                        this@ConsultaActivity,
                        "Error: ${response.body()}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@ConsultaActivity, "Error de red", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun mostrarConfirmacionFinalizar() {
        val contados =
            productosAuditados.filter { productosContadosSet.contains(it.producto.CodigoBarras) }
        if (contados.isEmpty()) {
            Toast.makeText(this, "No hay productos contados para finalizar.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Finalizar Auditoría")
            .setMessage(
                "Se han contado ${contados.size} productos.\n\n" +
                        "Antes de continuar, se te pedirá motivo para las diferencias (sobra/falta)."
            )
            .setPositiveButton("Sincronizar con MyBusiness") { _, _ ->
                solicitarMotivosParaDiferencias(contados) {
                    sincronizarTodoPorLotes(contados)
                }
            }
            .setNeutralButton("Solo Generar CSV") { _, _ ->
                solicitarMotivosParaDiferencias(contados) {
                    generarCSV()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun solicitarMotivosParaDiferencias(
        lista: List<ProductoConteo>,
        onComplete: () -> Unit
    ) {
        val pendientes =
            lista.filter { it.cantidadFisica - it.producto.Existencias != 0.0 && it.motivoDiferencia.isNullOrBlank() }
        if (pendientes.isEmpty()) {
            onComplete()
            return
        }

        val motivos = arrayOf("Mal Ubicado", "Dañado", "Sin Etiqueta", "Error de Captura", "Otro")
        fun procesar(index: Int) {
            if (index >= pendientes.size) {
                onComplete()
                return
            }

            val item = pendientes[index]
            val dif = item.cantidadFisica - item.producto.Existencias
            val unidad = item.producto.Unidad ?: "PZA"
            val mensaje = when {
                dif > 0 -> "Sobra ${abs(dif)} $unidad"
                dif < 0 -> "Falta ${abs(dif)} $unidad"
                else -> "Sin diferencia"
            }

            AlertDialog.Builder(this)
                .setTitle(item.producto.Producto)
                .setMessage("$mensaje\n\nSelecciona el motivo de la diferencia:")
                .setItems(motivos) { _, seleccion ->
                    item.motivoDiferencia = motivos[seleccion]
                    procesar(index + 1)
                }
                .setNegativeButton("Omitir") { _, _ -> procesar(index + 1) }
                .setCancelable(false)
                .show()
        }

        procesar(0)
    }

    private fun sincronizarTodoPorLotes(lista: List<ProductoConteo>) {
        if (lista.isEmpty()) return

        val progress = ProgressDialog(this)
        progress.setTitle("Sincronizando...")
        progress.setMessage("Iniciando actualización masiva...")
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progress.max = lista.size
        progress.setCancelable(false)
        progress.show()

        var exitosos = 0
        var errores = 0

        fun enviarSiguiente(index: Int) {
            if (index >= lista.size) {
                progress.dismiss()
                AlertDialog.Builder(this@ConsultaActivity)
                    .setTitle("Sincronización Completa")
                    .setMessage("Éxitos: $exitosos\nErrores: $errores\n\nLa tabla de productos ha sido actualizada.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val item = lista[index]
            progress.setMessage("Actualizando: ${item.producto.Producto}")
            progress.progress = index + 1

            val pActualizado = item.producto.copy(Existencias = item.cantidadFisica)
            apiService?.actualizarProducto(pActualizado)?.enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful && response.body()?.trim()?.contains("OK") == true) {
                        exitosos++
                    } else {
                        errores++
                    }
                    enviarSiguiente(index + 1)
                }

                override fun onFailure(call: Call<String>, t: Throwable) {
                    errores++
                    enviarSiguiente(index + 1)
                }
            })
        }

        enviarSiguiente(0)
    }

    private fun generarCSV() {
        val csv = StringBuilder("Codigo,Producto,Anterior,Nuevo,Dif,Motivo\n")
        productosAuditados.forEach {
            val fueContado = productosContadosSet.contains(it.producto.CodigoBarras)
            val fisicoFinal = if (fueContado) it.cantidadFisica else it.producto.Existencias
            val dif = fisicoFinal - it.producto.Existencias
            csv.append("${it.producto.CodigoBarras},${it.producto.Producto},${it.producto.Existencias},$fisicoFinal,$dif,${it.motivoDiferencia ?: ""}\n")
        }

        val file = File(getExternalFilesDir(null), "Auditoria_${System.currentTimeMillis()}.csv")
        FileOutputStream(file).use { it.write(csv.toString().toByteArray()) }
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartir Reporte"))
    }

    // Funciones de apoyo (Búsqueda, Retrofit, etc. se mantienen igual)
    private fun iniciarAuditoriaPorLinea(linea: Linea) {
        lineaSeleccionada = linea
        tvLineaActiva.text = "Línea: ${linea.Descrip}"
        layoutSeleccionInicial.visibility = View.GONE
        layoutTrabajoLinea.visibility = View.VISIBLE
        btnFinalizarReporte.visibility = View.VISIBLE
        cargarProductosDeLinea(linea.Linea)
    }

    private fun cargarProductosDeLinea(codigoLinea: String) {
        updateStatus(1)
        apiService?.getProductosPorLinea(codigoLinea)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(
                call: Call<List<Producto>>,
                response: Response<List<Producto>>
            ) {
                updateStatus(0)
                if (response.isSuccessful) {
                    productosAuditados.clear()
                    response.body()?.forEach { productosAuditados.add(ProductoConteo(it)) }
                    adapter?.notifyDataSetChanged()
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                updateStatus(2)
            }
        })
    }

    private fun buscarIndividualUrgente(query: String) {
        if (query.isBlank()) return
        apiService?.buscarProducto(query)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                if (response.isSuccessful && response.body() != null) mostrarDialogoConteo(
                    ProductoConteo(response.body()!!),
                    true
                )
            }

            override fun onFailure(call: Call<Producto>, t: Throwable) {}
        })
    }

    private fun buscarEnLista(query: String) {
        val p = productosAuditados.find {
            it.producto.CodigoBarras == query || it.producto.Producto.contains(
                query,
                ignoreCase = true
            )
        }
        if (p != null) mostrarDialogoConteo(p) else Toast.makeText(
            this,
            "No en lista",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setupRetrofit() {
        val ip =
            getSharedPreferences("config", Context.MODE_PRIVATE).getString("api_ip", "192.168.1.XX")
        apiService = Retrofit.Builder().baseUrl("http://$ip:3000/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ApiService::class.java)
    }

    private fun cargarLineas() {
        apiService?.getLineas()?.enqueue(object : Callback<List<Linea>> {
            override fun onResponse(call: Call<List<Linea>>, response: Response<List<Linea>>) {
                if (response.isSuccessful) {
                    listaLineas =
                        (response.body() ?: emptyList()).sortedBy { it.Descrip.lowercase() }
                    spinnerLineaInicial.setAdapter(
                        LineaAdapter(
                            this@ConsultaActivity,
                            R.layout.item_linea_dropdown,
                            listaLineas,
                            false
                        )
                    )
                }
            }

            override fun onFailure(call: Call<List<Linea>>, t: Throwable) {}
        })
    }

    private fun verificarConexion() {
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
                0 -> R.color.success; 1 -> R.color.warning; else -> R.color.error
            }
        )
        viewStatusDot.background =
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        tvStatusText.text = when (status) {
            0 -> "Conectado"; 1 -> "Conectando..."; else -> "Desconectado"
        }
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) {
        if (it.contents != null) {
            if (layoutTrabajoLinea.visibility == View.VISIBLE) buscarEnLista(it.contents) else buscarIndividualUrgente(
                it.contents
            )
        }
    }

    private fun mostrarMenuProductoAuditado(producto: ProductoConteo) {
        val opciones = arrayOf("Motivo de Diferencia", "Sincronizar Manual", "Editar Manual")
        AlertDialog.Builder(this).setItems(opciones) { _, which ->
            when (which) {
                0 -> {
                    val motivos =
                        arrayOf("Mal Ubicado", "Dañado", "Sin Etiqueta", "Error de Captura")
                    AlertDialog.Builder(this).setItems(motivos) { _, m ->
                        producto.motivoDiferencia = motivos[m]; adapter?.notifyDataSetChanged()
                    }.show()
                }

                1 -> confirmarSincronizacionIndividual(producto)
                2 -> {
                    val input = EditText(this).apply {
                        inputType =
                            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(
                        producto.cantidadFisica.toString()
                    )
                    }
                    AlertDialog.Builder(this).setTitle("Editar").setView(input)
                        .setPositiveButton("Fijar") { _, _ ->
                            producto.cantidadFisica = input.text.toString().toDoubleOrNull()
                                ?: 0.0; productosContadosSet.add(producto.producto.CodigoBarras); adapter?.notifyDataSetChanged()
                        }.show()
                }
            }
        }.show()
    }
}
