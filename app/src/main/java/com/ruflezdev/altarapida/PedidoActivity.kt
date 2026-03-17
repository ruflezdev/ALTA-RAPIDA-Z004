package com.ruflezdev.altarapida

import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.io.FileOutputStream

class PedidoActivity : AppCompatActivity() {

    private lateinit var layoutSeleccionLinea: LinearLayout
    private lateinit var layoutTrabajoPedido: LinearLayout
    private lateinit var spinnerLineaPedido: AutoCompleteTextView
    private lateinit var btnPedidoLibre: View
    private lateinit var tvLineaActivaPedido: TextView
    private lateinit var txtBusquedaPedido: EditText
    private lateinit var btnScanPedido: ImageButton
    private lateinit var rvPedido: RecyclerView
    private lateinit var btnFinalizarPedido: View

    private var apiService: ApiService? = null
    private var listaPedido: MutableList<ProductoConteo> = mutableListOf()
    private var adapter: ProductConteoAdapter? = null
    private var lineaSeleccionada: Linea? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedido)

        layoutSeleccionLinea = findViewById(R.id.layoutSeleccionLineaPedido)
        layoutTrabajoPedido = findViewById(R.id.layoutTrabajoPedido)
        spinnerLineaPedido = findViewById(R.id.spinnerLineaPedido)
        btnPedidoLibre = findViewById(R.id.btnPedidoLibre)
        tvLineaActivaPedido = findViewById(R.id.tvLineaActivaPedido)
        txtBusquedaPedido = findViewById(R.id.txtBusquedaPedido)
        btnScanPedido = findViewById(R.id.btnScanPedido)
        rvPedido = findViewById(R.id.rvPedido)
        btnFinalizarPedido = findViewById(R.id.btnFinalizarPedido)

        rvPedido.layoutManager = LinearLayoutManager(this)
        // Se pasa isPedido = true para que el adaptador oculte diferencias y muestre "A Pedir"
        adapter = ProductConteoAdapter(
            productos = listaPedido,
            isPedido = true,
            onItemClick = { item ->
                mostrarDialogoCantidadPedido(item)
            }
        )
        rvPedido.adapter = adapter

        setupRetrofit()
        cargarLineas()

        spinnerLineaPedido.setOnItemClickListener { parent, _, position, _ ->
            val linea = parent.getItemAtPosition(position) as Linea
            iniciarPedidoPorLinea(linea)
        }

        btnPedidoLibre.setOnClickListener {
            iniciarPedidoLibre()
        }

        txtBusquedaPedido.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                ejecutarBusqueda(txtBusquedaPedido.text.toString())
                true
            } else {
                false
            }
        }

        btnScanPedido.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Escaneando producto para pedido...")
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
                setCaptureActivity(CustomCaptureActivity::class.java)
            }
            barcodeLauncher.launch(options)
        }

        btnFinalizarPedido.setOnClickListener {
            mostrarOpcionesFinalizar()
        }
    }

    private fun cargarLineas() {
        apiService?.getLineas()?.enqueue(object : Callback<List<Linea>> {
            override fun onResponse(call: Call<List<Linea>>, response: Response<List<Linea>>) {
                if (response.isSuccessful) {
                    val lineas =
                        (response.body() ?: emptyList()).sortedBy { it.Descrip.lowercase() }
                    val adapterLinea = LineaAdapter(
                        this@PedidoActivity,
                        R.layout.item_linea_dropdown,
                        lineas,
                        false
                    )
                    spinnerLineaPedido.setAdapter(adapterLinea)
                }
            }

            override fun onFailure(call: Call<List<Linea>>, t: Throwable) {}
        })
    }

    private fun iniciarPedidoPorLinea(linea: Linea) {
        lineaSeleccionada = linea
        tvLineaActivaPedido.text = "Pedido Línea: ${linea.Descrip}"
        layoutSeleccionLinea.visibility = View.GONE
        layoutTrabajoPedido.visibility = View.VISIBLE
        btnFinalizarPedido.visibility = View.VISIBLE

        apiService?.getProductosPorLinea(linea.Linea)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(
                call: Call<List<Producto>>,
                response: Response<List<Producto>>
            ) {
                if (response.isSuccessful) {
                    val prods = response.body() ?: emptyList()
                    listaPedido.clear()
                    prods.forEach { listaPedido.add(ProductoConteo(it, 0.0)) }
                    adapter?.notifyDataSetChanged()
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {}
        })
    }

    private fun iniciarPedidoLibre() {
        lineaSeleccionada = null
        tvLineaActivaPedido.text = "Pedido Libre (Multimarcas)"
        layoutSeleccionLinea.visibility = View.GONE
        layoutTrabajoPedido.visibility = View.VISIBLE
        btnFinalizarPedido.visibility = View.VISIBLE
        listaPedido.clear()
        adapter?.notifyDataSetChanged()
    }

    private val barcodeLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            if (result.contents != null) {
                ejecutarBusqueda(result.contents)
            }
        }

    private fun ejecutarBusqueda(query: String) {
        if (query.isBlank()) return

        apiService?.buscarProducto(query)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                if (response.isSuccessful && response.body() != null) {
                    agregarOPedir(response.body()!!)
                } else {
                    apiService?.buscarProductos(query)?.enqueue(object : Callback<List<Producto>> {
                        override fun onResponse(
                            call: Call<List<Producto>>,
                            response: Response<List<Producto>>
                        ) {
                            if (response.isSuccessful) {
                                val lista = response.body() ?: emptyList()
                                if (lista.isNotEmpty()) {
                                    if (lista.size == 1) {
                                        agregarOPedir(lista[0])
                                    } else {
                                        mostrarSeleccion(lista)
                                    }
                                } else {
                                    Toast.makeText(
                                        this@PedidoActivity,
                                        "No encontrado",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        override fun onFailure(call: Call<List<Producto>>, t: Throwable) {}
                    })
                }
            }

            override fun onFailure(call: Call<Producto>, t: Throwable) {
                Toast.makeText(this@PedidoActivity, "Error de red", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun mostrarSeleccion(productos: List<Producto>) {
        val nombres = productos.map { "${it.Producto} (${it.CodigoBarras})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar producto")
            .setItems(nombres) { _, which -> agregarOPedir(productos[which]) }
            .show()
    }

    private fun agregarOPedir(p: Producto) {
        val yaEnLista = listaPedido.find { it.producto.CodigoBarras == p.CodigoBarras }
        if (yaEnLista != null) {
            mostrarDialogoCantidadPedido(yaEnLista)
        } else {
            val nuevo = ProductoConteo(p, 0.0)
            listaPedido.add(0, nuevo)
            adapter?.notifyDataSetChanged()
            mostrarDialogoCantidadPedido(nuevo)
        }
        txtBusquedaPedido.text.clear()
    }

    private fun mostrarDialogoCantidadPedido(item: ProductoConteo) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(item.producto.Producto)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val tvStock = TextView(this)
        val unidad = item.producto.Unidad ?: "PZA"
        tvStock.text = "Stock en Sistema: ${item.producto.Existencias} $unidad"
        layout.addView(tvStock)

        val input = EditText(this)
        input.hint = "Cantidad a pedir"
        if (item.cantidadFisica > 0) {
            input.setText(item.cantidadFisica.toString())
        }
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setSelectAllOnFocus(true)
        layout.addView(input)

        builder.setView(layout)
        builder.setPositiveButton("OK") { _, _ ->
            val cant = input.text.toString().toDoubleOrNull() ?: 0.0
            item.cantidadFisica = cant
            adapter?.notifyDataSetChanged()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun mostrarOpcionesFinalizar() {
        val opciones = arrayOf("Enviar por WhatsApp", "Exportar a Excel (CSV)", "Cancelar")
        AlertDialog.Builder(this)
            .setTitle("Finalizar Pedido")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> enviarWhatsApp()
                    1 -> exportarExcel()
                }
            }
            .show()
    }

    private fun enviarWhatsApp() {
        val pedidos = listaPedido.filter { it.cantidadFisica > 0 }
        if (pedidos.isEmpty()) {
            Toast.makeText(this, "No hay productos en el pedido", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.append("*PEDIDO: ${lineaSeleccionada?.Descrip ?: "Libre"}*\n\n")
        pedidos.forEach {
            sb.append("• ${it.producto.Producto}: *${it.cantidadFisica} ${it.producto.Unidad ?: "PZA"}*\n")
        }

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString())
        intent.setPackage("com.whatsapp")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            intent.setPackage(null)
            startActivity(Intent.createChooser(intent, "Compartir pedido"))
        }
    }

    private fun exportarExcel() {
        val pedidos = listaPedido.filter { it.cantidadFisica > 0 }
        if (pedidos.isEmpty()) return

        val fileName = "Pedido_${lineaSeleccionada?.Descrip ?: "Libre"}.csv"
        val file = File(getExternalFilesDir(null), fileName)

        try {
            val fos = FileOutputStream(file)
            fos.write("Producto,Cantidad,Unidad,Codigo\n".toByteArray())
            pedidos.forEach {
                val line =
                    "${it.producto.Producto},${it.cantidadFisica},${it.producto.Unidad ?: "PZA"},${it.producto.CodigoBarras}\n"
                fos.write(line.toByteArray())
            }
            fos.close()

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/csv"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(intent, "Enviar Excel"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al crear archivo", Toast.LENGTH_SHORT).show()
        }
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
}
