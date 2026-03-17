package com.ruflezdev.altarapida

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

    // Para rastrear qué productos han sido contados activamente
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

        // Al tocar manualmente (click), entra directamente a actualizar
        adapter = ProductConteoAdapter(
            productosAuditados,
            isPedido = false,
            onItemClick = { producto ->
                mostrarDialogoConteo(producto)
            },
            onItemLongClick = { producto ->
                mostrarMenuProductoAuditado(producto)
            }
        )
        rvResultados.adapter = adapter

        setupRetrofit()
        verificarConexion()
        cargarLineas()

        txtBusquedaIndividual.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                buscarIndividualUrgente(txtBusquedaIndividual.text.toString())
                true
            } else false
        }

        spinnerLineaInicial.setOnItemClickListener { parent, _, position, _ ->
            val linea = parent.getItemAtPosition(position) as Linea
            iniciarAuditoriaPorLinea(linea)
        }

        txtBusqueda.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscarEnLista(txtBusqueda.text.toString())
                true
            } else false
        }

        btnScanConsulta.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Escaneando...")
                setBeepEnabled(true)
                setCaptureActivity(CustomCaptureActivity::class.java)
            }
            barcodeLauncher.launch(options)
        }

        btnFinalizarReporte.setOnClickListener { mostrarConfirmacionFinalizar() }
    }

    private fun buscarIndividualUrgente(query: String) {
        if (query.isBlank()) return
        updateStatus(1)
        apiService?.buscarProducto(query)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                updateStatus(0)
                if (response.isSuccessful && response.body() != null) {
                    val p = response.body()!!
                    mostrarDialogoConteo(ProductoConteo(p), esIndividual = true)
                } else {
                    buscarIndividualPorLista(query)
                }
            }

            override fun onFailure(call: Call<Producto>, t: Throwable) {
                updateStatus(2)
            }
        })
    }

    private fun buscarIndividualPorLista(query: String) {
        apiService?.buscarProductos(query)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(
                call: Call<List<Producto>>,
                response: Response<List<Producto>>
            ) {
                if (response.isSuccessful) {
                    val lista = response.body() ?: emptyList()
                    if (lista.size == 1) mostrarDialogoConteo(
                        ProductoConteo(lista[0]),
                        esIndividual = true
                    )
                    else if (lista.size > 1) mostrarSeleccionIndividual(lista)
                    else Toast.makeText(this@ConsultaActivity, "No encontrado", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {}
        })
    }

    private fun mostrarSeleccionIndividual(productos: List<Producto>) {
        val nombres = productos.map { it.Producto }.toTypedArray()
        AlertDialog.Builder(this).setItems(nombres) { _, which ->
            mostrarDialogoConteo(ProductoConteo(productos[which]), esIndividual = true)
        }.show()
    }

    private fun iniciarAuditoriaPorLinea(linea: Linea) {
        lineaSeleccionada = linea
        tvLineaActiva.text = "Línea: ${linea.Descrip}"
        layoutSeleccionInicial.visibility = View.GONE
        layoutTrabajoLinea.visibility = View.VISIBLE
        btnFinalizarReporte.visibility = View.VISIBLE
        productosContadosSet.clear()
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

    private fun mostrarDialogoConteo(item: ProductoConteo, esIndividual: Boolean = false) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(item.producto.Producto)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val tvInfo = TextView(this)
        val unidad = item.producto.Unidad ?: "PZA"
        tvInfo.text =
            "Sistema: ${item.producto.Existencias} $unidad | Físico: ${item.cantidadFisica} $unidad"
        layout.addView(tvInfo)

        val input = EditText(this)
        input.hint = "Cantidad encontrada"
        input.setText("")
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setSelectAllOnFocus(true)
        layout.addView(input)

        builder.setView(layout)

        builder.setPositiveButton("OK (Sumar)") { _, _ ->
            val cant = input.text.toString().toDoubleOrNull() ?: 0.0
            item.cantidadFisica += cant
            productosContadosSet.add(item.producto.CodigoBarras)

            if (esIndividual) confirmarSincronizacionIndividual(item)
            else adapter?.notifyDataSetChanged()

            txtBusquedaIndividual.text.clear()
            txtBusqueda.text.clear()
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun mostrarDialogoEdicionManual(item: ProductoConteo, esIndividual: Boolean) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(item.cantidadFisica.toString())
        input.setSelectAllOnFocus(true)

        AlertDialog.Builder(this)
            .setTitle("Editar Cantidad Física")
            .setView(input)
            .setPositiveButton("Fijar") { _, _ ->
                item.cantidadFisica = input.text.toString().toDoubleOrNull() ?: 0.0
                productosContadosSet.add(item.producto.CodigoBarras)
                if (esIndividual) confirmarSincronizacionIndividual(item)
                else adapter?.notifyDataSetChanged()
            }
            .setNegativeButton("Atrás", null)
            .show()
    }

    private fun mostrarMenuProductoAuditado(producto: ProductoConteo) {
        // Al dejar presionado sale el menú solicitado
        val opciones = arrayOf("Motivo de Diferencia", "Sincronización Manual", "Editar Manual")
        AlertDialog.Builder(this).setItems(opciones) { _, which ->
            when (which) {
                0 -> mostrarDialogoEspecificarMotivo(producto)
                1 -> confirmarSincronizacionIndividual(producto)
                2 -> mostrarDialogoEdicionManual(producto, false)
            }
        }.show()
    }

    private fun confirmarSincronizacionIndividual(item: ProductoConteo) {
        AlertDialog.Builder(this)
            .setTitle("Sincronizar Producto")
            .setMessage("¿Actualizar '${item.producto.Producto}' a ${item.cantidadFisica} en MyBusiness?")
            .setPositiveButton("Sincronizar Ahora") { _, _ ->
                val pActualizado = item.producto.copy(Existencias = item.cantidadFisica)
                apiService?.actualizarProducto(pActualizado)?.enqueue(object : Callback<String> {
                    override fun onResponse(call: Call<String>, response: Response<String>) {
                        if (response.isSuccessful && response.body() == "OK") {
                            Toast.makeText(
                                this@ConsultaActivity,
                                "Sincronizado correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                            val index =
                                productosAuditados.indexOfFirst { it.producto.CodigoBarras == item.producto.CodigoBarras }
                            if (index != -1) {
                                productosAuditados[index] = item.copy(producto = pActualizado)
                                adapter?.notifyDataSetChanged()
                            }
                        }
                    }

                    override fun onFailure(call: Call<String>, t: Throwable) {
                        Toast.makeText(
                            this@ConsultaActivity,
                            "Error al sincronizar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }
            .setNegativeButton("Solo guardar local") { _, _ ->
                if (!productosAuditados.contains(item)) productosAuditados.add(0, item)
                adapter?.notifyDataSetChanged()
            }
            .show()
    }

    private fun mostrarDialogoEspecificarMotivo(item: ProductoConteo) {
        val motivos = arrayOf("Mal Ubicado", "Dañado", "Sin Etiqueta", "Error de Captura", "Otro")
        AlertDialog.Builder(this)
            .setTitle("Motivo de Diferencia")
            .setItems(motivos) { _, which ->
                item.motivoDiferencia = motivos[which]
                adapter?.notifyDataSetChanged()
            }
            .show()
    }

    private fun mostrarConfirmacionFinalizar() {
        val total = productosAuditados.size
        val contados =
            productosAuditados.count { productosContadosSet.contains(it.producto.CodigoBarras) }
        val faltantes = total - contados

        val resumen = "RESUMEN DE AUDITORÍA:\n" +
                "------------------------------\n" +
                "Total en Línea: $total\n" +
                "Contados: $contados\n" +
                "Faltan por Auditar: $faltantes\n\n" +
                "¿Estás 100% seguro de generar el reporte?\n\n" +
                "RECUERDA: Los productos que no contaste mantendrán su stock actual de sistema. " +
                "Al finalizar, deberás sincronizar el reporte con MyBusiness POS."

        AlertDialog.Builder(this)
            .setTitle("¿Generar Reporte?")
            .setMessage(resumen)
            .setPositiveButton("SÍ, GENERAR REPORTE") { _, _ ->
                generarCSV()
                Toast.makeText(
                    this,
                    "Reporte generado. Recuerda sincronizar en MyBusiness POS.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Seguir Auditando", null)
            .setNeutralButton("Limpiar Todo") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Limpiar Auditoría")
                    .setMessage("Se borrarán los cambios locales no sincronizados. ¿Continuar?")
                    .setPositiveButton("Sí, Limpiar") { _, _ ->
                        productosAuditados.clear()
                        productosContadosSet.clear()
                        adapter?.notifyDataSetChanged()
                        layoutSeleccionInicial.visibility = View.VISIBLE
                        layoutTrabajoLinea.visibility = View.GONE
                        btnFinalizarReporte.visibility = View.GONE
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .show()
    }

    private fun generarCSV() {
        val csv = StringBuilder("Codigo,Producto,Sistema,Fisico,Diferencia,Motivo,Auditado\n")
        productosAuditados.forEach {
            val fueContado = productosContadosSet.contains(it.producto.CodigoBarras)
            // Si no se contó, se deja lo que está en stock (se omite el cambio)
            val fisicoFinal = if (fueContado) it.cantidadFisica else it.producto.Existencias
            val dif = fisicoFinal - it.producto.Existencias
            val statusContado = if (fueContado) "SI" else "NO (Se mantiene stock)"

            csv.append("${it.producto.CodigoBarras},${it.producto.Producto},${it.producto.Existencias},$fisicoFinal,$dif,${it.motivoDiferencia ?: ""},$statusContado\n")
        }

        val fileName =
            "Auditoria_${lineaSeleccionada?.Descrip ?: "General"}_${System.currentTimeMillis()}.csv"
        val file = File(getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { it.write(csv.toString().toByteArray()) }

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir Reporte"))
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

    private fun updateStatus(status: Int) {
        val color = ContextCompat.getColor(
            this, when (status) {
                0 -> R.color.success; 1 -> R.color.warning; else -> R.color.error
            }
        )
        val drawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        viewStatusDot.background = drawable
        tvStatusText.text = when (status) {
            0 -> "Conectado"; 1 -> "Conectando..."; else -> "Desconectado"
        }
    }

    private val barcodeLauncher =
        registerForActivityResult(ScanContract()) { if (it.contents != null) buscarOScanear(it.contents) }

    private fun buscarOScanear(q: String) {
        if (layoutTrabajoLinea.visibility == View.VISIBLE) buscarEnLista(q)
        else buscarIndividualUrgente(q)
    }
}
