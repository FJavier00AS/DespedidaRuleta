package com.example.despedidaruleta.domain.repository

import android.net.Uri
import com.example.despedidaruleta.domain.model.ImportPreview
import com.example.despedidaruleta.domain.model.RouletteCategory

interface ContentImportParser {
    suspend fun parse(uri: Uri, fallbackCategory: RouletteCategory?): ImportPreview
}
