package com.calc.expense

/**
 * 도감 글의 영어·스페인어. 한국어 원문은 [Plant]·[Shelf] 에 그대로 있고, 여기에는 번역만 둔다 —
 * 꽃 하나에 다섯 줄씩 세 언어를 enum 안에 늘어놓으면 원문을 읽을 수가 없다.
 *
 * 꽃 하나 = [이름, 뜻, 이야기, 조건, 짧은 조건]. 선반 하나 = [제목, 화분 이름].
 * 빠진 것이 있으면 한국어 원문으로 떨어진다.
 */
object DogamText {

    fun plant(plant: Plant, index: Int, ko: String): String = pick(PLANTS[plant.key], index, ko)

    fun shelf(shelf: Shelf, index: Int, ko: String): String = pick(SHELVES[shelf.name], index, ko)

    private fun pick(entry: Pair<List<String>, List<String>>?, index: Int, ko: String): String {
        if (entry == null) return ko
        val list: List<String> = when (L10n.lang) {
            Lang.KO -> return ko
            Lang.EN -> entry.first
            Lang.ES -> entry.second
        }
        return list.getOrNull(index) ?: ko
    }

    private val SHELVES: Map<String, Pair<List<String>, List<String>>> = mapOf(
        "RECORD" to (listOf("Logging habit", "Notebook pot") to listOf("Hábito de anotar", "Maceta cuaderno")),
        "PILE" to (listOf("Days piled up", "Brick pot") to listOf("Días acumulados", "Maceta de ladrillo")),
        "GRADE_S" to (listOf("S grade", "Star pot") to listOf("Nota S", "Maceta estrella")),
        "KEEP" to (listOf("Staying on budget", "Calendar pot") to listOf("Dentro del presupuesto", "Maceta calendario")),
        "COMEBACK" to (listOf("Bouncing back", "Clearing-sky pot") to listOf("Volver a levantarse", "Maceta de cielo despejado")),
        "NO_SPEND" to (listOf("No-spend", "Night-sky pot") to listOf("Sin gastos", "Maceta de cielo nocturno")),
        "PERIOD" to (listOf("Week · cycle", "Rainbow band · coin pot") to listOf("Semana · ciclo", "Franja arcoíris · maceta de monedas")),
        "TIDY" to (listOf("Tidying up", "Glass jar · piggy bank") to listOf("Orden", "Tarro de cristal · hucha")),
    )

