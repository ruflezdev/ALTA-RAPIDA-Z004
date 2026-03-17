package com.ruflezdev.altarapida

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView
    private var apiService: ApiService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_consulta)

        txtBusqueda = findViewById(R.id.txtBusqueda)
        rvResultados = findViewById(R.id.rvResultados)
        btnScanConsulta = findViewById(R.id.btnScanConsulta)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        tvStatusText = findViewById(R.id.tvStatusText)

        rvResultados.layoutManager = LinearLayoutManager(this)

        setupRetrofit()
        verificarConexion()

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
            updateStatus(2) // Error
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
            1 -> R.color.warning // Amarillo
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

    private fun realizarBusqueda(query: String) {
        if (query.isBlank()) return
        updateStatus(1)

        // Prioridad 1: Búsqueda exacta primero
        apiService?.buscarProducto(query)?.enqueue(object : Callback<Producto> {
            override fun onResponse(call: Call<Producto>, response: Response<Producto>) {
                if (response.isSuccessful && response.body() != null) {
                    updateStatus(0)
                    rvResultados.adapter = ProductAdapter(listOf(response.body()!!))
                } else {
                    // Si no es exacto, intentamos búsqueda general
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
            override fun onResponse(
                call: Call<List<Producto>>,
                response: Response<List<Producto>>
            ) {
                updateStatus(0)
                if (response.isSuccessful) {
                    val productos = response.body() ?: emptyList()
                    rvResultados.adapter = ProductAdapter(productos)
                    if (productos.isEmpty()) {
                        Toast.makeText(
                            this@ConsultaActivity,
                            "Producto no encontrado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    rvResultados.adapter = ProductAdapter(emptyList())
                    Toast.makeText(
                        this@ConsultaActivity,
                        "Error en la búsqueda",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<List<Producto>>, t: Throwable) {
                updateStatus(2)
                Toast.makeText(this@ConsultaActivity, "Error de conexión", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }
}
