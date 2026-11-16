package com.anas.ufc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anas.ufc.ui.CardScreen
import com.anas.ufc.ui.UfcTheme
import com.anas.ufc.ui.UfcViewModel
import com.anas.ufc.ui.UfcViewModelFactory
import com.anas.ufc.widget.UfcRefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UfcRefreshWorker.scheduleDaily(applicationContext)
        setContent {
            UfcTheme {
                val viewModel: UfcViewModel = viewModel(factory = UfcViewModelFactory(application))
                val state by viewModel.state.collectAsStateWithLifecycle()
                BackHandler(state.showCredits, viewModel::closeCredits)
                CardScreen(state, viewModel)
            }
        }
    }
}
