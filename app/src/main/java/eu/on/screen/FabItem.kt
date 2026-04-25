package eu.on.screen

data class FabItem(
    val id: String,
    var iconRes: Int,
    val contentDescRes: Int,
    var isActive: Boolean = false
)
