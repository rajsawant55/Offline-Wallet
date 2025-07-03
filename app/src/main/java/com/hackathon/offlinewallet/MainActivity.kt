package com.hackathon.offlinewallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.hackathon.offlinewallet.ui.*
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.fragment.app.FragmentActivity

data class NavItem(val route: String, val icon: ImageVector, val label: String)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WalletTheme {
                val navController = rememberNavController()
                val authViewModel: AuthViewModel = hiltViewModel()
                val isLoggedIn by remember { derivedStateOf { authViewModel.isLoggedIn() } }
                val googlePlayServicesAvailable by remember {
                    mutableStateOf(
                        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
                    )
                }

                if (!googlePlayServicesAvailable) {
                    AlertDialog(
                        onDismissRequest = { /* Prevent dismissal */ },
                        title = { Text("Google Play Services Required") },
                        text = { Text("This app requires Google Play Services for authentication. Please install or update Google Play Services.") },
                        confirmButton = {
                            Button(onClick = { finish() }) {
                                Text("Exit")
                            }
                        }
                    )
                } else {
                    val navItems = listOf(
                        NavItem("home", Icons.Default.Home, "Home"),
                        NavItem("send_money", Icons.Default.Send, "Send"),
                        NavItem("transactions", Icons.Default.History, "History"),
                        NavItem("receive_money", Icons.Default.MonetizationOn, "Receive")
                    )
                    Scaffold(
                        bottomBar = {
                            if (isLoggedIn) {
                                NavigationBar {
                                    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                                    navItems.forEach { item ->
                                        NavigationBarItem(
                                            icon = { Icon(item.icon, contentDescription = item.label) },
                                            label = { Text(item.label) },
                                            selected = currentRoute == item.route,
                                            onClick = {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = if (isLoggedIn) "home" else "login",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("login") { LoginScreen(navController, authViewModel) }
                            composable("register") { RegistrationScreen(navController, authViewModel) }
                            composable("home") {
                                HomeScreen(navController, authViewModel, hiltViewModel())
                            }
                            composable("add_money") {
                                AddMoneyScreen(navController, authViewModel, hiltViewModel())
                            }
                            composable("send_money") {
                                SendMoneyScreen(navController, authViewModel, hiltViewModel())
                            }
                            composable("merchant_payment") {
                                MerchantPaymentScreen(navController, authViewModel, hiltViewModel())
                            }
                            composable("transactions") {
                                TransactionScreen(navController, authViewModel, hiltViewModel())
                            }
                            composable("receive_money") {
                                ReceiveMoneyScreen(navController, authViewModel, hiltViewModel())
                            }
                        }
                    }
                }
            }
        }
    }
}