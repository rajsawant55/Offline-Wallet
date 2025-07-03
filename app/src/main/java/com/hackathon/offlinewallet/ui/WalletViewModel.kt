package com.hackathon.offlinewallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.offlinewallet.data.Wallet
import com.hackathon.offlinewallet.data.WalletRepository
import com.hackathon.offlinewallet.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val repository: WalletRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    fun getWallet(userEmail: String): StateFlow<Wallet?> {
        viewModelScope.launch {
            if (userEmail.isNotBlank()) {
                // Check if user exists in Room
                val userExists = authRepository.getUser(userEmail).firstOrNull() != null
                if (userExists) {
                    val wallet = repository.getWallet(userEmail).firstOrNull()
                    if (wallet == null) {
                        repository.insertWallet(Wallet(id = "wallet_$userEmail", balance = 0.0, userEmail = userEmail))
                    }
                }
            }
        }
        return repository.getWallet(userEmail).stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    val transactions: StateFlow<List<com.hackathon.offlinewallet.data.Transaction>> = repository.getTransactions().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val isOnline: StateFlow<Boolean> = flow { emit(repository.isOnline()) }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun addMoney(userEmail: String, amount: Double) {
        viewModelScope.launch { repository.addMoney(userEmail, amount) }
    }

    fun sendMoney(userEmail: String, amount: Double, recipient: String, isUpi: Boolean = false): Boolean {
        var success = false
        viewModelScope.launch {
            success = repository.sendMoney(userEmail, amount, recipient, "SEND", isUpi)
        }
        return success
    }
}