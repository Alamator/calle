package pl.robert.calle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import pl.robert.calle.ui.CalleViewModel
import pl.robert.calle.ui.MapScreen
import pl.robert.calle.ui.theme.CalleTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CalleViewModel by viewModels {
        CalleViewModel.factory(application as CalleApp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalleTheme {
                MapScreen(viewModel = viewModel)
            }
        }
    }
}
