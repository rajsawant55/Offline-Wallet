package com.hackathon.offlinewallet // Or your app's package name

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OfflineWalletApp : Application() {
    // You can leave this class empty if you don't have other
    // application-level setup to do here.
    // Hilt will generate the necessary code based on the annotation.
}