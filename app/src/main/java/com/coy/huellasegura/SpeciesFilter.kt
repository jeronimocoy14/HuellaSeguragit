package com.coy.huellasegura

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.DocumentSnapshot

/** Fichas para filtrar por categoría de animal ("Todas" + las 12 de [Species]); se usan en varias pantallas. */
object SpeciesFilter {

    /**
     * Llena [group] y avisa con [onChange] cada vez que se elige una: recibe el nombre de la categoría
     * (p. ej. "Ave") o null si se eligió "Todas".
     */
    fun setup(activity: Activity, group: ChipGroup, onChange: (String?) -> Unit) {
        val inflater = LayoutInflater.from(activity)
        fun addChip(text: String, species: String?, checked: Boolean) {
            val chip = inflater.inflate(R.layout.item_species_chip, group, false) as Chip
            chip.id = android.view.View.generateViewId()
            chip.text = text
            chip.tag = species
            chip.isChecked = checked
            group.addView(chip)
        }
        addChip(activity.getString(R.string.species_filter_all), null, checked = true)
        Species.all.forEach { addChip(activity.getString(it.nameRes), activity.getString(it.nameRes), checked = false) }
        group.setOnCheckedStateChangeListener { g, ids ->
            onChange(ids.firstOrNull()?.let { g.findViewById<Chip>(it)?.tag as? String })
        }
    }

    /** Vuelve a «Todas». */
    fun reset(group: ChipGroup) {
        group.getChildAt(0)?.let { group.check(it.id) }
    }

    /** ¿El documento es de la categoría elegida? Sin categoría elegida (null) entran todos. */
    fun matches(doc: DocumentSnapshot, selected: String?): Boolean =
        selected == null || doc.getString("especie").equals(selected, ignoreCase = true)
}
