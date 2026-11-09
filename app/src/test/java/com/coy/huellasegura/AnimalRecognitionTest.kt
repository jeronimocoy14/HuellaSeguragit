package com.coy.huellasegura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reconocimiento del animal en la foto: cómo se traducen las etiquetas del modelo a nuestras categorías.
 * (El modelo en sí corre en el celular; aquí se prueba la lógica que decide la categoría.)
 */
class AnimalRecognitionTest {

    @Test
    fun `las etiquetas del modelo se buscan sin importar mayusculas`() {
        assertEquals("dog", AnimalCatalog.buscar("Dog")?.clave)
        assertEquals("dog", AnimalCatalog.buscar("  DOG ")?.clave)
    }

    @Test
    fun `los sinonimos apuntan a su animal`() {
        assertEquals("dog", AnimalCatalog.buscar("Puppy")?.clave)
        assertEquals("cat", AnimalCatalog.buscar("Kitten")?.clave)
        assertEquals("cattle", AnimalCatalog.buscar("Cow")?.clave)
        assertEquals("chicken", AnimalCatalog.buscar("Rooster")?.clave)
        assertEquals("turtle", AnimalCatalog.buscar("Tortoise")?.clave)
    }

    @Test
    fun `una etiqueta que no es un animal no se reconoce`() {
        assertNull(AnimalCatalog.buscar("Laptop"))
        assertNull(AnimalCatalog.buscar("Furniture"))
    }

    @Test
    fun `las etiquetas genericas se distinguen de los animales concretos`() {
        assertTrue(AnimalCatalog.esGenerica(AnimalCatalog.buscar("Pet")!!))
        assertTrue(AnimalCatalog.esGenerica(AnimalCatalog.buscar("Animal")!!))
        assertFalse(AnimalCatalog.esGenerica(AnimalCatalog.buscar("Dog")!!))
    }

    @Test
    fun `un loro es un tipo de ave`() {
        assertEquals("bird", AnimalCatalog.grupo("parrot"))
        assertEquals("bird", AnimalCatalog.grupo("chicken"))
        assertEquals("reptile", AnimalCatalog.grupo("snake"))
        assertNull(AnimalCatalog.grupo("dog"))
    }

    @Test
    fun `todo grupo amplio existe en el catalogo`() {
        listOf("bird", "reptile", "insect", "rodent", "fish").forEach { assertNotNull("falta $it", AnimalCatalog.buscar(it)) }
    }

    // ---- Del animal reconocido a las 12 categorías de la app

    @Test
    fun `perro y gato van a sus categorias`() {
        assertEquals(R.string.pets_species_dog, SpeciesDetector.nameResFor("dog"))
        assertEquals(R.string.pets_species_cat, SpeciesDetector.nameResFor("cat"))
    }

    @Test
    fun `las aves se agrupan en Ave`() {
        listOf("bird", "parrot", "owl", "eagle", "chicken", "duck", "goose", "turkey", "penguin")
            .forEach { assertEquals("clave $it", R.string.pets_species_bird, SpeciesDetector.nameResFor(it)) }
    }

    @Test
    fun `los animales de granja van a su categoria`() {
        assertEquals(R.string.pets_species_cow, SpeciesDetector.nameResFor("cattle"))
        assertEquals(R.string.pets_species_horse, SpeciesDetector.nameResFor("horse"))
        assertEquals(R.string.pets_species_horse, SpeciesDetector.nameResFor("donkey"))
        assertEquals(R.string.pets_species_pig, SpeciesDetector.nameResFor("pig"))
        assertEquals(R.string.pets_species_sheep, SpeciesDetector.nameResFor("sheep"))
        assertEquals(R.string.pets_species_goat, SpeciesDetector.nameResFor("goat"))
    }

    @Test
    fun `roedores y reptiles se agrupan`() {
        listOf("hamster", "guinea pig", "mouse", "squirrel").forEach {
            assertEquals("clave $it", R.string.pets_species_rodent, SpeciesDetector.nameResFor(it))
        }
        listOf("snake", "lizard", "iguana", "turtle", "crocodile").forEach {
            assertEquals("clave $it", R.string.pets_species_reptile, SpeciesDetector.nameResFor(it))
        }
    }

    @Test
    fun `lo que no encaja en ninguna categoria va a Otro`() {
        listOf("lion", "elephant", "shark", "butterfly", "wolf").forEach {
            assertEquals("clave $it", R.string.pets_species_other, SpeciesDetector.nameResFor(it))
        }
    }
}
