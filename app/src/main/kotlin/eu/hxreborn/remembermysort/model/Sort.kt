package eu.hxreborn.remembermysort.model

internal object Sort {
    // TODO: Probablt not needed 
    const val DIRECTION_ASC = 1
    const val DIRECTION_DESC = 2
}

internal data class SortPreference(
    val position: Int,
    val dimId: Int, // TODO: Check if its worth to store it 
    val direction: Int,
) {
    companion object {
        // TODO: Probably not needed 
        val DEFAULT = SortPreference(position = -1, dimId = -1, direction = Sort.DIRECTION_DESC)
    }
}
