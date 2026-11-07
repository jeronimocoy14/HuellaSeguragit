package com.coy.huellasegura

/**
 * Catálogo de tipos de animales. Traduce las claves (en inglés) que producen
 * los modelos a un nombre en español con su emoji.
 *
 * - Las etiquetas del modelo general de ML Kit ("Dog", "Bird", "Butterfly"...) se
 *   buscan aquí directamente.
 * - Las ~400 especies de ImageNet se agrupan por tipo en assets/imagenet_tipos.txt
 *   (p. ej. "French bulldog" y "toy poodle" -> "dog"), y esa clave se busca aquí.
 */
object AnimalCatalog {

    data class AnimalInfo(val clave: String, val nombre: String, val emoji: String)

    private val tipos: Map<String, AnimalInfo> = listOf(
        // Mascotas y granja
        Triple("dog", "Perro", "🐶"),
        Triple("cat", "Gato", "🐱"),
        Triple("horse", "Caballo", "🐴"),
        Triple("donkey", "Burro", "🫏"),
        Triple("cattle", "Vaca / Ganado", "🐄"),
        Triple("sheep", "Oveja", "🐑"),
        Triple("goat", "Cabra", "🐐"),
        Triple("pig", "Cerdo", "🐷"),
        Triple("rabbit", "Conejo", "🐰"),
        Triple("hamster", "Hámster", "🐹"),
        Triple("guinea pig", "Cuy / Cobayo", "🐹"),
        Triple("chicken", "Gallina / Gallo", "🐔"),
        Triple("duck", "Pato", "🦆"),
        Triple("goose", "Ganso", "🪿"),
        Triple("turkey", "Pavo", "🦃"),
        Triple("llama", "Llama / Alpaca", "🦙"),
        Triple("camel", "Camello", "🐫"),
        // Felinos y cánidos salvajes
        Triple("lion", "León", "🦁"),
        Triple("tiger", "Tigre", "🐯"),
        Triple("leopard", "Leopardo", "🐆"),
        Triple("jaguar", "Jaguar", "🐆"),
        Triple("cheetah", "Guepardo", "🐆"),
        Triple("wild cat", "Felino salvaje", "🐈"),
        Triple("wolf", "Lobo", "🐺"),
        Triple("fox", "Zorro", "🦊"),
        Triple("coyote", "Coyote", "🐺"),
        Triple("hyena", "Hiena", "🐕"),
        Triple("wild canine", "Cánido salvaje", "🐕"),
        // Otros mamíferos
        Triple("bear", "Oso", "🐻"),
        Triple("panda", "Panda", "🐼"),
        Triple("red panda", "Panda rojo", "🦊"),
        Triple("monkey", "Mono", "🐒"),
        Triple("ape", "Simio (gorila, chimpancé...)", "🦍"),
        Triple("lemur", "Lémur", "🐒"),
        Triple("elephant", "Elefante", "🐘"),
        Triple("zebra", "Cebra", "🦓"),
        Triple("giraffe", "Jirafa", "🦒"),
        Triple("deer", "Venado / Ciervo", "🦌"),
        Triple("antelope", "Antílope / Gacela", "🦌"),
        Triple("bison", "Bisonte", "🦬"),
        Triple("buffalo", "Búfalo", "🐃"),
        Triple("rhinoceros", "Rinoceronte", "🦏"),
        Triple("hippopotamus", "Hipopótamo", "🦛"),
        Triple("squirrel", "Ardilla", "🐿️"),
        Triple("mouse", "Ratón / Rata", "🐭"),
        Triple("beaver", "Castor", "🦫"),
        Triple("marmot", "Marmota", "🐿️"),
        Triple("porcupine", "Puercoespín", "🦔"),
        Triple("hedgehog", "Erizo", "🦔"),
        Triple("rodent", "Roedor", "🐭"),
        Triple("otter", "Nutria", "🦦"),
        Triple("skunk", "Zorrillo", "🦨"),
        Triple("badger", "Tejón", "🦡"),
        Triple("weasel", "Comadreja / Hurón", "🦡"),
        Triple("raccoon", "Mapache", "🦝"),
        Triple("armadillo", "Armadillo", "🐾"),
        Triple("sloth", "Perezoso", "🦥"),
        Triple("kangaroo", "Canguro", "🦘"),
        Triple("koala", "Koala", "🐨"),
        Triple("wombat", "Wombat", "🐾"),
        Triple("marsupial", "Marsupial", "🦘"),
        Triple("platypus", "Ornitorrinco", "🦫"),
        Triple("echidna", "Equidna", "🦔"),
        Triple("meerkat", "Suricata", "🐾"),
        Triple("mongoose", "Mangosta", "🐾"),
        Triple("bat", "Murciélago", "🦇"),
        // Mamíferos marinos
        Triple("whale", "Ballena / Orca", "🐋"),
        Triple("dolphin", "Delfín", "🐬"),
        Triple("seal", "Foca / Lobo marino", "🦭"),
        Triple("manatee", "Manatí / Dugongo", "🐋"),
        // Aves
        Triple("bird", "Ave", "🐦"),
        Triple("parrot", "Loro / Guacamaya", "🦜"),
        Triple("owl", "Búho / Lechuza", "🦉"),
        Triple("eagle", "Águila / Halcón", "🦅"),
        Triple("vulture", "Buitre / Cóndor", "🦅"),
        Triple("penguin", "Pingüino", "🐧"),
        Triple("flamingo", "Flamenco", "🦩"),
        Triple("swan", "Cisne", "🦢"),
        Triple("peacock", "Pavo real", "🦚"),
        Triple("ostrich", "Avestruz", "🐦"),
        Triple("hummingbird", "Colibrí", "🐦"),
        Triple("toucan", "Tucán", "🐦"),
        Triple("pelican", "Pelícano", "🐦"),
        Triple("pigeon", "Paloma", "🕊️"),
        // Reptiles y anfibios
        Triple("reptile", "Reptil", "🦎"),
        Triple("snake", "Serpiente", "🐍"),
        Triple("lizard", "Lagarto / Lagartija", "🦎"),
        Triple("iguana", "Iguana", "🦎"),
        Triple("chameleon", "Camaleón", "🦎"),
        Triple("crocodile", "Cocodrilo / Caimán", "🐊"),
        Triple("turtle", "Tortuga", "🐢"),
        Triple("dinosaur", "Dinosaurio", "🦖"),
        Triple("frog", "Rana / Sapo", "🐸"),
        Triple("salamander", "Salamandra / Ajolote", "🦎"),
        Triple("amphibian", "Anfibio", "🐸"),
        // Peces
        Triple("fish", "Pez", "🐟"),
        Triple("shark", "Tiburón", "🦈"),
        Triple("ray", "Raya / Mantarraya", "🐟"),
        // Insectos y arácnidos
        Triple("insect", "Insecto", "🐛"),
        Triple("butterfly", "Mariposa", "🦋"),
        Triple("moth", "Polilla", "🦋"),
        Triple("bee", "Abeja", "🐝"),
        Triple("ant", "Hormiga", "🐜"),
        Triple("ladybug", "Mariquita", "🐞"),
        Triple("beetle", "Escarabajo", "🪲"),
        Triple("dragonfly", "Libélula", "🪰"),
        Triple("grasshopper", "Saltamontes / Grillo", "🦗"),
        Triple("cockroach", "Cucaracha", "🪳"),
        Triple("mantis", "Mantis", "🦗"),
        Triple("fly", "Mosca", "🪰"),
        Triple("cicada", "Cigarra", "🦗"),
        Triple("caterpillar", "Oruga", "🐛"),
        Triple("spider", "Araña", "🕷️"),
        Triple("scorpion", "Escorpión", "🦂"),
        Triple("tick", "Garrapata", "🕷️"),
        Triple("centipede", "Ciempiés", "🐛"),
        // Otros invertebrados
        Triple("crab", "Cangrejo", "🦀"),
        Triple("lobster", "Langosta", "🦞"),
        Triple("shrimp", "Camarón", "🦐"),
        Triple("crustacean", "Crustáceo", "🦐"),
        Triple("isopod", "Cochinilla / Isópodo", "🐛"),
        Triple("trilobite", "Trilobites (fósil)", "🦴"),
        Triple("snail", "Caracol / Babosa", "🐌"),
        Triple("octopus", "Pulpo / Nautilo", "🐙"),
        Triple("squid", "Calamar", "🦑"),
        Triple("mollusk", "Molusco", "🐚"),
        Triple("jellyfish", "Medusa", "🪼"),
        Triple("sea anemone", "Anémona de mar", "🪸"),
        Triple("coral", "Coral", "🪸"),
        Triple("starfish", "Estrella de mar", "⭐"),
        Triple("sea urchin", "Erizo de mar", "🦔"),
        Triple("sea cucumber", "Pepino de mar", "🥒"),
        Triple("worm", "Gusano", "🪱"),
        // Genéricos: indican "hay un animal" sin decir cuál
        Triple("animal", "Animal", "🐾"),
        Triple("pet", "Mascota", "🐾"),
        Triple("mammal", "Mamífero", "🐾"),
        Triple("wildlife", "Fauna silvestre", "🐾"),
    ).associate { (clave, nombre, emoji) -> clave to AnimalInfo(clave, nombre, emoji) }

