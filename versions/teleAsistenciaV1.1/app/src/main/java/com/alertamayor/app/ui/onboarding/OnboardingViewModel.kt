package com.alertamayor.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.domain.model.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone

    init {
        viewModelScope.launch {
            _onboardingDone.value = settings.onboardingDone.first()
        }
    }

    fun selectMode(mode: AppMode) {
        viewModelScope.launch {
            settings.saveMode(mode)
            settings.setOnboardingDone(true)
            _onboardingDone.value = true
        }
    }
}
