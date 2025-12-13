package eu.hxreborn.remembermysort.model

internal object Sort {
    const val DIRECTION_ASC = 1
    const val DIRECTION_DESC = 2
}

internal data class SortPreference(
    val position: Int,
    val dimId: Int,
    val direction: Int,
) {
    companion object {
        val DEFAULT = SortPreference(position = -1, dimId = -1, direction = Sort.DIRECTION_DESC)
    }
}