    private val PLANTS: Map<String, Pair<List<String>, List<String>>> = mapOf(
        "sprout" to (
            listOf("Sprout", "Beginning", "You logged for the first time", "The first day you log spending", "First entry") to
                listOf("Brote", "Comienzo", "Anotaste por primera vez", "El primer día que anotas un gasto", "Primer gasto")
            ),
        "rosemary" to (
            listOf("Rosemary", "Remembrance", "You logged every day for a week", "Log every day for 7 days in a row", "7 days in a row") to
                listOf("Romero", "Recuerdo", "Anotaste todos los días durante una semana", "Anota todos los días durante 7 días seguidos", "7 días seguidos")
            ),
        "forget_me_not" to (
            listOf("Forget-me-not", "Forget me not", "Not a single day forgotten for a month", "Log every day for 30 days in a row", "30 days in a row") to
                listOf("Nomeolvides", "No me olvides", "Ni un día olvidado en un mes", "Anota todos los días durante 30 días seguidos", "30 días seguidos")
            ),
        "violet" to (
            listOf("Violet", "Diligence", "You've logged on 30 days", "30 logged days in total (gaps allowed)", "30 logged days") to
                listOf("Violeta", "Constancia", "Llevas 30 días anotados", "30 días anotados en total (aunque haya huecos)", "30 días anotados")
            ),
        "cosmos" to (
            listOf("Cosmos", "Harmony", "Spending and logging became second nature", "100 logged days in total (gaps allowed)", "100 logged days") to
                listOf("Cosmos", "Armonía", "Gastar y anotar ya es natural", "100 días anotados en total (aunque haya huecos)", "100 días anotados")
            ),
        "edelweiss" to (
            listOf("Edelweiss", "Precious memories", "You've logged on a year's worth of days", "365 logged days in total (gaps allowed)", "365 logged days") to
                listOf("Edelweiss", "Recuerdos preciados", "Llevas un año de días anotados", "365 días anotados en total (aunque haya huecos)", "365 días anotados")
            ),
        "daisy" to (
            listOf("Daisy", "Hope", "You got your first S", "Get a daily S grade for the first time", "First S") to
                listOf("Margarita", "Esperanza", "Conseguiste tu primera S", "Consigue por primera vez una nota diaria S", "Primera S")
            ),
        "sunflower" to (
            listOf("Sunflower", "Turns to follow the sun", "S keeps coming back", "Get a daily S grade 5 times", "S × 5") to
                listOf("Girasol", "Sigue al sol", "La S vuelve una y otra vez", "Consigue la nota diaria S 5 veces", "S × 5")
            ),
        "laurel" to (
            listOf("Laurel wreath", "Victory", "You've had twenty S grades", "Get a daily S grade 20 times", "S × 20") to
                listOf("Corona de laurel", "Victoria", "Llevas veinte notas S", "Consigue la nota diaria S 20 veces", "S × 20")
            ),
        "clover" to (
            listOf(
                "Three-leaf clover", "Happiness",
                "Three days in a row within your daily amount",
                "Stay within the daily amount 3 days in a row", "3 days on budget",
            ) to listOf(
                "Trébol de tres hojas", "Felicidad",
                "Tres días seguidos dentro de tu cantidad diaria",
                "Mantente dentro de la cantidad diaria 3 días seguidos", "3 días en presupuesto",
            )
            ),
        "plum" to (
            listOf("Plum blossom", "Patience", "It endures the cold and blooms first", "Stay within the daily amount 7 days in a row", "7 days on budget") to
                listOf("Flor de ciruelo", "Paciencia", "Aguanta el frío y florece primero", "Mantente dentro de la cantidad diaria 7 días seguidos", "7 días en presupuesto")
            ),
        "bamboo" to (
            listOf("Bamboo", "Integrity", "Two weeks in a row within your daily amount", "Stay within the daily amount 14 days in a row", "14 days on budget") to
                listOf("Bambú", "Integridad", "Dos semanas seguidas dentro de tu cantidad diaria", "Mantente dentro de la cantidad diaria 14 días seguidos", "14 días en presupuesto")
            ),
        "chamomile" to (
            listOf(
                "Chamomile", "Strength in adversity", "After a big day, you got back on track",
                "Stay within the daily amount the day after going over", "Back on track next day",
            ) to listOf(
                "Manzanilla", "Fortaleza ante la adversidad", "Tras un día de mucho gasto, volviste al plan",
                "Mantente dentro de la cantidad diaria el día después de pasarte", "Recuperado al día siguiente",
            )
            ),
        "snowdrop" to (
            listOf("Snowdrop", "Hope", "A flower that blooms even in snow", "Get back on track after going over, 5 times", "Back on track × 5") to
                listOf("Campanilla de invierno", "Esperanza", "Una flor que florece incluso en la nieve", "Vuelve al plan tras pasarte, 5 veces", "Recuperado × 5")
            ),
        "hibiscus" to (
            listOf("Rose of Sharon", "Perseverance", "It blooms, fades and blooms again", "Get back on track after going over, 15 times", "Back on track × 15") to
                listOf("Rosa de Siria", "Perseverancia", "Florece, se marchita y vuelve a florecer", "Vuelve al plan tras pasarte, 15 veces", "Recuperado × 15")
            ),
        "lavender" to (
            listOf("Lavender", "Silence", "A whole day without spending", "End a day with ₩0 in your personal wallet", "No-spend day") to
                listOf("Lavanda", "Silencio", "Un día entero sin gastar", "Termina un día con ₩0 en tu cartera personal", "Día sin gastos")
            ),
        "lily_of_the_valley" to (
            listOf("Lily of the valley", "Happiness will surely come", "Five no-spend days", "5 no-spend days", "No-spend × 5") to
                listOf("Lirio de los valles", "La felicidad llegará", "Cinco días sin gastos", "5 días sin gastos", "Sin gastos × 5")
            ),
        "evening_primrose" to (
            listOf("Evening primrose", "Waiting", "Twenty no-spend days", "20 no-spend days", "No-spend × 20") to
                listOf("Onagra", "Espera", "Veinte días sin gastos", "20 días sin gastos", "Sin gastos × 20")
            ),
        "marigold" to (
            listOf(
                "Marigold", "Happiness that is bound to come", "A whole week within budget",
                "Keep a Monday–Sunday week within 7 days' worth", "Keep a week",
            ) to listOf(
                "Caléndula", "La felicidad que sin duda llegará", "Una semana entera dentro del presupuesto",
                "Mantén una semana de lunes a domingo dentro de 7 días de presupuesto", "Cumplir una semana",
            )
            ),
        "olive" to (
            listOf("Olive", "Peace", "Four weeks within budget", "Keep a week 4 times", "Week × 4") to
                listOf("Olivo", "Paz", "Cuatro semanas dentro del presupuesto", "Cumple una semana 4 veces", "Semana × 4")
            ),
        "money_tree" to (
            listOf(
                "Money plant", "Prosperity", "You finished a cycle within budget",
                "Stay within budget from payday to the day before the next payday", "Keep a cycle",
            ) to listOf(
                "Árbol del dinero", "Prosperidad", "Terminaste un ciclo dentro del presupuesto",
                "Mantente en el presupuesto desde el cobro hasta el día antes del siguiente", "Cumplir un ciclo",
            )
            ),
        "mint" to (
            listOf("Mint", "Freshness", "Neatly sorted with nothing uncategorized", "0 uncategorized in a cycle with more than 10 entries", "0 uncategorized") to
                listOf("Menta", "Frescura", "Todo ordenado, nada sin categoría", "0 sin categoría en un ciclo con más de 10 gastos", "0 sin categoría")
            ),
        "monstera" to (
            listOf(
                "Monstera", "Leaves with holes", "You sorted out what goes out every month",
                "Set your fixed costs", "Set fixed costs",
            ) to listOf(
                "Costilla de Adán", "Hojas con agujeros", "Ordenaste lo que sale cada mes",
                "Define tus gastos fijos", "Definir gastos fijos",
            )
            ),
        "babys_breath" to (
            listOf(
                "Baby's breath", "A pure heart", "Three cycles in a row, neatly sorted",
                "3 cycles with more than 10 entries and 0 uncategorized", "0 uncategorized × 3",
            ) to listOf(
                "Paniculata", "Corazón puro", "Tres ciclos seguidos, todo ordenado",
                "3 ciclos con más de 10 gastos y 0 sin categoría", "0 sin categoría × 3",
            )
            ),
    )
}
