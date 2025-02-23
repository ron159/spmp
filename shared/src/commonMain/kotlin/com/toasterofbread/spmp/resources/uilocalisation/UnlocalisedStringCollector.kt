package com.toasterofbread.spmp.resources.uilocalisation

import androidx.compose.runtime.mutableStateListOf

class UnlocalisedStringCollector {
    data class UnlocalisedString(
        val type: String, 
        val key: String?,
        val source_language: String?
    ) {
        val stacktrace: List<StackTraceElement> = Throwable().stackTrace.takeLast(5)
        
        companion object {
            fun fromLocalised(string: LocalisedString) =
                UnlocalisedString(string.getType().name, string.serialise(), null)
        }
    }
    
    private val unlocalised_strings: MutableList<UnlocalisedString> = mutableStateListOf()

    fun add(string: UnlocalisedString): Boolean {
        synchronized(unlocalised_strings) {
            for (item in unlocalised_strings) {
                if (item == string) {
                    return false
                }
            }

            unlocalised_strings.add(string)
            return true
        }
    }
    fun add(string: LocalisedString) = add(UnlocalisedString.fromLocalised(string))

    fun getStrings(): List<UnlocalisedString> = unlocalised_strings
}
