package com.hackathon.offlinewallet.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor() {
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = "https://pgqlszpghkjksrmvxhqk.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBncWxzenBnaGtqa3NybXZ4aHFrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTE1NTA2MDgsImV4cCI6MjA2NzEyNjYwOH0.TEgkqhkrlJkD5E0EE16Z281xDUSVTKNuZGqnJU19lmA"
    ) {
        install(Auth)
        install(Postgrest)
    }

}