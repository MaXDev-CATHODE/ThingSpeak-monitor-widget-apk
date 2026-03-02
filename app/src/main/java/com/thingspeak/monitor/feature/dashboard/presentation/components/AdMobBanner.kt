package com.thingspeak.monitor.feature.dashboard.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

import androidx.compose.runtime.Composable

/**
 * A Composable wrapper for Google AdMob banner.
 */
@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-3940256099942544/6300978111" // Test ID
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isVisible = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

    if (isVisible.value) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "Advertisement",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            var adViewInstance: com.google.android.gms.ads.AdView? = null

            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> adViewInstance?.resume()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> adViewInstance?.pause()
                        androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> adViewInstance?.destroy()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    com.google.android.gms.ads.AdView(context).apply {
                        setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                        this.adUnitId = adUnitId
                        
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                super.onAdFailedToLoad(error)
                                isVisible.value = false
                            }
                        }

                        loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
                        adViewInstance = this
                    }
                },
                update = { /* Updates handled via DisposableEffect */ },
                onRelease = { adView ->
                    adView.destroy()
                    adViewInstance = null
                }
            )
        }
    }
}
