package com.ruflezdev.altarapida

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class LineaAdapter(
    context: Context,
    private val resource: Int,
    private val items: List<Linea>,
    private val showAddNew: Boolean = false
) : ArrayAdapter<Any>(context, resource) {

    private val displayItems: MutableList<Any> = mutableListOf()

    init {
        if (showAddNew) {
            displayItems.add("+ Crear nueva línea...")
        }
        displayItems.addAll(items)
    }

    override fun getCount(): Int = displayItems.size

    override fun getItem(position: Int): Any = displayItems[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(resource, parent, false)
        val item = getItem(position)

        val container = view.findViewById<View>(R.id.lytContainer)
        val tvCodigo = view.findViewById<TextView>(R.id.tvLineaCodigo)
        val tvDescrip = view.findViewById<TextView>(R.id.tvLineaDescrip)

        if (item is String) {
            // Caso "+ Crear nueva línea..."
            tvCodigo.text = item
            tvDescrip.visibility = View.GONE
            container.setBackgroundResource(0) // Sin borde para el botón de crear
        } else if (item is Linea) {
            tvCodigo.text = item.Linea
            tvDescrip.text = item.Descrip
            tvDescrip.visibility = View.VISIBLE
            container.setBackgroundResource(R.drawable.bg_linea_item)
        }

        return view
    }
}