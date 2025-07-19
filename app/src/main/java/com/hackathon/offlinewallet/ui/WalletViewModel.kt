package com.hackathon.offlinewallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.offlinewallet.data.WalletTransactions
import com.hackathon.offlinewallet.data.Wallet
import com.hackathon.offlinewallet.data.WalletDao
import com.hackathon.offlinewallet.data.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val walletDao: WalletDao
) : ViewModel() {

    private val _isOnline = MutableStateFlow(walletRepository.isOnline())
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        viewModelScope.launch {
            while (true) {
                _isOnline.value = walletRepository.isOnline()
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun getWallet(email: String): StateFlow<Wallet?> {
        val walletFlow = MutableStateFlow<Wallet?>(null)
        viewModelScope.launch {
            walletRepository.getWallet(email).fold(
                onSuccess = { wallet ->
                    walletFlow.value = wallet
                },
                onFailure = { error ->
                    walletFlow.value = null
                    android.util.Log.e("WalletViewModel", "Error fetching wallet: ${error.message}", error)
                }
            )
        }
        return walletFlow
    }

    fun addMoney(email: String, amount: Double, onError: (String?) -> Unit) {
        viewModelScope.launch {
            walletRepository.addMoney(email, amount).fold(
                onSuccess = { onError(null) },
                onFailure = { error ->
                    android.util.Log.e("WalletViewModel", "Error adding money: ${error.message}", error)
                    onError(error.message)
                }
            )
        }
    }

    fun sendMoney(senderEmail: String, receiverEmail: String, amount: Double, onError: (String?) -> Unit) {
        viewModelScope.launch {
            walletRepository.sendMoney(senderEmail, receiverEmail, amount).fold(
                onSuccess = { onError(null) },
                onFailure = { error ->
                    android.util.Log.e("WalletViewModel", "Error sending money: ${error.message}", error)
                    onError(error.message)
                }
            )
        }
    }

    fun receiveMoney(receiverEmail: String, senderEmail: String, amount: Double, onError: (String?) -> Unit) {
        viewModelScope.launch {
            walletRepository.receiveMoney(receiverEmail, senderEmail, amount).fold(
                onSuccess = { onError(null) },
                onFailure = { error ->
                    android.util.Log.e("WalletViewModel", "Error receiving money: ${error.message}", error)
                    onError(error.message)
                }
            )
        }
    }

    fun getTransactions(userId: String): StateFlow<List<WalletTransactions>> {
        val walletTransactionsFlow = MutableStateFlow<List<WalletTransactions>>(emptyList())
        viewModelScope.launch {
            walletRepository.getTransactions(userId).fold(
                onSuccess = { transactions ->
                    walletTransactionsFlow.value = transactions
                },
                onFailure = { error ->
                    walletTransactionsFlow.value = emptyList()
                    android.util.Log.e("WalletViewModel", "Error fetching transactions: ${error.message}", error)
                }
            )
        }
        return walletTransactionsFlow
    }

    fun storeOfflineTransaction(transaction: WalletTransactions) {
        viewModelScope.launch {
            walletRepository.storeOfflineTransaction(transaction)
        }
    }

}

@Serializable
data class CreateOrderResponse(
    val payment_session_id: String,
    val order_id: String
)