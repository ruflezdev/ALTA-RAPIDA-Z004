package com.ruflezdev.altarapida

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

class ConsultaActivity : AppCompatActivity() {

    private lateinit var txtBusqueda: EditText
    private lateinit var rvResultados: RecyclerView
    private lateinit var btnScanConsulta: ImageButton
    private lateinit var spinnerLineaConsulta: AutoCompleteTextView
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView
    private var apiService: ApiService? = null
    private var listaLineas: List<Linea> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consulta)

        txtBusqueda = findViewById(R.id.txtBusqueda)
        rvResultados = findViewById(R.id.rvResultados)
        btnScanConsulta = findViewById(R.id.btnScanConsulta)
        spinnerLineaConsulta = findViewById(R.id.spinnerLineaConsulta)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)

        rvResultados.layoutManager = LinearLayoutManager(this)
        rvResultados.adapter = ProductAdapter(emptyList())

        setupRetrofit()
        verificarConexion()
        cargarLineas()

        txtBusqueda.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                realizarBusqueda(txtBusqueda.text.toString())
                true
            } else {
                false
            }
        }

        btnScanConsulta.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.ONE_D_CODE_TYPES)
                setPrompt("Escaneando para consulta...")
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
                setCaptureActivity(CustomCaptureActivity::class.java)
                addExtra("SCAN_CAMERA_ID", 0)
            }
            barcodeLauncher.launch(options)
        }

        spinnerLineaConsulta.setOnItemClickListener { parent, _, position, _ ->
            val seleccionado = parent.getItemAtPosition(position)
            if (seleccionado is Linea) {
                spinnerLineaConsulta.setText(seleccionado.Descrip, false)
                consultarPorLinea(seleccionado.Linea)
            }
        }
    }

    private val barcodeLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            if (result.contents != null) {
                txtBusqueda.setText(result.contents)
                realizarBusqueda(result.contents)
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

    private fun cargarLineas() {
        apiService?.getLineas()?.enqueue(object : Callback<List<Linea>> {
            override fun onResponse(call: Call<List<Linea>>, response: Response<List<Linea>>) {
                if (response.isSuccessful) {
                    val rawList = response.body() ?: emptyList()
                    listaLineas = rawList.sortedBy { it.Descrip.lowercase() }
                    
                    val adapter = LineaAdapter(this@ConsultaActivity, R.layout.item_linea_dropdown, listaLineas, false)
                    spinnerLineaConsulta.setAdapter(adapter)
                }
            }
            override fun onFailure(call: Call<List<Linea>>, t: Throwable) {
                Toast.makeText(this@ConsultaActivity, "Error al cargar líneas", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun consultarPorLinea(codigoLinea: String) {
        updateStatus(1)
        apiService?.getProductosPorLinea(codigoLinea)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                updateStatus(0)
                if (response.isSuccessful) {
                    val productos = response.body() ?: emptyList()
                    rvResultados.adapter = ProductAdapter(productos)
                    if (productos.isEmpty()) {
                        Toast.makeText(this@ConsultaActivity, "No hay productos en esta línea", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                updateStatus(2)
                Toast.makeText(this@ConsultaActivity, "Error al consultar línea", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateStatus(status: Int) {
        val color = when (status) {
            0 -> R.color.success
            1 -> R.color.warning
            else -> R.color.error
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

    private fun realizarBusqueda(query: String) {
        if (query.isBlank()) return
        updateStatus(1)

        apiService?.buscarProducto(query)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                if (response.isSuccessful && response.body() != null) {
                    updateStatus(0)
                    rvResultados.adapter = ProductAdapter(listOf(response.body()!!))
                } else {
                    intentarBusquedaGeneral(query)
                }
            }
            override fun onFailure(call: Call<Producto>, t: Throwable) {
                intentarBusquedaGeneral(query)
            }
        })
    }

    private fun intentarBusquedaGeneral(query: String) {
        apiService?.buscarProductos(query)?.enqueue(object : Callback<List<Producto>> {
            override fun onResponse(call: Call<List<Producto>>, response: Response<List<Producto>>) {
                updateStatus(0)
                if (response.isSuccessful) {
                    val productos = response.body() ?: emptyList()
                    rvResultados.adapter = ProductAdapter(productos)
                }
            }
            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                updateStatus(2)
                Toast.makeText(this@ConsultaActivity, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        })
    }
}