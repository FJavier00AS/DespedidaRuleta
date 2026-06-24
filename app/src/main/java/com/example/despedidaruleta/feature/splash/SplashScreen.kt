package com.example.despedidaruleta.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.despedidaruleta.core.designsystem.component.LoadingState
import com.example.despedidaruleta.core.designsystem.component.VegasBackground
import com.example.despedidaruleta.core.designsystem.theme.DespedidaRuletaTheme
import com.example.despedidaruleta.core.designsystem.theme.VegasColors

@Composable
fun SplashScreen() {
    VegasBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Despedida Ruleta",
                style = MaterialTheme.typography.displayLarge,
                color = VegasColors.Gold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Casino privado para una noche memorable",
                style = MaterialTheme.typography.bodyLarge,
                color = VegasColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
            )
            LoadingState(message = "Comprobando sesion")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    DespedidaRuletaTheme { SplashScreen() }
}
