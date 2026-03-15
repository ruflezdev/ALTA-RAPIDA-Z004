package com.ruflezdev.altarapida

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProductAdapter(private val productos: List<Producto>) :
    RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvCodigo: TextView = view.findViewById(R.id.tvCodigo)
        val tvPrecio: TextView = view.findViewById(R.id.tvPrecio)
        val tvStock: TextView = view.findViewById(R.id.tvStock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_producto, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = productos[position]
        holder.tvNombre.text = p.Producto
        holder.tvCodigo.text = "Código: ${p.CodigoBarras}"
        holder.tvPrecio.text = "Precio: $${p.PrecioPublico}"
        holder.tvStock.text = "Stock: ${p.Existencias}"
    }

    override fun getItemCount() = productos.size
}
