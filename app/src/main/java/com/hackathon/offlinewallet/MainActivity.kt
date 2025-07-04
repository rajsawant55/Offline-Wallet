package com.hackathon.offlinewallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Configuration



import com.hackathon.offlinewallet.ui.*
import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var workManagerConfiguration: Configuration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalletApp()

        }
    }
}

@Composable
fun WalletApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val walletViewModel: WalletViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(navController, authViewModel)
        }
        composable("register") {
            RegistrationScreen(navController, authViewModel)
        }
        composable("home") {
            HomeScreen(navController, authViewModel, walletViewModel)
        }
        composable("add_money") {
            AddMoneyScreen(navController, authViewModel, walletViewModel)
        }
        composable("send_money") {
            SendMoneyScreen(navController, authViewModel, walletViewModel)
        }
        composable("receive_money") {
            ReceiveMoneyScreen(navController, authViewModel, walletViewModel)
        }
        composable("merchant_payment") {
            MerchantPaymentScreen(navController, authViewModel, walletViewModel)
        }
        composable("transactions") {
            TransactionScreen(navController, authViewModel, walletViewModel)
        }
    }
}