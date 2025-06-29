package com.hackathon.offlinewallet.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

@Composable
fun ReceiveMoneyScreen(navController: NavController, viewModel: WalletViewModel) {
    val wallet by viewModel.wallet.collectAsState()
    val qrBitmap = remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(wallet) {
        wallet?.id?.let { walletId ->
            qrBitmap.value = generateQrCode(walletId)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Receive Money", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Show this QR code to receive money")
        Spacer(modifier = Modifier.height(16.dp))
        if (qrBitmap.value != null) {
            Image(
                bitmap = qrBitmap.value!!.asImageBitmap(),
                contentDescription = "Wallet QR Code",
                modifier = Modifier.size(200.dp)
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Wallet ID: ${wallet?.id ?: "Loading..."}")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}

private fun generateQrCode(text: String): Bitmap {
    val width = 200
    val height = 200
    val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return bitmap
}