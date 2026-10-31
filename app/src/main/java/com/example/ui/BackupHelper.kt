package com.example.ui

import android.content.Context
import android.net.Uri
import com.example.data.BackupData
import com.example.viewmodel.FinanceViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object BackupHelper {
    suspend fun exportData(context: Context, uri: Uri, data: BackupData) {
        withContext(Dispatchers.IO) {
            try {
                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(BackupData::class.java)
                val json = jsonAdapter.toJson(data)
                
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output).use { writer ->
                        writer.write(json)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun importData(context: Context, uri: Uri, viewModel: FinanceViewModel) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    InputStreamReader(input).use { reader ->
                        val json = reader.readText()
                        val moshi = Moshi.Builder().build()
                        val jsonAdapter = moshi.adapter(BackupData::class.java)
                        val data = jsonAdapter.fromJson(json)
                        
                        data?.let {
                            viewModel.restoreBackup(it)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
