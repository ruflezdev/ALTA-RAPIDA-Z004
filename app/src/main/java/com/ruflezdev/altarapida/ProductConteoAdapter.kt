package com.ruflezdev.altarapida

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProductConteoAdapter(
    private var productos: MutableList<ProductoConteo>,
    private val isPedido: Boolean = false,
    private val onItemClick: (ProductoConteo) -> Unit,
    private val onItemLongClick: ((ProductoConteo) -> Unit)? = null
) : RecyclerView.Adapter<ProductConteoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvCodigo: TextView = view.findViewById(R.id.tvCodigo)
        val tvStockSistema: TextView = view.findViewById(R.id.tvStockSistema)
        val tvConteoFisico: TextView = view.findViewById(R.id.tvConteoFisico)
        val tvDiferencia: TextView = view.findViewById(R.id.tvDiferencia)
        val tvMotivo: TextView = view.findViewById(R.id.tvMotivo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto_conteo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = productos[position]
        val unidad = p.producto.Unidad ?: "PZA"

        holder.tvNombre.text = p.producto.Producto
        holder.tvCodigo.text = "Código: ${p.producto.CodigoBarras}"
        holder.tvStockSistema.text = "Sistema: ${p.producto.Existencias} $unidad"

        if (isPedido) {
            // Modo Pedido: No mostrar diferencias, solo lo que se va a pedir
            holder.tvConteoFisico.text = "A Pedir: ${p.cantidadFisica} $unidad"
            holder.tvDiferencia.visibility = View.GONE
            holder.tvMotivo.visibility = View.GONE
        } else {
            // Modo Auditoría: Mostrar diferencias y motivos
            holder.tvConteoFisico.text = "Físico: ${p.cantidadFisica} $unidad"
            holder.tvDiferencia.visibility = View.VISIBLE

            val dif = p.cantidadFisica - p.producto.Existencias
            when {
                dif > 0 -> {
                    holder.tvDiferencia.text = "SOBRANTE (+${dif}) $unidad"
                    holder.tvDiferencia.setTextColor(Color.parseColor("#2196F3"))
                }

                dif < 0 -> {
                    holder.tvDiferencia.text = "FALTANTE (${dif}) $unidad"
                    holder.tvDiferencia.setTextColor(Color.RED)
                }

                else -> {
                    holder.tvDiferencia.text = "CORRECTO"
                    holder.tvDiferencia.setTextColor(Color.parseColor("#4CAF50"))
                }
            }

            if (!p.motivoDiferencia.isNullOrEmpty()) {
                holder.tvMotivo.visibility = View.VISIBLE
                holder.tvMotivo.text =
                    "Motivo: ${p.motivoDiferencia} ${p.productoRelacionado ?: ""}"
            } else {
                holder.tvMotivo.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener { onItemClick(p) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(p)
            true
        }
    }

    override fun getItemCount() = productos.size
}
