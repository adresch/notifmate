package com.notifmate.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AskPermissionViewmodel : ViewModel() {
    private val _permissionsMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionsMap: StateFlow<Map<String, Boolean>> get() = _permissionsMap.asStateFlow()

    fun updatePermissionsMap(newPermissionsMap: Map<String, Boolean>) {
        _permissionsMap.update {
            newPermissionsMap
        }
        _permissionsMap.value = newPermissionsMap
    }

    // Function to get value from permissions map

}