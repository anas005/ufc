package com.anas.ufc.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anas.ufc.data.CacheSnapshot
import com.anas.ufc.data.CardSection
import com.anas.ufc.data.RefreshStatus
import com.anas.ufc.data.UfcRepository
import com.anas.ufc.data.UfcRepositoryProvider
import com.anas.ufc.widget.UfcRefreshWorker
import com.anas.ufc.widget.UfcWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UfcUiState(
    val snapshot: CacheSnapshot = CacheSnapshot(),
    val selectedSection: CardSection = CardSection.MAIN,
    val selectedEventSlug: String? = null,
    val isLoading: Boolean = true,
    val showCredits: Boolean = false,
)

class UfcViewModel(
    application: Application,
    private val repository: UfcRepository = UfcRepositoryProvider.get(application),
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(UfcUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.cache.collect { cache ->
                mutableState.update { current ->
                    val selected = current.selectedEventSlug
                        ?.takeIf { slug -> cache.events.any { it.slug == slug } }
                        ?: cache.selectedEventSlug?.takeIf { slug -> cache.events.any { it.slug == slug } }
                        ?: cache.events.firstOrNull()?.slug
                    current.copy(snapshot = cache, selectedEventSlug = selected, isLoading = false)
                }
            }
        }
    }

    fun selectEvent(slug: String) {
        mutableState.update { it.copy(selectedEventSlug = slug) }
        viewModelScope.launch {
            repository.selectEvent(slug)
            UfcWidget().updateAll(getApplication())
            val selected = repository.snapshot().events.firstOrNull { it.slug == slug }
            if (selected == null || selected.card.values.all { it.isEmpty() }) {
                repository.setRefreshStatus(RefreshStatus.REFRESHING)
                UfcWidget().updateAll(getApplication())
                UfcRefreshWorker.enqueueEvent(getApplication(), slug)
            }
        }
    }

    fun selectSection(section: CardSection) {
        mutableState.update { it.copy(selectedSection = section) }
    }

    fun refresh() {
        viewModelScope.launch {
            repository.setRefreshStatus(RefreshStatus.REFRESHING)
            UfcRefreshWorker.enqueueManual(getApplication())
        }
    }

    fun openCredits() {
        mutableState.update { it.copy(showCredits = true) }
    }

    fun closeCredits() {
        mutableState.update { it.copy(showCredits = false) }
    }
}

class UfcViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(UfcViewModel::class.java))
        return modelClass.cast(UfcViewModel(application))
    }
}
