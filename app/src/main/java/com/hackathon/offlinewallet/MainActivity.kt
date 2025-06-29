package com.hackathon.offlinewallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hackathon.offlinewallet.ui.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") { HomeScreen(navController, hiltViewModel()) }
                composable("add_money") { AddMoneyScreen(navController, hiltViewModel()) }
                composable("send_money") { SendMoneyScreen(navController, hiltViewModel()) }
                composable("merchant_payment") { MerchantPaymentScreen(navController, hiltViewModel()) }
                composable("transactions") { TransactionScreen(navController, hiltViewModel()) }
                composable("receive_money") { ReceiveMoneyScreen(navController, hiltViewModel()) }
            }
        }
    }
}