    /** Sinónimos de etiquetas del modelo general de ML Kit -> clave del catálogo. */
    private val alias = mapOf(
        "puppy" to "dog", "kitten" to "cat", "himalayan" to "cat",
        "cow" to "cattle", "bull" to "cattle", "ox" to "cattle", "calf" to "cattle",
        "pony" to "horse", "mule" to "donkey", "rooster" to "chicken", "hen" to "chicken",
        "lamb" to "sheep", "alpaca" to "llama", "rat" to "mouse",
        "gorilla" to "ape", "chimpanzee" to "ape", "orangutan" to "ape", "primate" to "monkey",
        "polar bear" to "bear", "brown bear" to "bear", "lynx" to "wild cat",
        "sea lion" to "seal", "harbor seal" to "seal", "orca" to "whale",
        "falcon" to "eagle", "hawk" to "eagle", "raven" to "bird", "magpie" to "bird",
        "sparrow" to "bird", "canary" to "bird", "woodpecker" to "bird", "blue jay" to "bird",
        "tortoise" to "turtle", "sea turtle" to "turtle", "alligator" to "crocodile",
        "toad" to "frog", "goldfish" to "fish", "seahorse" to "fish", "rays and skates" to "ray",
        "moths and butterflies" to "butterfly", "lady bug" to "ladybug", "larva" to "caterpillar",
        "slug" to "snail", "oyster" to "mollusk", "clam" to "mollusk", "shellfish" to "mollusk",
    )

