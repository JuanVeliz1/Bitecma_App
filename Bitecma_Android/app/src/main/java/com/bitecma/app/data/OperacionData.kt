package com.bitecma.app.data

data class SectorAmerb(
    val id: Int,
    val nombre: String,
    val region: Int
)

data class Opa(
    val id: Int,
    val nombre: String,
    val nombreCorto: String,
    val region: Int
)

object OperacionData {
    val sectoresAmerb = listOf(
        SectorAmerb(1, "PUNTA SEREMEÑO", 1),
        SectorAmerb(2, "AMARILLO", 1),
        SectorAmerb(3, "PISAGUA", 1),
        SectorAmerb(4, "CARAMUCHO SECTOR C", 1)
    )

    val opas = listOf(
        Opa(1, "COOPERATIVA DE PESCADORES ARTESANALES Y ACUICULTORES DE CALETA CHILENA", "PUNTA SEREMEÑO", 1),
        Opa(2, "S.T.I DEL MAR Nº 2 DE PISAGUA", "AMARILLO", 1),
        Opa(3, "S.T.I. BUZOS MARISCADORES DE CALETA PISAGUA", "PISAGUA", 1)
    )

    val caletasByRegion = mapOf(
        1 to listOf("Cáñamo", "Caramucho", "Cavancha", "Chanavaya (Pabellón de Pica)", "Chanavayita", "Chipana", "Los Verdes", "Patache", "Pisagua", "Playa Blanca", "Puerto de Iquique", "Río Seco", "Riquelme", "San Marcos"),
        2 to listOf("Abtao (Juan López)", "Antofagasta", "Blanco Encalada", "Caleta Buena (Los Chinos)", "Cifuncho", "Cobija", "Coloso", "Constitución (I. Sta. María)", "El Bote", "El Cobre", "El Fierro", "El Lagarto", "Gualaguala", "Hornitos (Hornos)", "Huachán", "Indígena", "La Chimba", "La Colorada", "Las Tórtolas", "Mejillones", "Michilla", "Paposo", "Paquica (La Cuchara)", "Punta Arenas", "Punta Atala", "Taltal", "Tocopilla", "Urcu (Urco)"),
        3 to listOf("Agua de Zorra", "Angosta", "Bahía Salada", "Barranquilla", "Caldera", "Carrizal Bajo", "Carrizalillo", "Chañaral (San Pedro)", "Chañaral de Aceituno", "Chasco", "El Cisne", "El Sarco", "Flamenco", "Huasco", "La Reina", "Loreto", "Los Bronces", "Los Burros Sur", "Los Pozos", "Los Toyos", "Maldonado", "Obispito", "Pajonales", "Pan de Azúcar", "Puerto Viejo", "Punta Froden", "Punta Lobos", "Torres del Inca", "Totoral Bajo", "Zenteno"),
        4 to listOf("Cascabeles", "Chigualoco", "Chungungo", "Coquimbo", "El Apolillado", "El Sauce", "Guanaqueros", "Guayacán", "Hornos", "Huentelauquén", "La Cebada", "Las Conchas", "Limarí", "Los Choros (Choreadero)", "Maitencillo", "Peñuelas", "Pichidangui", "Playa Chica de La Herradura", "Puerto Aldea (Hornilla)", "Puerto Manso", "Puerto Oscuro", "Punta Choros A (San Agustín)", "Punta Choros B (Los Corrales)", "San Pedro, La Serena", "San Pedro, Los Vilos", "Sierra", "Talca", "Talcaruca", "Talquilla", "Tongoy", "Totoral", "Totoralillo Centro (Totoralillo)", "Totoralillo Norte", "Totoralillo Sur (Totoralillo)"),
        5 to listOf("Alejandro Selkirk", "Algarrobo", "Bahía Cumberland (isla Robinson Crusoe)", "Cartagena", "Desembocadura Río Maipo", "El Embarcadero", "El Manzano", "El Membrillo", "El Quisco", "Hanga Piko", "Hanga Roa", "Higuerilla", "Horcón", "Hotu Iti", "Laguna Verde", "Laperouse (Hanga Hoonu)", "Las Cruces", "Las Cujas-Cachagua", "Ligua", "Loncura", "Los Molles", "Maitencillo", "Montemar", "Papagallo", "Papudo", "Pichicuy", "Playa Mostazal", "Polcura", "Portales", "Puertecito", "Quintay", "San Pedro de Concón", "San Pedro-Pacheco Altamirano", "Sudamericana", "Vaihu", "Ventanas", "Zapallar"),
        6 to listOf("Boca de Rapel", "Bucalemu", "Cáhuil", "Chorrillos", "Matanzas", "Pichilemu", "Puertecillo", "Topocalma"),
        7 to listOf("Boyeruca", "Cardonal", "Constitución", "Curanipe", "Duao", "Iloca", "La Pesca", "La Trinchera", "Lipimávida", "Llico", "Loanco", "Los Pellines", "Maguillines", "Pelluhue", "Putu", "Río Maule"),
        8 to listOf("Antiquina", "Arauco", "Boca Sur", "Burca", "Caleta Lota Bajo", "Candelaria", "Cantera", "Casa de Piedra - Tirúa Sur 7", "Cerro Verde", "Chivilingo", "Chome", "Cocholgüe Caleta Chica", "Cocholgüe Caleta Grande", "Colcura", "Coliumo", "Comillahue - Tirúa Sur 6", "Dichato", "El Blanco", "El Morro", "El Soldado", "Huentelolén", "Infiernillo", "Islote del Trabajo (isla Mocha)", "La Calera (isla Mocha)", "La Cata", "La Conchilla", "La Hacienda (isla Mocha)", "Laraquete", "Las Misiones - Tirúa Sur 1", "Las Peñas", "Lebu", "Lenga", "Lirquén", "Llico", "Lo Rojas", "Locobe", "Los Bagres", "Los Cazones (isla Mocha)", "Los Chilcos - Tirúa Sur 5", "Los Piures", "Matadero (isla Mocha)", "Maule", "Millonhue", "Montecristo", "Morguilla", "Pangue", "Penco", "Peroné", "Playa Lotilla", "Playa Negra", "Pueblo Hundido", "Puente de Tierra - Tirúa Sur 2", "Puerto Inglés", "Puerto Norte (isla Santa María)", "Puerto Nuevo", "Puerto Sur (isla Santa María)", "Punta Astorga", "Punta Lavapié", "Purema", "Quiapo", "Quichiuto", "Quidico", "Rocuant", "Rumena", "San Vicente", "Talcahuano", "Tirúa", "Tomé", "Tranaquepe", "Tranicura A - Tirúa Sur 3", "Tranicura B - Tirúa Sur 4", "Tubul", "Tumbes", "Villarrica", "Villarrica (Ránquil)", "Yani"),
        9 to listOf("Boca Budi", "La Barra", "Lago Budi-Nahuelhuapi", "Los Pinos", "Nehuentué", "Puerto Domínguez", "Puerto Saavedra (El Huilque)", "Queule", "Romopulli"),
        10 to listOf("Achao", "Aguantao", "Aituy", "Alao", "Alfaro (isla Huar)", "Alqui (isla Tranqui)", "Amortajado", "Amortajado Sector A", "Anahuac", "Ancud", "Angelmó", "Añihué (grupo Chauques)", "Apiao", "Astillero", "Auchac", "Auchemó", "Auchó", "Aulen", "Aulín (grupo Chauques)", "Ayacara", "Bahía Huelmo", "Bahía Ilque", "Bahía Mansa", "Bajo Palena", "Blandchard", "Buill", "Caguach", "Caicura", "Caipulli", "Calbuco-La Vega", "Caleta Gutiérrez", "Caleta Parga", "Caleta Poyo", "Calle", "Candelaria", "Carelmapu", "Cariquilda", "Casa de Pesca", "Cascajal", "Castro", "Catrumán", "Caucahué", "Caulín", "Chacao", "Chaicas", "Chaicura", "Chaiguaco", "Chaitén", "Chana", "Chanhué", "Chauchil", "Chaulinec", "Chauquear (isla Puluqui)", "Chayahué", "Chelín", "Cheñiao (grupo Chauques)", "Chepu", "Chequián", "Chinquihue", "Cholgo", "Cholhue (isla Huar)", "Chonchi", "Chope (isla Puluqui)", "Chuit", "Chulchuy", "Chulín", "Chumeldén", "Cochamó", "Coihuín", "Colaco", "Compu", "Cóndor", "Contao", "Coñimó", "Cuberos", "Cucao", "Curaco de Vélez", "Curanué", "Dalcahue", "Duhatao", "El Dique", "El Estero (isla Maillen)", "El Manzano", "El Rosario", "Estaquilla", "Faro Corona", "Fátima", "Guabún", "Guapilacuy", "Hornopirén", "Hualaihué Estero", "Hueihue", "Hueldén", "Huellelhue", "Huequi", "Huicha", "Huildad", "Imerquiña/Nayahué", "Isla Acui", "Isla Cailín", "Isla Chaullín", "Isla Huapi-Abtao", "Isla Queullín", "Isla Tabón", "Isla Tac", "La Arena", "La Pasada", "Lamecura", "Lechagua", "Lenca", "Lepihué", "Lin Lin", "Linao", "Linguar", "Liucura", "Llanchid", "Lleguimán", "Lliuco", "Lolcura", "Los Chonos", "Los Toros", "Loyola", "Machil (isla Puluqui)", "Maicolpué", "Manao", "Manquemapu", "Manzano", "Mañihueico", "Mapué (isla Tranqui)", "Mar Brava", "Maullín", "Mechuque (grupo Chauques)", "Metri", "Meulín", "Milagro", "Muelle Toledo", "Nal", "Palqui", "Panitao Bajo", "Pargua", "Perhue (isla Puluqui)", "Pichicolo", "Pichipelluco", "Piedra Azul", "Pilluco", "Pollollo (isla Puluqui)", "Pucatrihue", "Puerto Montt", "Puerto Octay", "Puerto Varas", "Puqueldón", "Quemchi", "Quetalmahue", "Queulín (isla Queullín)", "Quicaví", "Quinchao", "Quinuad (isla Huar)", "Quonchi", "Rampa de Chonchi", "Reloncaví", "Rolecha", "San Agustín", "San José", "San Juan", "San Rafael", "Santa Bárbara", "Seno Reloncaví", "Tenaún", "Tentelhue", "Tercera Caleta", "Voigue"),
        11 to listOf("Caleta Andrade (isla Las Huichas)", "Caleta Puerto Gala", "Estero Copa (isla Las Huichas)", "Estero Gato", "Grupo Gala", "Isla Costa", "Melimoyu", "Playas Blancas", "Puerto Aguirre (isla Las Huichas)", "Puerto Americano", "Puerto Aysén", "Puerto Chacabuco", "Puerto Cisnes", "Puerto Gaviota", "Puerto Melinka", "Puerto Puyuguapi", "Puerto Raúl Marín Balmaceda", "Repollal", "Santo Domingo", "Tortel"),
        12 to listOf("Bahía Chilota", "Bahía Mansa", "Barranco Amarillo", "Los Pinos", "Porvenir", "Puerto Edén", "Puerto Natales", "Puerto Toro", "Puerto Williams", "Punta Arenas (muelle fiscal)", "Punta Carrera", "Río Canelo"),
        14 to listOf("Amargos", "Bahía San Juan", "Bonifacio", "Chaihuín", "Chan-chan", "Corral", "Corral Bajo", "El Piojo", "Huape", "Hueicolla", "Huiro", "Isla del Rey", "La Aguada", "La Misión", "Lamehuapi", "Los Molinos", "Maiquillahue", "Mancera", "Mehuín", "Mississipi", "Niebla", "San Carlos", "San Ignacio", "Tres Espinos", "Valdivia"),
        15 to listOf("Arica", "Camarones"),
        16 to listOf("Boca Itata", "Buchupureo", "Cobquecura", "Mela", "Perales", "Taucú")
    )

    val tiposOrganizacion = listOf("STI", "ASOC", "OTRO")
}
