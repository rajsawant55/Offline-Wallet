package com.hackathon.offlinewallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.offlinewallet.data.Wallet
import com.hackathon.offlinewallet.data.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val repository: WalletRepository
) : ViewModel() {
    val wallet: StateFlow<Wallet?> = repository.getWallet().stateIn(viewModelScope, SharingStarted.Lazily, null)
    val transactions: StateFlow<List<com.hackathon.offlinewallet.data.Transaction>> = repository.getTransactions().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val isOnline: StateFlow<Boolean> = flow { emit(repository.isOnline()) }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun addMoney(amount: Double) {
        viewModelScope.launch { repository.addMoney(amount) }
    }

    fun sendMoney(amount: Double, recipient: String, type:String,isUpi: Boolean = false): Boolean {
        var success = false
        viewModelScope.launch {
            success = repository.sendMoney(amount, recipient, type, isUpi)
        }
        return success
    }
}