    /** Subtipo -> grupo amplio (p. ej. "parrot" es un "bird"). */
    private val grupos: Map<String, String> = buildMap {
        listOf("parrot", "owl", "eagle", "vulture", "penguin", "flamingo", "swan", "peacock", "ostrich",
            "hummingbird", "toucan", "pelican", "pigeon", "chicken", "duck", "goose", "turkey")
            .forEach { put(it, "bird") }
        listOf("butterfly", "moth", "bee", "ant", "ladybug", "beetle", "dragonfly", "grasshopper",
            "cockroach", "mantis", "fly", "cicada", "caterpillar")
            .forEach { put(it, "insect") }
        listOf("snake", "lizard", "iguana", "chameleon", "crocodile", "turtle", "dinosaur")
            .forEach { put(it, "reptile") }
        listOf("frog", "salamander").forEach { put(it, "amphibian") }
        listOf("shark", "ray").forEach { put(it, "fish") }
        listOf("crab", "lobster", "shrimp", "isopod").forEach { put(it, "crustacean") }
        listOf("snail", "octopus", "squid").forEach { put(it, "mollusk") }
        listOf("hamster", "guinea pig", "squirrel", "mouse", "beaver", "marmot", "porcupine")
            .forEach { put(it, "rodent") }
        listOf("kangaroo", "koala", "wombat").forEach { put(it, "marsupial") }
    }

    /** Grupo amplio al que pertenece una clave, o null. */
    fun grupo(clave: String): String? = grupos[clave]

    /** Claves genéricas: dicen que hay un animal, pero no de qué tipo. */
    private val genericas = setOf("animal", "pet", "mammal", "wildlife")

    /** Devuelve el animal para una etiqueta o clave, o null si no es un animal. */
    fun buscar(etiqueta: String): AnimalInfo? {
        val c = etiqueta.trim().lowercase()
        return tipos[alias[c] ?: c]
    }

    fun esGenerica(info: AnimalInfo): Boolean = info.clave in genericas
}
